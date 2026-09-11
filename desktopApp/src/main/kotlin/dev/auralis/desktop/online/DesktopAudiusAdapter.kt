package dev.auralis.desktop.online

import dev.auralis.desktop.logging.DesktopLogger
import dev.auralis.studio.online.MusicCatalogProvider
import dev.auralis.studio.online.OnlineTrack
import dev.auralis.studio.online.ProviderResult
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.IOException

private const val TAG = "DesktopAudiusAdapter"
private const val BASE = "https://api.audius.co/v1"
private const val USER_AGENT = "Lyrune/1.0.0 (Windows Desktop)"
private const val APP_NAME = "Lyrune"

@Serializable
private data class AudiusResponse<T>(val data: T)

@Serializable
internal data class AudiusTrackDto(
    val id: String = "",
    val title: String = "",
    val duration: Int = 0,
    val downloadable: Boolean? = null,
    @SerialName("is_downloadable") val isDownloadable: Boolean? = null,
    @SerialName("is_download_gated") val isDownloadGated: Boolean = false,
    @SerialName("is_streamable") val isStreamable: Boolean? = null,
    @SerialName("is_stream_gated") val isStreamGated: Boolean = false,
    val artwork: AudiusArtworkDto? = null,
    val user: AudiusUserDto? = null,
    val isrc: String? = null,
    val permalink: String = "",
    @SerialName("album_name") val albumName: String? = null,
    @SerialName("orig_filename") val originalFileName: String? = null,
    val genre: String? = null,
    val license: String? = null,
)

@Serializable
internal data class AudiusArtworkDto(
    @SerialName("1000x1000") val large: String? = null,
    @SerialName("480x480") val medium: String? = null,
    @SerialName("150x150") val small: String? = null,
)

@Serializable
internal data class AudiusUserDto(
    val name: String = "",
    val handle: String = "",
)

class DesktopAudiusAdapter(
    val apiKey: String = "",
    customHttpClient: HttpClient? = null,
) : MusicCatalogProvider {

    override val id: String = "audius"
    override val displayName: String = "Audius"
    override val isConfigured: Boolean = true

    private val client: HttpClient by lazy {
        customHttpClient ?: HttpClient(CIO) {
            expectSuccess = true
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; isLenient = true })
            }
            install(HttpTimeout) {
                connectTimeoutMillis = 8_000
                requestTimeoutMillis = 10_000
                socketTimeoutMillis = 12_000
            }
            defaultRequest {
                headers.append("User-Agent", USER_AGENT)
            }
        }
    }

    override suspend fun search(query: String, page: Int): ProviderResult<List<OnlineTrack>> {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return ProviderResult.Success(emptyList())

        return try {
            val resp = client.get("$BASE/tracks/search") {
                parameter("query", trimmed)
                parameter("app_name", APP_NAME)
                if (apiKey.isNotBlank()) {
                    parameter("api_key", apiKey)
                }
            }

            when (resp.status) {
                HttpStatusCode.TooManyRequests -> ProviderResult.RateLimited
                HttpStatusCode.OK -> {
                    val body = resp.body<AudiusResponse<List<AudiusTrackDto>>>()
                    val tracks = body.data.map { it.toOnlineTrack() }
                    ProviderResult.Success(tracks)
                }
                else -> ProviderResult.Error("HTTP ${resp.status.value}", resp.status.value)
            }
        } catch (e: io.ktor.client.plugins.ClientRequestException) {
            when (e.response.status) {
                HttpStatusCode.TooManyRequests -> ProviderResult.RateLimited
                HttpStatusCode.Unauthorized, HttpStatusCode.Forbidden -> ProviderResult.Unauthorized
                HttpStatusCode.NotFound -> ProviderResult.Unavailable
                else -> ProviderResult.Error("Audius request failed: HTTP ${e.response.status.value}", e.response.status.value)
            }
        } catch (e: IOException) {
            DesktopLogger.warn(TAG, "Audius network unavailable: ${e.message}")
            ProviderResult.NetworkUnavailable
        } catch (e: Exception) {
            DesktopLogger.error(TAG, "Audius search failed: ${e.message}", e)
            ProviderResult.Error("Audius search error: ${e.message}")
        }
    }

    private fun AudiusTrackDto.toOnlineTrack(): OnlineTrack {
        val artistName = user?.name?.takeIf { it.isNotBlank() } ?: user?.handle.orEmpty()
        val streamable = (isStreamable != false) && !isStreamGated
        val downloadableDirect = (isDownloadable == true || (isDownloadable == null && downloadable == true)) && !isDownloadGated
        val streamUrl = if (streamable && id.isNotBlank()) {
            "$BASE/tracks/$id/stream?app_name=$APP_NAME"
        } else null
        val downloadUrl = if (downloadableDirect && id.isNotBlank()) {
            "$BASE/tracks/$id/stream?app_name=$APP_NAME"
        } else null

        val artworkUrl = artwork?.large
            ?: artwork?.medium
            ?: artwork?.small
            .orEmpty()

        val fullSourceUrl = if (permalink.isNotBlank()) {
            if (permalink.startsWith("http")) permalink else "https://audius.co$permalink"
        } else ""

        return OnlineTrack(
            provider = id,
            id = this.id,
            title = title,
            artist = artistName,
            album = albumName.orEmpty(),
            durationMs = duration * 1000L,
            artworkUrl = artworkUrl,
            isrc = isrc,
            previewUrl = streamUrl,
            streamUrl = streamUrl,
            downloadUrl = downloadUrl,
            streamAllowed = streamable,
            downloadAllowed = downloadableDirect,
            audioFormat = "audio/mpeg",
            license = license.orEmpty(),
            sourceUrl = fullSourceUrl,
            originalFileName = originalFileName,
        )
    }
}
