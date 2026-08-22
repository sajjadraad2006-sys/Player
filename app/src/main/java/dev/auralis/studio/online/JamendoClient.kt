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
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private const val TAG = "JamendoClient"
private const val BASE = "https://api.jamendo.com/v3.0"
private const val USER_AGENT = "Auralis Studio/0.2.0 (Android)"

// ─── Jamendo API response models ─────────────────────────────────────────────

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
    @SerialName("x-ratelimit-limit") val rateLimitLimit: Int = 0,
    @SerialName("x-ratelimit-remaining") val rateLimitRemaining: Int = 0,
)

@Serializable
private data class JamendoTrackDto(
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

/**
 * Jamendo API client.
 *
 * **A real client_id issued through the Jamendo developer portal is required.**
 * If [clientId] is blank, [isConfigured] returns false and all methods return
 * [ProviderResult.NotConfigured]. The app still compiles and local playback
 * is unaffected.
 *
 * Download is only offered when [JamendoTrackDto.audioDownloadAllowed] == true.
 */
class JamendoClient(private val clientId: String) : OnlineMusicProvider {

    override val name: String = "Jamendo"

    override val isConfigured: Boolean
        get() = clientId.isNotBlank()

    private val client by lazy {
        HttpClient(OkHttp) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; isLenient = true })
            }
            defaultRequest {
                headers.append("User-Agent", USER_AGENT)
            }
        }
    }

    override suspend fun search(query: String, page: Int): ProviderResult<List<OnlineTrack>> {
        if (!isConfigured) return ProviderResult.NotConfigured
        return try {
            val offset = page * 20
            val resp = client.get("$BASE/tracks/") {
                parameter("client_id", clientId)
                parameter("format", "json")
                parameter("namesearch", query)
                parameter("include", "musicinfo licenses")
                parameter("audioformat", "mp31")
                parameter("limit", 20)
                parameter("offset", offset)
                parameter("order", "popularity_total")
            }
            when (resp.status) {
                HttpStatusCode.TooManyRequests -> ProviderResult.RateLimited
                HttpStatusCode.OK -> {
                    val body = resp.body<JamendoResponse>()
                    if (body.headers.status == "error") {
                        return ProviderResult.Error(body.headers.errorMessage, body.headers.code)
                    }
                    ProviderResult.Success(body.results.map { it.toOnlineTrack() })
                }
                else -> ProviderResult.Error("HTTP ${resp.status.value}", resp.status.value)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Search failed: ${e.message}")
            ProviderResult.Error(e.message ?: "Unknown error")
        }
    }

    override suspend fun getTrack(id: String): ProviderResult<OnlineTrack> {
        if (!isConfigured) return ProviderResult.NotConfigured
        return try {
            val resp = client.get("$BASE/tracks/") {
                parameter("client_id", clientId)
                parameter("format", "json")
                parameter("id", id)
                parameter("include", "musicinfo licenses")
                parameter("audioformat", "mp31")
            }
            if (resp.status != HttpStatusCode.OK) {
                return ProviderResult.Error("HTTP ${resp.status.value}")
            }
            val body = resp.body<JamendoResponse>()
            val track = body.results.firstOrNull()
                ?: return ProviderResult.Error("Track not found")
            ProviderResult.Success(track.toOnlineTrack())
        } catch (e: Exception) {
            ProviderResult.Error(e.message ?: "Unknown error")
        }
    }

    private fun JamendoTrackDto.toOnlineTrack() = OnlineTrack(
        provider        = "jamendo",
        id              = id,
        title           = name,
        artist          = artistName,
        album           = albumName,
        durationMs      = duration * 1000L,
        artworkUrl      = image,
        // audio = streaming preview; audioDownload = download (only if permitted)
        streamUrl       = audio.ifBlank { null },
        downloadUrl     = if (audioDownloadAllowed) audioDownload.ifBlank { null } else null,
        downloadAllowed = audioDownloadAllowed,
        license         = license_ccurl,
        licenseUrl      = license_ccurl,
        sourceUrl       = shareurl,
    )

    fun close() {
        runCatching { client.close() }
    }
}
