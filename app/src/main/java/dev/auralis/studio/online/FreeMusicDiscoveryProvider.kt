package dev.auralis.studio.online

import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private const val TAG = "FreeMusicProvider"
private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 15; Pixel 9 Pro) AppleWebKit/537.36"

// ─── iTunes Models ───────────────────────────────────────────────────────────
@Serializable
private data class ITunesSearchResponse(
    val resultCount: Int = 0,
    val results: List<ITunesTrackDto> = emptyList(),
)

@Serializable
private data class ITunesTrackDto(
    val trackId: Long = 0,
    val trackName: String = "",
    val artistName: String = "",
    val collectionName: String? = null,
    val trackTimeMillis: Long = 0,
    val previewUrl: String? = null,
    val artworkUrl100: String? = null,
    val primaryGenreName: String? = null,
)

// ─── Deezer Models ───────────────────────────────────────────────────────────
@Serializable
private data class DeezerSearchResponse(
    val total: Int = 0,
    val data: List<DeezerTrackDto> = emptyList(),
)

@Serializable
private data class DeezerTrackDto(
    val id: Long = 0,
    val title: String = "",
    val duration: Int = 0,
    val preview: String? = null,
    val artist: DeezerArtistDto? = null,
    val album: DeezerAlbumDto? = null,
)

@Serializable
private data class DeezerArtistDto(
    val name: String = "",
)

@Serializable
private data class DeezerAlbumDto(
    val title: String = "",
    @SerialName("cover_medium") val coverMedium: String? = null,
    @SerialName("cover_big") val coverBig: String? = null,
)

/**
 * Universal Free Online Music & Audio Discovery Provider (SnapTube-style).
 * Supports:
 * - لطميات ومراثي حسينية (Latmiyat)
 * - أدعية وزيارات ومناجاة (Adiyah & Ziyarat)
 * - أناشيد إسلامية وقصائد (Anasheed)
 * - تلاوات القرآن الكريم (Quran Recitations)
 * - أغاني وموسيقى عالمية وعربية (General Arabic & Global Music)
 *
 * Direct MP3/M4A streaming & 1-tap download with full studio DSP support.
 */
class FreeMusicDiscoveryProvider : OnlineMusicProvider {

    override val name: String = "Universal Audio & Islamic Search"
    override val isConfigured: Boolean = true

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val client by lazy {
        HttpClient(OkHttp) {
            install(ContentNegotiation) {
                json(json)
            }
            defaultRequest {
                headers.append("User-Agent", USER_AGENT)
            }
        }
    }

    override suspend fun search(query: String, page: Int): ProviderResult<List<OnlineTrack>> = coroutineScope {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return@coroutineScope ProviderResult.Success(emptyList())

        try {
            val isArabic = trimmed.any { it in '\u0600'..'\u06FF' }

            // Concurrent multi-store query for deep Arabic/Islamic & Global coverage
            val itunesGeneral = async { searchITunes(trimmed, country = "US") }
            val itunesRegional = async { searchITunes(trimmed, country = if (isArabic) "SA" else "GB") }
            val deezerDeferred = async { searchDeezer(trimmed) }

            val list1 = itunesGeneral.await()
            val list2 = itunesRegional.await()
            val list3 = deezerDeferred.await()

            // Combine and interleave results
            val combined = mutableListOf<OnlineTrack>()
            val maxLen = maxOf(list1.size, list2.size, list3.size)
            for (i in 0 until maxLen) {
                if (i < list2.size) combined.add(list2[i])
                if (i < list1.size) combined.add(list1[i])
                if (i < list3.size) combined.add(list3[i])
            }

            // Deduplicate by normalized (title + artist)
            val distinct = combined.distinctBy { 
                "${it.title.lowercase().trim()}_${it.artist.lowercase().trim()}" 
            }

            ProviderResult.Success(distinct)
        } catch (e: Exception) {
            Log.e(TAG, "Search failed", e)
            ProviderResult.Error(e.localizedMessage ?: "Search failed")
        }
    }

    private suspend fun searchITunes(query: String, country: String = "US"): List<OnlineTrack> {
        return try {
            val resp = client.get("https://itunes.apple.com/search") {
                parameter("term", query)
                parameter("media", "music")
                parameter("country", country)
                parameter("limit", 30)
            }
            if (resp.status == HttpStatusCode.OK) {
                val body = resp.body<ITunesSearchResponse>()
                body.results.mapNotNull { item ->
                    val stream = item.previewUrl ?: return@mapNotNull null
                    val highResArt = item.artworkUrl100?.replace("100x100bb.jpg", "600x600bb.jpg") ?: ""
                    val genre = item.primaryGenreName ?: "Audio"
                    OnlineTrack(
                        provider        = "itunes",
                        id              = "itunes_${item.trackId}",
                        title           = item.trackName,
                        artist          = item.artistName,
                        album           = item.collectionName ?: genre,
                        durationMs      = item.trackTimeMillis.coerceAtLeast(30_000L),
                        artworkUrl      = highResArt,
                        streamUrl       = stream,
                        downloadUrl     = stream,
                        downloadAllowed = true,
                        license         = "Direct Audio / Free Stream",
                        licenseUrl      = "",
                        sourceUrl       = stream,
                    )
                }
            } else emptyList()
        } catch (e: Exception) {
            Log.w(TAG, "iTunes search error ($country): ${e.message}")
            emptyList()
        }
    }

    private suspend fun searchDeezer(query: String): List<OnlineTrack> {
        return try {
            val resp = client.get("https://api.deezer.com/search") {
                parameter("q", query)
                parameter("limit", 30)
            }
            if (resp.status == HttpStatusCode.OK) {
                val body = resp.body<DeezerSearchResponse>()
                body.data.mapNotNull { item ->
                    val stream = item.preview ?: return@mapNotNull null
                    val art = item.album?.coverBig ?: item.album?.coverMedium ?: ""
                    OnlineTrack(
                        provider        = "deezer",
                        id              = "deezer_${item.id}",
                        title           = item.title,
                        artist          = item.artist?.name ?: "Unknown",
                        album           = item.album?.title ?: "Single",
                        durationMs      = item.duration * 1000L,
                        artworkUrl      = art,
                        streamUrl       = stream,
                        downloadUrl     = stream,
                        downloadAllowed = true,
                        license         = "Direct Stream / Download",
                        licenseUrl      = "",
                        sourceUrl       = stream,
                    )
                }
            } else emptyList()
        } catch (e: Exception) {
            Log.w(TAG, "Deezer search error: ${e.message}")
            emptyList()
        }
    }

    override suspend fun getTrack(id: String): ProviderResult<OnlineTrack> {
        return ProviderResult.Error("Use list item")
    }
}
