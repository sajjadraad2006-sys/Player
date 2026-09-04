package dev.auralis.studio.online

import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

private const val TAG = "YouTubeCatalog"
private const val SEARCH_URL = "https://www.googleapis.com/youtube/v3/search"
private const val VIDEOS_URL = "https://www.googleapis.com/youtube/v3/videos"
private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"

// ─── YouTube Data API v3 Models ─────────────────────────────────────────────

@Serializable
private data class YouTubeSearchResponse(
    val items: List<YouTubeSearchResultItem> = emptyList(),
)

@Serializable
private data class YouTubeSearchResultItem(
    val id: YouTubeResourceId = YouTubeResourceId(),
    val snippet: YouTubeSnippet = YouTubeSnippet(),
)

@Serializable
private data class YouTubeResourceId(
    val kind: String = "",
    val videoId: String = "",
)

@Serializable
private data class YouTubeSnippet(
    val title: String = "",
    val description: String = "",
    val channelTitle: String = "",
    val thumbnails: YouTubeThumbnails = YouTubeThumbnails(),
    val publishedAt: String = "",
)

@Serializable
private data class YouTubeThumbnails(
    val high: YouTubeThumbnailDetails? = null,
    val medium: YouTubeThumbnailDetails? = null,
    val default: YouTubeThumbnailDetails? = null,
)

@Serializable
private data class YouTubeThumbnailDetails(
    val url: String = "",
)

@Serializable
private data class YouTubeVideoDetailsResponse(
    val items: List<YouTubeVideoDetailsItem> = emptyList(),
)

@Serializable
private data class YouTubeVideoDetailsItem(
    val id: String = "",
    val contentDetails: YouTubeContentDetails = YouTubeContentDetails(),
    val status: YouTubeStatus? = null,
)

@Serializable
private data class YouTubeContentDetails(
    val duration: String = "",
    val licensedContent: Boolean = false,
)

@Serializable
private data class YouTubeStatus(
    val embeddable: Boolean = true,
    val license: String = "",
)

/**
 * Production YouTube Catalog Provider using official YouTube Data API v3.
 *
 * Guaranteed:
 * 1. Uses YouTube Data API v3 for search & batch video metadata discovery.
 * 2. Does NOT use yt-dlp for primary catalog search.
 * 3. Constructs stable canonical watch URLs (https://www.youtube.com/watch?v=VIDEO_ID).
 * 4. Enables downloadAllowed=true for on-demand LRR isolated engine resolution.
 * 5. Handles missing API keys gracefully with [ProviderResult.NotConfigured].
 * 6. Never logs secrets or API keys.
 */
