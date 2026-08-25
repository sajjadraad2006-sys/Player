package dev.auralis.studio.online

import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.HttpTimeout
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
private const val USER_AGENT = "Lyrune/0.2.0 (Android)"

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
class JamendoClient(
    private val clientId: String,
    private val defaultQuality: () -> DownloadQuality = { DownloadQuality.BEST_AVAILABLE },
) : OnlineMusicProvider, MusicStreamProvider, MusicDownloadProvider {

    override val id: String = "jamendo"

    override val name: String = "Jamendo"

    override val isConfigured: Boolean
        get() = clientId.isNotBlank()

    private val client by lazy {
        HttpClient(OkHttp) {
            expectSuccess = true
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
        return try {
            val offset = page * 20
            val resp = client.get("$BASE/tracks/") {
                parameter("client_id", clientId)
                parameter("format", "json")
                parameter("namesearch", query)
                parameter("include", "musicinfo licenses")
                parameter("audioformat", "mp32")
                parameter("audiodlformat", requestedFormat(defaultQuality()))
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
                        return ProviderResult.Error(body.headers.errorMessage, body.headers.code)
                    }
                    ProviderResult.Success(body.results.map { it.toOnlineTrack(requestedFormat(defaultQuality())) })
                }
                else -> ProviderResult.Error("HTTP ${resp.status.value}", resp.status.value)
            }
        } catch (e: io.ktor.client.plugins.ClientRequestException) {
            when (e.response.status) {
                HttpStatusCode.TooManyRequests -> ProviderResult.RateLimited
                HttpStatusCode.Unauthorized, HttpStatusCode.Forbidden -> ProviderResult.Unauthorized
                HttpStatusCode.NotFound -> ProviderResult.Unavailable
                else -> ProviderResult.Error("Jamendo request failed", e.response.status.value)
            }
        } catch (e: java.io.IOException) {
            ProviderResult.NetworkUnavailable
        } catch (e: Exception) {
            Log.e(TAG, "Search failed: ${e.message}")
            ProviderResult.Error("Jamendo search failed")
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
                parameter("audioformat", "mp32")
                parameter("audiodlformat", requestedFormat(defaultQuality()))
                parameter("imagesize", 600)
            }
            if (resp.status != HttpStatusCode.OK) {
                return ProviderResult.Error("HTTP ${resp.status.value}")
            }
            val body = resp.body<JamendoResponse>()
            val track = body.results.firstOrNull()
                ?: return ProviderResult.Error("Track not found")
            ProviderResult.Success(track.toOnlineTrack(requestedFormat(defaultQuality())))
        } catch (e: io.ktor.client.plugins.ClientRequestException) {
            when (e.response.status) {
                HttpStatusCode.TooManyRequests -> ProviderResult.RateLimited
                HttpStatusCode.Unauthorized, HttpStatusCode.Forbidden -> ProviderResult.Unauthorized
                HttpStatusCode.NotFound -> ProviderResult.Unavailable
                else -> ProviderResult.Error("Jamendo request failed", e.response.status.value)
            }
        } catch (e: java.io.IOException) {
            ProviderResult.NetworkUnavailable
        } catch (e: Exception) {
            ProviderResult.Error("Jamendo request failed")
        }
    }

    override suspend fun resolveStream(track: OnlineTrack): ProviderResult<StreamResource> {
        if (track.provider != id || !track.streamAllowed || track.streamUrl.isNullOrBlank()) {
            return ProviderResult.Unavailable
        }
        return ProviderResult.Success(StreamResource(track.streamUrl, MediaUrlKind.STREAM, "mp3", "VBR stream"))
    }

    override suspend fun resolveDownload(
        track: OnlineTrack,
        quality: DownloadQuality,
    ): ProviderResult<DownloadResource> {
        if (!isConfigured) return ProviderResult.NotConfigured
        if (track.provider != id) return ProviderResult.Unavailable
        val format = requestedFormat(quality)
        val resolved = when (val fresh = fetchTrack(track.id, format)) {
            is ProviderResult.Success -> fresh.data
            ProviderResult.RateLimited -> return ProviderResult.RateLimited
            ProviderResult.NotConfigured -> return ProviderResult.NotConfigured
            ProviderResult.NetworkUnavailable -> return ProviderResult.NetworkUnavailable
            ProviderResult.Unauthorized -> return ProviderResult.Unauthorized
            ProviderResult.Unavailable -> return ProviderResult.Unavailable
            is ProviderResult.Error -> return ProviderResult.Error(fresh.message, fresh.code)
        }
        if (!resolved.downloadAllowed || resolved.downloadUrl.isNullOrBlank()) return ProviderResult.Unauthorized
        return ProviderResult.Success(
            DownloadResource(
                url = resolved.downloadUrl,
                format = resolved.audioFormat,
                qualityLabel = resolved.qualityLabel,
                isOriginal = format == "flac",
            ),
        )
    }

    private suspend fun fetchTrack(trackId: String, downloadFormat: String): ProviderResult<OnlineTrack> = try {
        val resp = client.get("$BASE/tracks/") {
            parameter("client_id", clientId)
            parameter("format", "json")
            parameter("id", trackId)
            parameter("include", "musicinfo licenses")
            parameter("audioformat", "mp32")
            parameter("audiodlformat", downloadFormat)
            parameter("imagesize", 600)
        }
        val body = resp.body<JamendoResponse>()
        if (body.headers.status == "error") ProviderResult.Error(body.headers.errorMessage, body.headers.code)
        else body.results.firstOrNull()?.let { ProviderResult.Success(it.toOnlineTrack(downloadFormat)) }
            ?: ProviderResult.Unavailable
    } catch (e: io.ktor.client.plugins.ClientRequestException) {
        when (e.response.status) {
            HttpStatusCode.TooManyRequests -> ProviderResult.RateLimited
            HttpStatusCode.Unauthorized, HttpStatusCode.Forbidden -> ProviderResult.Unauthorized
            HttpStatusCode.NotFound -> ProviderResult.Unavailable
            else -> ProviderResult.Error("Jamendo request failed", e.response.status.value)
        }
    } catch (e: java.io.IOException) {
        ProviderResult.NetworkUnavailable
    } catch (e: Exception) {
        ProviderResult.Error("Jamendo request failed")
    }

    internal fun mapFixture(dto: JamendoTrackDto, downloadFormat: String = "flac") = dto.toOnlineTrack(downloadFormat)

    /**
     * Parse a raw Jamendo /tracks/ JSON payload for testing without network access.
     * Uses the same JSON decoder and mapping path as the real search() call.
     */
    internal fun parseSearchFixture(payload: String): List<OnlineTrack> {
        val json = Json { ignoreUnknownKeys = true; isLenient = true }
        val response = json.decodeFromString<JamendoResponse>(payload)
        return response.results.map { it.toOnlineTrack(requestedFormat(DownloadQuality.BEST_AVAILABLE)) }
    }

    private fun JamendoTrackDto.toOnlineTrack(downloadFormat: String) = OnlineTrack(
        provider        = "jamendo",
        id              = id,
        title           = name,
        artist          = artistName,
        album           = albumName,
        durationMs      = duration * 1000L,
        artworkUrl      = image,
        // audio = full-length stream; audioDownload = authorized download (only if permitted)
        previewUrl      = null,
        streamUrl       = audio.ifBlank { null },
        downloadUrl     = if (audioDownloadAllowed) audioDownload.ifBlank { null } else null,
        streamAllowed   = audio.isNotBlank(),
        downloadAllowed = audioDownloadAllowed,
        audioFormat     = if (audioDownloadAllowed) downloadFormat.removeSuffix("1").removeSuffix("2") else "mp3",
        availableDownloadFormats = if (audioDownloadAllowed) listOf("flac", "mp32", "ogg", "mp31") else emptyList(),
        qualityLabel    = qualityLabel(downloadFormat),
        license         = license_ccurl,
        licenseUrl      = license_ccurl,
        sourceUrl       = shareurl,
    )

    internal fun requestedFormat(quality: DownloadQuality): String = when (quality) {
        DownloadQuality.BEST_AVAILABLE -> "flac"
        DownloadQuality.HIGH -> "mp32"
        DownloadQuality.DATA_SAVER -> "mp31"
    }

    private fun qualityLabel(format: String): String = when (format) {
        "flac" -> "FLAC"
        "mp32" -> "MP3 VBR high"
        "ogg" -> "Ogg Vorbis"
        else -> "MP3 96 kbps"
    }

    fun close() {
        runCatching { client.close() }
    }
}
