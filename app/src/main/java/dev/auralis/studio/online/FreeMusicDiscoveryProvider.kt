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

            // Sort by version-aware ranking (Original Studio first unless requested)
            val queryLower = trimmed.lowercase()
            val hasRemixInQuery = "remix" in queryLower || "mix" in queryLower
            val hasLiveInQuery = "live" in queryLower || "concert" in queryLower
            val hasAcousticInQuery = "acoustic" in queryLower || "unplugged" in queryLower
            val hasSpedInQuery = "sped" in queryLower || "nightcore" in queryLower
            val hasSlowedInQuery = "slowed" in queryLower
            val hasInstrumentalInQuery = "instrumental" in queryLower || "karaoke" in queryLower
            val hasCoverInQuery = "cover" in queryLower

            val ranked = distinct.sortedWith(
                compareByDescending<OnlineTrack> { track ->
                    var score = 100
                    val titleLower = track.title.lowercase()

                    if (titleLower == queryLower) score += 200
                    else if (titleLower.startsWith(queryLower)) score += 100
                    else if (titleLower.contains(queryLower)) score += 50

                    val isRemix = "remix" in titleLower || " mix" in titleLower
                    val isLive = "live" in titleLower || "concert" in titleLower
                    val isAcoustic = "acoustic" in titleLower || "unplugged" in titleLower
                    val isSped = "sped up" in titleLower || "nightcore" in titleLower
                    val isSlowed = "slowed" in titleLower
                    val isInstrumental = "instrumental" in titleLower || "karaoke" in titleLower
                    val isCover = "cover" in titleLower

                    if (isRemix && !hasRemixInQuery) score -= 40
                    if (isLive && !hasLiveInQuery) score -= 35
                    if (isAcoustic && !hasAcousticInQuery) score -= 30
                    if (isSped && !hasSpedInQuery) score -= 45
                    if (isSlowed && !hasSlowedInQuery) score -= 45
                    if (isInstrumental && !hasInstrumentalInQuery) score -= 40
                    if (isCover && !hasCoverInQuery) score -= 35

                    if (isRemix && hasRemixInQuery) score += 60
                    if (isLive && hasLiveInQuery) score += 60
                    if (isAcoustic && hasAcousticInQuery) score += 60
                    if (isSped && hasSpedInQuery) score += 60
                    if (isSlowed && hasSlowedInQuery) score += 60

                    score
                }
            )

            ProviderResult.Success(ranked)
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
                    // iTunes previewUrl is a ~30-second promotional clip.
                    // It is a PREVIEW only — never a download or full stream.
                    val preview = item.previewUrl ?: return@mapNotNull null
                    val highResArt = item.artworkUrl100?.replace("100x100bb.jpg", "600x600bb.jpg") ?: ""
                    val genre = item.primaryGenreName ?: "Audio"
                    OnlineTrack(
                        provider        = "itunes",
                        id              = "itunes_${item.trackId}",
                        title           = item.trackName,
                        artist          = item.artistName,
                        album           = item.collectionName ?: genre,
                        durationMs      = item.trackTimeMillis,
                        artworkUrl      = highResArt,
                        previewUrl      = preview,
                        streamUrl       = null,
                        downloadUrl     = null,
                        downloadAllowed = false,
                        license         = "Catalog search / 30s preview only",
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
                    // Deezer preview is a ~30-second promotional clip — PREVIEW only.
                    val preview = item.preview ?: return@mapNotNull null
                    val art = item.album?.coverBig ?: item.album?.coverMedium ?: ""
                    OnlineTrack(
                        provider        = "deezer",
                        id              = "deezer_${item.id}",
                        title           = item.title,
                        artist          = item.artist?.name ?: "Unknown",
                        album           = item.album?.title ?: "Single",
                        durationMs      = item.duration * 1000L,
                        artworkUrl      = art,
                        previewUrl      = preview,
                        streamUrl       = null,
                        downloadUrl     = null,
                        downloadAllowed = false,
                        license         = "Catalog search / 30s preview only",
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