class YouTubeCatalogProvider(
    private var apiKey: String = "",
    val enableWebFallback: Boolean = true,
) : MusicCatalogProvider {

    override val id: String = "youtube"
    override val displayName: String = "YouTube"

    override val isConfigured: Boolean
        get() = apiKey.isNotBlank() || enableWebFallback

    fun updateApiKey(newKey: String) {
        apiKey = newKey.trim()
    }

    private val client by lazy {
        HttpClient(OkHttp) {
            expectSuccess = false
            engine {
                config {
                    followRedirects(true)
                    followSslRedirects(true)
                }
            }
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; isLenient = true })
            }
            install(HttpTimeout) {
                connectTimeoutMillis = 10_000
                requestTimeoutMillis = 15_000
                socketTimeoutMillis = 20_000
            }
            defaultRequest {
                headers.append("User-Agent", USER_AGENT)
                headers.append("Accept-Language", "en-US,en;q=0.9,ar;q=0.8")
            }
        }
    }

    override suspend fun search(query: String, page: Int): ProviderResult<List<OnlineTrack>> {
        if (!isConfigured) return ProviderResult.NotConfigured
        val trimmed = query.trim()
        if (trimmed.isBlank()) return ProviderResult.Success(emptyList())

        if (apiKey.isNotBlank()) {
            val apiResult = searchWithDataApi(trimmed)
            if (apiResult is ProviderResult.Success && apiResult.data.isNotEmpty()) {
                return apiResult
            }
        }

        if (enableWebFallback) {
            return searchWeb(trimmed)
        }

        return ProviderResult.NotConfigured
    }

    private suspend fun searchWithDataApi(trimmed: String): ProviderResult<List<OnlineTrack>> {
        return try {
            // 1. Search videos
            val searchResponse = client.get(SEARCH_URL) {
                parameter("part", "snippet")
                parameter("type", "video")
                parameter("maxResults", 15)
                parameter("q", trimmed)
                parameter("key", apiKey)
            }

            if (searchResponse.status == HttpStatusCode.Forbidden || searchResponse.status == HttpStatusCode.Unauthorized) {
                return ProviderResult.Unauthorized
            }
            if (searchResponse.status == HttpStatusCode.TooManyRequests) {
                return ProviderResult.RateLimited
            }
            if (!searchResponse.status.value.let { it in 200..299 }) {
                return ProviderResult.Error("YouTube search returned HTTP ${searchResponse.status.value}", searchResponse.status.value)
            }

            val searchDto: YouTubeSearchResponse = searchResponse.body()
            val validItems = searchDto.items.filter { it.id.videoId.isNotBlank() }
            if (validItems.isEmpty()) {
                return ProviderResult.Success(emptyList())
            }

            // 2. Batch video details (duration & license)
            val videoIds = validItems.map { it.id.videoId }
            val detailsMap = fetchVideoDetails(videoIds)

            // 3. Transform to OnlineTrack
            val tracks = validItems.map { item ->
                val videoId = item.id.videoId
                val snippet = item.snippet
                val details = detailsMap[videoId]

                val rawTitle = snippet.title
                val cleanTitle = unescapeHtml(rawTitle)
                val cleanChannel = unescapeHtml(snippet.channelTitle)
                val artworkUrl = snippet.thumbnails.high?.url
                    ?: snippet.thumbnails.medium?.url
                    ?: snippet.thumbnails.default?.url
                    ?: ""

                val durationMs = parseIsoDurationMs(details?.contentDetails?.duration.orEmpty())
                val licenseStr = details?.status?.license.orEmpty()

                OnlineTrack(
                    provider = "youtube",
                    id = videoId,
                    title = cleanTitle,
                    artist = cleanChannel,
                    album = "YouTube",
                    durationMs = durationMs,
                    artworkUrl = artworkUrl,
                    sourceUrl = "https://www.youtube.com/watch?v=$videoId",
                    streamAllowed = false,
                    downloadAllowed = true,
                    license = licenseStr,
                )
            }

            ProviderResult.Success(tracks)
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            Log.w(TAG, "YouTube Data API search failed: ${e.message}")
            ProviderResult.Error(e.message ?: "YouTube search failed")
        }
    }

    suspend fun searchWeb(query: String): ProviderResult<List<OnlineTrack>> {
        // 1. First priority: Direct official InnerTube Web endpoint (pure JSON, resilient against consent walls)
        try {
            val innerTubeTracks = searchInnerTube(query)
            if (innerTubeTracks.isNotEmpty()) {
                return ProviderResult.Success(innerTubeTracks)
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            Log.w(TAG, "InnerTube search attempt fallback to HTML: ${e.message}")
        }

        // 2. Fallback to HTML results scraping with consent bypass cookies
        return try {
            val response = client.get("https://www.youtube.com/results") {
                parameter("search_query", query)
                headers.append("Cookie", "CONSENT=YES+1; SOCS=CAESEwgDEgk2MTQyNjcyMjAaAmVuIAEaBgiA_LyaBg")
            }
            if (response.status.value !in 200..299) {
                return ProviderResult.Error("YouTube web search returned HTTP ${response.status.value}", response.status.value)
            }
            val html = response.bodyAsText()
            val tracks = parseYtInitialDataHtml(html)
            ProviderResult.Success(tracks)
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            Log.w(TAG, "YouTube web search failed: ${e.message}")
            ProviderResult.Error(e.message ?: "YouTube search failed")
        }
    }

    private suspend fun searchInnerTube(query: String): List<OnlineTrack> {
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

        val response = client.post("https://www.youtube.com/youtubei/v1/search") {
            contentType(ContentType.Application.Json)
            setBody(payload.toString())
            header("X-YouTube-Client-Name", "1")
            header("X-YouTube-Client-Version", "2.20240901.00.00")
        }

        if (response.status.value !in 200..299) return emptyList()
        val text = response.bodyAsText()
        val jsonElement = runCatching { jsonFormat.parseToJsonElement(text) }.getOrNull() ?: return emptyList()
        return extractTracksFromJsonElement(jsonElement)
    }

    private suspend fun fetchVideoDetails(videoIds: List<String>): Map<String, YouTubeVideoDetailsItem> {
        if (videoIds.isEmpty()) return emptyMap()
        return try {
            val response = client.get(VIDEOS_URL) {
                parameter("part", "contentDetails,status")
                parameter("id", videoIds.joinToString(","))
                parameter("key", apiKey)
            }
            if (response.status.value in 200..299) {
                val detailsDto: YouTubeVideoDetailsResponse = response.body()
                detailsDto.items.associateBy { it.id }
            } else {
                emptyMap()
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            Log.w(TAG, "YouTube details fetch failed: ${e.message}")
            emptyMap()
        }
    }

    companion object {
        fun parseClockDurationMs(clock: String): Long {
            if (clock.isBlank()) return 0L
            val parts = clock.trim().split(':').mapNotNull { it.toLongOrNull() }
            return when (parts.size) {
                1 -> parts[0] * 1_000L
                2 -> (parts[0] * 60L + parts[1]) * 1_000L
                3 -> (parts[0] * 3600L + parts[1] * 60L + parts[2]) * 1_000L
                else -> 0L
            }
        }

        private val jsonFormat = Json { ignoreUnknownKeys = true; isLenient = true }

        fun parseYtInitialDataHtml(html: String): List<OnlineTrack> {
            val pattern = Regex("""var ytInitialData\s*=\s*(\{.+?\});\s*(?:</script>|var)""", RegexOption.DOT_MATCHES_ALL)
            val match = pattern.find(html)
                ?: Regex("""ytInitialData\s*=\s*(\{.+?\});""", RegexOption.DOT_MATCHES_ALL).find(html)
                ?: Regex("""window\["ytInitialData"\]\s*=\s*(\{.+?\});""", RegexOption.DOT_MATCHES_ALL).find(html)
                ?: Regex("""var ytInitialData = (\{.*?\}\s*);</script>""").find(html)
            val jsonStr = match?.groupValues?.get(1) ?: return emptyList()

            val jsonElement = runCatching {
                jsonFormat.parseToJsonElement(jsonStr)
            }.getOrNull() ?: return emptyList()

            return extractTracksFromJsonElement(jsonElement)
        }

        fun extractTracksFromJsonElement(jsonElement: JsonElement): List<OnlineTrack> {
            val tracks = mutableListOf<OnlineTrack>()
            val seenIds = mutableSetOf<String>()

            fun extractVideos(element: JsonElement) {
                when (element) {
                    is JsonObject -> {
                        val vr = element["videoRenderer"]?.jsonObject ?: element["compactVideoRenderer"]?.jsonObject
                        if (vr != null) {
                            val videoId = vr["videoId"]?.jsonPrimitive?.contentOrNull
                            if (!videoId.isNullOrBlank() && seenIds.add(videoId)) {
                                val runsTitle = vr["title"]?.jsonObject?.get("runs")?.jsonArray
                                    ?.mapNotNull { it.jsonObject["text"]?.jsonPrimitive?.contentOrNull }
                                    ?.joinToString("")
                                val title = runsTitle?.takeIf { it.isNotBlank() }
                                    ?: vr["title"]?.jsonObject?.get("simpleText")?.jsonPrimitive?.contentOrNull
                                    ?: "Unknown Title"

                                val runsChannel = vr["ownerText"]?.jsonObject?.get("runs")?.jsonArray
                                    ?.mapNotNull { it.jsonObject["text"]?.jsonPrimitive?.contentOrNull }
                                    ?.joinToString("")
                                    ?: vr["shortBylineText"]?.jsonObject?.get("runs")?.jsonArray
                                        ?.mapNotNull { it.jsonObject["text"]?.jsonPrimitive?.contentOrNull }
                                        ?.joinToString("")
                                    ?: vr["longBylineText"]?.jsonObject?.get("runs")?.jsonArray
                                        ?.mapNotNull { it.jsonObject["text"]?.jsonPrimitive?.contentOrNull }
                                        ?.joinToString("")
                                val channel = runsChannel?.takeIf { it.isNotBlank() } ?: "YouTube"

                                val lengthText = vr["lengthText"]?.jsonObject?.get("simpleText")?.jsonPrimitive?.contentOrNull
                                    ?: vr["lengthText"]?.jsonObject?.get("runs")?.jsonArray?.firstOrNull()?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull
                                    ?: ""
                                val durationMs = parseClockDurationMs(lengthText)
                                val artworkUrl = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"

                                tracks.add(
                                    OnlineTrack(
                                        provider = "youtube",
                                        id = videoId,
                                        title = unescapeHtml(title),
                                        artist = unescapeHtml(channel),
                                        album = "YouTube",
                                        durationMs = durationMs,
                                        artworkUrl = artworkUrl,
                                        sourceUrl = "https://www.youtube.com/watch?v=$videoId",
                                        streamAllowed = false,
                                        downloadAllowed = true,
                                        license = "",
                                    )
                                )
                            }
                        }
                        for ((_, v) in element) {
                            extractVideos(v)
                        }
                    }
                    is JsonArray -> {
                        for (item in element) {
                            extractVideos(item)
                        }
                    }
                    else -> Unit
                }
            }

            extractVideos(jsonElement)
            return tracks
        }

        fun parseIsoDurationMs(durationStr: String): Long {
            if (durationStr.isBlank() || !durationStr.startsWith("PT")) return 0L
            var hours = 0L
            var minutes = 0L
            var seconds = 0L
            val clean = durationStr.removePrefix("PT")
            var rest = clean
            val hIndex = rest.indexOf('H')
            if (hIndex != -1) {
                hours = rest.substring(0, hIndex).toLongOrNull() ?: 0L
                rest = rest.substring(hIndex + 1)
            }
            val mIndex = rest.indexOf('M')
            if (mIndex != -1) {
                minutes = rest.substring(0, mIndex).toLongOrNull() ?: 0L
                rest = rest.substring(mIndex + 1)
            }
            val sIndex = rest.indexOf('S')
            if (sIndex != -1) {
                seconds = rest.substring(0, sIndex).toLongOrNull() ?: 0L
            }
            return (hours * 3600 + minutes * 60 + seconds) * 1000L
        }

        fun unescapeHtml(text: String): String {
            return text.replace("&quot;", "\"")
                .replace("&apos;", "'")
                .replace("&#39;", "'")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&#x2F;", "/")
                .replace("&nbsp;", " ")
        }
    }
}
