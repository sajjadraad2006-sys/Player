package dev.auralis.studio.lyrics

import android.util.Log
import dev.auralis.studio.core.model.LyricsLine
import dev.auralis.studio.core.model.LyricsSource
import dev.auralis.studio.core.model.SyncedLyrics
import dev.auralis.studio.core.model.Track
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.util.Locale
import kotlin.math.abs

private const val TAG = "YouTubeCaptions"

/**
 * Robust captions and timed text resolver from YouTube Innertube endpoints.
 * Serves as a high-precision, synced fallback for Arabic, regional, and non-English
 * songs when open databases like LRCLIB have no entry.
 */
class YouTubeCaptionsClient {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val client = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(json)
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 6_000L
            connectTimeoutMillis = 4_000L
            socketTimeoutMillis = 6_000L
        }
    }

    data class CaptionsResult(
        val lyrics: SyncedLyrics,
        val lrcText: String,
        val plainText: String,
        val videoId: String,
    )

    /**
     * Resolves synced lyrics from YouTube captions for the given track.
     */
    suspend fun resolve(track: Track): CaptionsResult? = withContext(Dispatchers.IO) {
        try {
            val videoId = extractOrSearchVideoId(track) ?: return@withContext null
            val captionTrackUrl = fetchCaptionTrackUrl(videoId) ?: return@withContext null
            val lines = fetchAndParseCaptions(captionTrackUrl)
            if (lines.isEmpty()) return@withContext null

            val lrc = toLrc(lines)
            val plain = lines.joinToString("\n") { it.text }
            val syncedLyrics = SyncedLyrics(
                lines = lines,
                synced = true,
                source = LyricsSource.YOUTUBE_CAPTIONS,
            )
            CaptionsResult(syncedLyrics, lrc, plain, videoId)
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            Log.w(TAG, "YouTube captions resolution failed for ${track.title}: ${e.message}")
            null
        }
    }

    private suspend fun extractOrSearchVideoId(track: Track): String? {
        val uri = track.uri.trim()
        val videoIdRegex = Regex("""^[a-zA-Z0-9_-]{11}$""")
        if (videoIdRegex.matches(uri)) return uri

        val ytWatchRegex = Regex("""(?:youtube\.com/watch\?v=|youtu\.be/|youtube\.com/embed/)([a-zA-Z0-9_-]{11})""")
        val match = ytWatchRegex.find(uri)
        if (match != null) return match.groupValues[1]

        // Search YouTube for video ID
        val norm = LyricsMetadataNormalizer.normalize(track.title, track.artist)
        val searchTerms = listOfNotNull(
            if (norm.artist.isNotBlank()) "${norm.artist} ${norm.title}" else norm.title,
            if (LyricsMetadataNormalizer.isArabic(norm.title)) "${norm.title} كلمات" else null,
        ).distinct()

        for (q in searchTerms) {
            val vid = searchInnertubeForVideoId(q, track.durationMs)
            if (vid != null) return vid
        }
        return null
    }

    private suspend fun searchInnertubeForVideoId(query: String, durationMs: Long): String? {
        try {
            val payload = buildJsonObject {
                putJsonObject("context") {
                    putJsonObject("client") {
                        put("clientName", "WEB")
                        put("clientVersion", "2.20240901.00.00")
                        put("hl", "ar")
                        put("gl", "IQ")
                    }
                }
                put("query", query)
            }

            val resp = client.post("https://www.youtube.com/youtubei/v1/search") {
                contentType(ContentType.Application.Json)
                setBody(payload.toString())
                header("X-YouTube-Client-Name", "1")
                header("X-YouTube-Client-Version", "2.20240901.00.00")
            }

            if (!resp.status.isSuccess()) return null
            val body = resp.bodyAsText()
            val root = json.parseToJsonElement(body).jsonObject

            val contents = root["contents"]?.jsonObject
                ?.get("twoColumnSearchResultsRenderer")?.jsonObject
                ?.get("primaryContents")?.jsonObject
                ?.get("sectionListRenderer")?.jsonObject
                ?.get("contents")?.jsonArray ?: return null

            for (section in contents) {
                val itemSection = section.jsonObject["itemSectionRenderer"]?.jsonObject
                val items = itemSection?.get("contents")?.jsonArray ?: continue

                for (item in items) {
                    val videoRenderer = item.jsonObject["videoRenderer"]?.jsonObject ?: continue
                    val videoId = videoRenderer["videoId"]?.jsonPrimitive?.content ?: continue
                    val lengthText = videoRenderer["lengthText"]?.jsonObject
                        ?.get("simpleText")?.jsonPrimitive?.content.orEmpty()
                    val videoDurationMs = parseClock(lengthText)

                    if (durationMs <= 0 || videoDurationMs <= 0 || abs(durationMs - videoDurationMs) < 12_000L) {
                        return videoId
                    }
                }
            }
        } catch (_: Exception) {}
        return null
    }

    private suspend fun fetchCaptionTrackUrl(videoId: String): String? {
        val payload = buildJsonObject {
            put("videoId", videoId)
            putJsonObject("context") {
                putJsonObject("client") {
                    put("clientName", "ANDROID")
                    put("clientVersion", "19.29.35")
                    put("hl", "ar")
                    put("gl", "IQ")
                }
            }
        }

        val resp = client.post("https://www.youtube.com/youtubei/v1/player") {
            contentType(ContentType.Application.Json)
            setBody(payload.toString())
            header("X-YouTube-Client-Name", "3")
            header("X-YouTube-Client-Version", "19.29.35")
        }

        if (!resp.status.isSuccess()) return null
        val root = json.parseToJsonElement(resp.bodyAsText()).jsonObject
        val captions = root["captions"]?.jsonObject
            ?.get("playerCaptionsTracklistRenderer")?.jsonObject
            ?.get("captionTracks")?.jsonArray ?: return null

        if (captions.isEmpty()) return null

        var bestUrl: String? = null
        var foundArabic = false

        for (item in captions) {
            val obj = item.jsonObject
            val baseUrl = obj["baseUrl"]?.jsonPrimitive?.content ?: continue
            val lang = obj["languageCode"]?.jsonPrimitive?.content.orEmpty()

            if (lang.startsWith("ar", ignoreCase = true)) {
                bestUrl = baseUrl
                foundArabic = true
                break
            } else if (!foundArabic && bestUrl == null) {
                bestUrl = baseUrl
            }
        }

        return bestUrl
    }

    private suspend fun fetchAndParseCaptions(captionUrl: String): List<LyricsLine> {
        val url = when {
            captionUrl.contains("fmt=") -> captionUrl
            captionUrl.contains("?") -> "$captionUrl&fmt=json3"
            else -> "$captionUrl?fmt=json3"
        }
        val resp = client.get(url)
        if (!resp.status.isSuccess()) return emptyList()

        val text = resp.bodyAsText()
        if (text.isBlank()) return emptyList()

        // 1. Try parsing JSON3 format
        val jsonLines = parseJson3Captions(text)
        if (jsonLines.isNotEmpty()) return jsonLines

        // 2. Fall back to XML parsing
        return parseXmlCaptions(text)
    }

    private fun parseJson3Captions(jsonText: String): List<LyricsLine> {
        return try {
            val root = json.parseToJsonElement(jsonText).jsonObject
            val events = root["events"]?.jsonArray ?: return emptyList()
            val lines = mutableListOf<LyricsLine>()

            for (event in events) {
                val obj = event.jsonObject
                val timeMs = obj["tStartMs"]?.jsonPrimitive?.longOrNull ?: continue
                val segs = obj["segs"]?.jsonArray ?: continue

                val sb = StringBuilder()
                for (seg in segs) {
                    val s = seg.jsonObject["utf8"]?.jsonPrimitive?.content.orEmpty()
                    sb.append(s)
                }

                val lineText = cleanCaptionLine(sb.toString())
                if (lineText.isNotBlank()) {
                    lines.add(LyricsLine(timeMs = timeMs, text = lineText))
                }
            }
            lines
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun parseXmlCaptions(xmlText: String): List<LyricsLine> {
        val regex = Regex("""<text start="([\d.]+)"(?: dur="([\d.]+)")?[^>]*>([^<]+)</text>""")
        val matches = regex.findAll(xmlText)
        val lines = mutableListOf<LyricsLine>()

        for (m in matches) {
            val startSec = m.groupValues[1].toDoubleOrNull() ?: continue
            val rawContent = m.groupValues[3]
            val decoded = decodeXmlEntities(rawContent)
            val lineText = cleanCaptionLine(decoded)
            if (lineText.isNotBlank()) {
                lines.add(LyricsLine(timeMs = (startSec * 1000).toLong(), text = lineText))
            }
        }
        return lines
    }

    private fun cleanCaptionLine(raw: String): String {
        var s = raw.replace("\n", " ").trim()
        val noiseTags = listOf("[موسيقى]", "[Music]", "[موسيقي]", "♪", "♫", "[Applause]", "[تصفيق]")
        for (noise in noiseTags) {
            s = s.replace(noise, "")
        }
        return s.replace(Regex("\\s{2,}"), " ").trim()
    }

    private fun decodeXmlEntities(input: String): String {
        return input.replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
    }

    private fun parseClock(clock: String): Long {
        if (clock.isBlank()) return 0L
        val parts = clock.trim().split(':').mapNotNull { it.toLongOrNull() }
        return when (parts.size) {
            1 -> parts[0] * 1_000L
            2 -> (parts[0] * 60L + parts[1]) * 1_000L
            3 -> (parts[0] * 3600L + parts[1] * 60L + parts[2]) * 1_000L
            else -> 0L
        }
    }

    private fun toLrc(lines: List<LyricsLine>): String {
        return lines.joinToString("\n") { line ->
            val totalSec = line.timeMs / 1000
            val min = totalSec / 60
            val sec = totalSec % 60
            val hundredths = (line.timeMs % 1000) / 10
            val timeTag = String.format(Locale.US, "[%02d:%02d.%02d]", min, sec, hundredths)
            "$timeTag ${line.text}"
        }
    }

    fun close() {
        client.close()
    }
}
