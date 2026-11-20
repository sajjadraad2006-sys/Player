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
import kotlinx.serialization.json.contentOrNull
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
 *
 * Strategies:
 * 1. Multi-candidate search targeting official audio, lyrics, and regional queries.
 * 2. VISIONOS Innertube client for official & auto-generated timed captions (ASR).
 * 3. Video description lyrics extraction for songs without closed captions.
 * 4. Automatic Arabic translation via timedtext tlang=ar and Google Translator fallback.
 */
class YouTubeCaptionsClient {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val client = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(json)
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 15_000L
            connectTimeoutMillis = 8_000L
            socketTimeoutMillis = 15_000L
        }
    }

    data class CaptionsResult(
        val lyrics: SyncedLyrics,
        val lrcText: String,
        val plainText: String,
        val videoId: String,
    )

    data class CaptionTrackInfo(
        val baseUrl: String,
        val lang: String,
        val isArabic: Boolean,
    )

    /**
     * Resolves synced lyrics from YouTube captions or video description for the given track.
     */
    suspend fun resolve(track: Track, translateToArabic: Boolean = true): CaptionsResult? = withContext(Dispatchers.IO) {
        try {
            val candidateVideoIds = collectCandidateVideoIds(track)
            if (candidateVideoIds.isEmpty()) return@withContext null

            for (videoId in candidateVideoIds) {
                // 1. Try timed caption tracks (VISIONOS, ANDROID_VR, WEB)
                val trackInfo = fetchCaptionTrackInfo(videoId)
                if (trackInfo != null && trackInfo.baseUrl.isNotBlank()) {
                    val originalLines = fetchAndParseCaptions(trackInfo.baseUrl)
                    if (originalLines.isNotEmpty()) {
                        val finalLines = if (translateToArabic && !trackInfo.isArabic) {
                            try {
                                val translatedUrl = if (trackInfo.baseUrl.contains("tlang=")) {
                                    trackInfo.baseUrl
                                } else {
                                    val sep = if (trackInfo.baseUrl.contains("?")) "&" else "?"
                                    "${trackInfo.baseUrl}${sep}tlang=ar"
                                }
                                val transLines = fetchAndParseCaptions(translatedUrl)
                                if (transLines.isNotEmpty()) {
                                    val matched = originalLines.mapIndexed { idx, orig ->
                                        val match = transLines.firstOrNull { abs(it.timeMs - orig.timeMs) < 1200L }
                                            ?: transLines.getOrNull(idx)
                                        if (match != null && match.text.isNotBlank() && match.text != orig.text) {
                                            orig.copy(translation = match.text)
                                        } else {
                                            orig
                                        }
                                    }
                                    if (matched.any { it.translation.isNullOrBlank() }) {
                                        LyricsTranslator.translateLines(matched, "ar")
                                    } else {
                                        matched
                                    }
                                } else {
                                    LyricsTranslator.translateLines(originalLines, "ar")
                                }
                            } catch (e: Exception) {
                                Log.w(TAG, "Failed to fetch translated captions: ${e.message}")
                                LyricsTranslator.translateLines(originalLines, "ar")
                            }
                        } else {
                            originalLines
                        }

                        val lrc = toLrc(finalLines)
                        val plain = finalLines.joinToString("\n") { it.text }
                        val syncedLyrics = SyncedLyrics(
                            lines = finalLines,
                            synced = true,
                            source = LyricsSource.YOUTUBE_CAPTIONS,
                        )
                        return@withContext CaptionsResult(syncedLyrics, lrc, plain, videoId)
                    }
                }

                // 2. Fallback: Extract full lyrics from video description (Official Arabic Music Videos)
                val descLyrics = fetchLyricsFromDescription(videoId, track.durationMs)
                if (descLyrics != null && descLyrics.isNotEmpty()) {
                    val isAr = LyricsMetadataNormalizer.isArabic(descLyrics.first().text)
                    val finalLines = if (translateToArabic && !isAr) {
                        LyricsTranslator.translateLines(descLyrics, "ar")
                    } else {
                        descLyrics
                    }
                    val lrc = toLrc(finalLines)
                    val plain = finalLines.joinToString("\n") { it.text }
                    val syncedLyrics = SyncedLyrics(
                        lines = finalLines,
                        synced = false,
                        source = LyricsSource.YOUTUBE_CAPTIONS,
                    )
                    return@withContext CaptionsResult(syncedLyrics, lrc, plain, videoId)
                }
            }
            null
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            Log.w(TAG, "YouTube captions resolution failed for ${track.title}: ${e.message}")
            null
        }
    }

    private suspend fun collectCandidateVideoIds(track: Track): List<String> {
        val candidates = mutableListOf<String>()

        // 1. Exact Video ID from Track URI or Artwork URL
        val uri = track.uri.trim()
        val videoIdRegex = Regex("""^[a-zA-Z0-9_-]{11}$""")
        if (videoIdRegex.matches(uri)) candidates.add(uri)

        val ytWatchRegex = Regex("""(?:youtube\.com/watch\?v=|youtu\.be/|youtube\.com/embed/)([a-zA-Z0-9_-]{11})""")
        ytWatchRegex.find(uri)?.let { candidates.add(it.groupValues[1]) }

        val art = (track.customArtworkUri ?: track.artworkUri).orEmpty()
        val artRegex = Regex("""(?:ytimg\.com/vi/|youtube\.com/vi/)([a-zA-Z0-9_-]{11})""")
        artRegex.find(art)?.let { candidates.add(it.groupValues[1]) }

        // 2. Innertube search queries targeting official video, audio, and lyrics
        val norm = LyricsMetadataNormalizer.normalize(track.title, track.artist)
        val queries = buildList {
            if (norm.artist.isNotBlank()) add("${norm.artist} ${norm.title} official audio")
            if (norm.artist.isNotBlank()) add("${norm.artist} ${norm.title}")
            if (LyricsMetadataNormalizer.isArabic(norm.title)) {
                add("${norm.title} ${norm.artist} كلمات")
                add("${norm.title} كلمات")
            } else {
                add("${norm.artist} ${norm.title} lyrics")
            }
            add(norm.title)
        }.distinct()

        for (q in queries) {
            val vids = searchInnertubeForVideoCandidates(q, track.durationMs)
            for (v in vids) {
                if (!candidates.contains(v)) candidates.add(v)
            }
            if (candidates.size >= 4) break
        }

        return candidates.distinct()
    }

    private suspend fun searchInnertubeForVideoCandidates(query: String, durationMs: Long): List<String> {
        val results = mutableListOf<String>()
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
                // Filter strictly to videos to eliminate channel cards
                put("params", "EgIQAQ%3D%3D")
            }

            val resp = client.post("https://www.youtube.com/youtubei/v1/search") {
                contentType(ContentType.Application.Json)
                setBody(payload.toString())
                header("X-YouTube-Client-Name", "1")
                header("X-YouTube-Client-Version", "2.20240901.00.00")
                header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            }

            if (!resp.status.isSuccess()) return emptyList()
            val root = json.parseToJsonElement(resp.bodyAsText()).jsonObject

            val contents = root["contents"]?.jsonObject
                ?.get("twoColumnSearchResultsRenderer")?.jsonObject
                ?.get("primaryContents")?.jsonObject
                ?.get("sectionListRenderer")?.jsonObject
                ?.get("contents")?.jsonArray ?: return emptyList()

            for (section in contents) {
                val itemSection = section.jsonObject["itemSectionRenderer"]?.jsonObject
                val items = itemSection?.get("contents")?.jsonArray ?: continue

                for (item in items) {
                    val videoRenderer = item.jsonObject["videoRenderer"]?.jsonObject ?: continue
                    val videoId = videoRenderer["videoId"]?.jsonPrimitive?.contentOrNull ?: continue
                    val lengthText = videoRenderer["lengthText"]?.jsonObject
                        ?.get("simpleText")?.jsonPrimitive?.contentOrNull.orEmpty()
                    val videoDurationMs = parseClock(lengthText)

                    // Generous duration match (up to 50 seconds variance or accept top result)
                    if (durationMs <= 0 || videoDurationMs <= 0 || abs(durationMs - videoDurationMs) < 50_000L || results.isEmpty()) {
                        results.add(videoId)
                        if (results.size >= 3) return results
                    }
                }
            }
        } catch (_: Exception) {}
        return results
    }

    private suspend fun fetchCaptionTrackInfo(videoId: String): CaptionTrackInfo? {
        // Priority 1: VISIONOS client (bypasses bot checks & exposes complete caption tracks including ASR)
        // Priority 2: ANDROID_VR
        // Priority 3: WEB
        val clientConfigs = listOf(
            ClientConfig(
                name = "VISIONOS",
                version = "1.02",
                clientId = "101",
                ua = "Mozilla/5.0 (Macintosh; Intel Mac OS X 15_7_3) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.0 Safari/605.1.15",
                deviceMake = "Apple",
                deviceModel = "RealityDevice17,1",
                osName = "visionOS",
                osVersion = "26.5.23O471",
            ),
            ClientConfig(
                name = "ANDROID_VR",
                version = "1.65.10",
                clientId = "28",
                ua = "com.google.android.apps.youtube.vr.oculus/1.65.10 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip",
                deviceMake = "Oculus",
                deviceModel = "Quest 3",
                osName = "Android",
                osVersion = "12L",
            ),
            ClientConfig(
                name = "WEB",
                version = "2.20240901.00.00",
                clientId = "1",
                ua = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            ),
        )

        for (cfg in clientConfigs) {
            try {
                val payload = buildJsonObject {
                    put("videoId", videoId)
                    putJsonObject("context") {
                        putJsonObject("client") {
                            put("clientName", cfg.name)
                            put("clientVersion", cfg.version)
                            put("hl", "ar")
                            put("gl", "IQ")
                            cfg.deviceMake?.let { put("deviceMake", it) }
                            cfg.deviceModel?.let { put("deviceModel", it) }
                            cfg.osName?.let { put("osName", it) }
                            cfg.osVersion?.let { put("osVersion", it) }
                        }
                    }
                }

                val resp = client.post("https://www.youtube.com/youtubei/v1/player") {
                    contentType(ContentType.Application.Json)
                    setBody(payload.toString())
                    header("X-YouTube-Client-Name", cfg.clientId)
                    header("X-YouTube-Client-Version", cfg.version)
                    header("User-Agent", cfg.ua)
                }

                if (!resp.status.isSuccess()) continue
                val root = json.parseToJsonElement(resp.bodyAsText()).jsonObject
                val captions = root["captions"]?.jsonObject
                    ?.get("playerCaptionsTracklistRenderer")?.jsonObject
                    ?.get("captionTracks")?.jsonArray ?: continue

                if (captions.isEmpty()) continue

                var bestTrack: CaptionTrackInfo? = null
                var foundArabic = false

                for (item in captions) {
                    val obj = item.jsonObject
                    val baseUrl = obj["baseUrl"]?.jsonPrimitive?.contentOrNull ?: continue
                    val lang = obj["languageCode"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    val isAr = lang.startsWith("ar", ignoreCase = true)

                    if (isAr) {
                        bestTrack = CaptionTrackInfo(baseUrl, lang, isArabic = true)
                        foundArabic = true
                        break
                    } else if (!foundArabic && bestTrack == null) {
                        bestTrack = CaptionTrackInfo(baseUrl, lang, isArabic = false)
                    }
                }

                if (bestTrack != null) return bestTrack
            } catch (_: Exception) {}
        }

        return null
    }

    private data class ClientConfig(
        val name: String,
        val version: String,
        val clientId: String,
        val ua: String,
        val deviceMake: String? = null,
        val deviceModel: String? = null,
        val osName: String? = null,
        val osVersion: String? = null,
    )

    private suspend fun fetchLyricsFromDescription(videoId: String, durationMs: Long): List<LyricsLine>? {
        return try {
            val payload = buildJsonObject {
                put("videoId", videoId)
                putJsonObject("context") {
                    putJsonObject("client") {
                        put("clientName", "WEB")
                        put("clientVersion", "2.20240901.00.00")
                        put("hl", "ar")
                        put("gl", "IQ")
                    }
                }
            }

            val resp = client.post("https://www.youtube.com/youtubei/v1/player") {
                contentType(ContentType.Application.Json)
                setBody(payload.toString())
                header("X-YouTube-Client-Name", "1")
                header("X-YouTube-Client-Version", "2.20240901.00.00")
                header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            }

            if (!resp.status.isSuccess()) return null
            val root = json.parseToJsonElement(resp.bodyAsText()).jsonObject
            val desc = root["videoDetails"]?.jsonObject?.get("shortDescription")?.jsonPrimitive?.contentOrNull.orEmpty()
            if (desc.isBlank()) return null

            val rawLines = extractLyricsFromDescription(desc)
            if (rawLines.size < 4) return null

            // Distribute lines across duration
            val totalDuration = if (durationMs > 20_000L) durationMs else (rawLines.size * 6_000L)
            val startTimeMs = 6_000L
            val availableMs = (totalDuration - startTimeMs - 4_000L).coerceAtLeast(rawLines.size * 2_000L)
            val stepMs = (availableMs / rawLines.size.coerceAtLeast(1)).coerceIn(2_000L, 8_000L)

            rawLines.mapIndexed { idx, lineText ->
                LyricsLine(
                    timeMs = startTimeMs + (idx * stepMs),
                    text = lineText,
                )
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun extractLyricsFromDescription(description: String): List<String> {
        val patterns = listOf(
            Regex("""(?:Lyrics|كلمات|الكلمات|كلمات الأغنية|كلمات اغنية|Song Lyrics)\s*[:|：|\-|–]?\s*\n+(.*?)(?=\n+\s*(?:Digital Stores|Digital Platforms|Listen & Download|Follow|للاستماع|منصة|لتحميل|اشترك|Subscribe|Connect|Produced|Directed|Social Media|Copyright|\(C\)|\xA9|http|\Z))""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)),
            Regex("""(?:Lyrics|كلمات|الكلمات|كلمات الأغنية)\s*[:|：|\-|–]?\s*(.*?)(?=\n+\s*(?:Follow|اشترك|Subscribe|Copyright|\(C\)|\xA9|http|\Z))""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)),
        )
        for (pat in patterns) {
            val m = pat.find(description)
            if (m != null) {
                val raw = m.groupValues[1].trim()
                val lines = raw.lines().map { it.trim() }.filter { it.isNotBlank() }
                val valid = lines.filter { line ->
                    !line.startsWith("http", ignoreCase = true) &&
                    !line.startsWith("•") &&
                    !line.startsWith("#") &&
                    line.length in 2..150
                }
                if (valid.size >= 4) return valid
            }
        }
        return emptyList()
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

        val jsonLines = parseJson3Captions(text)
        if (jsonLines.isNotEmpty()) return jsonLines

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
                    val s = seg.jsonObject["utf8"]?.jsonPrimitive?.contentOrNull.orEmpty()
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
            if (!line.translation.isNullOrBlank()) {
                "$timeTag ${line.text} || ${line.translation}"
            } else {
                "$timeTag ${line.text}"
            }
        }
    }

    fun close() {
        client.close()
    }
}
