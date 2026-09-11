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

private const val TAG = "DesktopJamendoAdapter"
private const val BASE = "https://api.jamendo.com/v3.0"
private const val USER_AGENT = "Lyrune/1.0.0 (Windows Desktop)"

@Serializable
private data class JamendoResponse(
    val results: List<JamendoTrackDto> = emptyList(),
    val headers: JamendoHeaders = JamendoHeaders(),
)

@Serializable
private data class JamendoHeaders(
    val status: String = "success",
    val code: Int = 0,
    @SerialName("error_message") val errorMessage: String = "",
)

@Serializable
internal data class JamendoTrackDto(
    val id: String = "",
    val name: String = "",
    @SerialName("artist_name") val artistName: String = "",
    @SerialName("album_name") val albumName: String = "",
    val duration: Int = 0,
    val image: String = "",
    val audio: String = "",
    @SerialName("audiodownload") val audioDownload: String = "",
    @SerialName("audiodownload_allowed") val audioDownloadAllowed: Boolean = false,
    val license_ccurl: String = "",
    val shareurl: String = "",
)

class DesktopJamendoAdapter(
    val clientId: String = "",
    customHttpClient: HttpClient? = null,
) : MusicCatalogProvider {

    override val id: String = "jamendo"
    override val displayName: String = "Jamendo"
    override val isConfigured: Boolean get() = clientId.isNotBlank()

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
        if (!isConfigured) return ProviderResult.NotConfigured
        val trimmed = query.trim()
        if (trimmed.isBlank()) return ProviderResult.Success(emptyList())

        return try {
            val offset = page * 20
            val resp = client.get("$BASE/tracks/") {
                parameter("client_id", clientId)
                parameter("format", "json")
                parameter("namesearch", trimmed)
                parameter("include", "musicinfo licenses")
                parameter("audioformat", "mp32")
                parameter("audiodlformat", "mp32")
                parameter("imagesize", 600)
                parameter("limit", 20)
                parameter("offset", offset)
                parameter("order", "popularity_total")
            }

            when (resp.status) {
                HttpStatusCode.TooManyRequests -> ProviderResult.RateLimited
                HttpStatusCode.OK -> {
                    val body = resp.body<JamendoResponse>()
                    if (body.headers.status == "error") {
                        DesktopLogger.warn(TAG, "Jamendo API error: ${body.headers.errorMessage} (code=${body.headers.code})")
                        return ProviderResult.Error(body.headers.errorMessage, body.headers.code)
                    }
                    val tracks = body.results.map { it.toOnlineTrack() }
                    ProviderResult.Success(tracks)
                }
                else -> ProviderResult.Error("HTTP ${resp.status.value}", resp.status.value)
            }
        } catch (e: io.ktor.client.plugins.ClientRequestException) {
            when (e.response.status) {
                HttpStatusCode.TooManyRequests -> ProviderResult.RateLimited
                HttpStatusCode.Unauthorized, HttpStatusCode.Forbidden -> ProviderResult.Unauthorized
                HttpStatusCode.NotFound -> ProviderResult.Unavailable
                else -> ProviderResult.Error("Jamendo request failed: HTTP ${e.response.status.value}", e.response.status.value)
            }
        } catch (e: IOException) {
            DesktopLogger.warn(TAG, "Jamendo network unavailable: ${e.message}")
            ProviderResult.NetworkUnavailable
        } catch (e: Exception) {
            DesktopLogger.error(TAG, "Jamendo search failed: ${e.message}", e)
            ProviderResult.Error("Jamendo search error: ${e.message}")
        }
    }

    private fun JamendoTrackDto.toOnlineTrack(): OnlineTrack {
        return OnlineTrack(
            provider = id,
            id = this.id,
            title = name,
            artist = artistName,
            album = albumName,
            durationMs = duration * 1000L,
            artworkUrl = image,
            previewUrl = audio.takeIf { it.isNotBlank() },
            streamUrl = audio.takeIf { it.isNotBlank() },
            downloadUrl = audioDownload.takeIf { audioDownloadAllowed && it.isNotBlank() },
            streamAllowed = audio.isNotBlank(),
            downloadAllowed = audioDownloadAllowed && audioDownload.isNotBlank(),
            audioFormat = "audio/mpeg",
            license = license_ccurl,
            licenseUrl = license_ccurl,
            sourceUrl = shareurl,
        )
    }
}
