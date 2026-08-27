package dev.auralis.studio.online

import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private const val TAG = "YouTubeCatalog"
private const val SEARCH_URL = "https://www.googleapis.com/youtube/v3/search"
private const val VIDEOS_URL = "https://www.googleapis.com/youtube/v3/videos"
private const val USER_AGENT = "Lyrune/0.2.0 (Android)"

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
 * 4. Never marks generic YouTube search results as download-permitted (streamAllowed=false, downloadAllowed=false).
 * 5. Handles missing API keys gracefully with [ProviderResult.NotConfigured].
 * 6. Never logs secrets or API keys.
 */
class YouTubeCatalogProvider(
    private val apiKey: String,
) : MusicCatalogProvider {

    override val id: String = "youtube"
    override val displayName: String = "YouTube"

    override val isConfigured: Boolean
        get() = apiKey.isNotBlank()

    private val client by lazy {
        HttpClient(OkHttp) {
            expectSuccess = false
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
            }
        }
    }

    override suspend fun search(query: String, page: Int): ProviderResult<List<OnlineTrack>> {
        if (!isConfigured) return ProviderResult.NotConfigured
        val trimmed = query.trim()
        if (trimmed.isBlank()) return ProviderResult.Success(emptyList())

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
                    downloadAllowed = false,
                    license = licenseStr,
                )
            }

            ProviderResult.Success(tracks)
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            Log.w(TAG, "YouTube search failed: ${e.message}")
            ProviderResult.Error(e.message ?: "YouTube search failed")
        }
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
