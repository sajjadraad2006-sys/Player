package dev.auralis.studio.online

import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull

private const val AUDIUS_TAG = "AudiusClient"
private const val AUDIUS_BASE = "https://api.audius.co/v1"
private const val AUDIUS_USER_AGENT = "Lyrune/0.2.0 (Android)"

@Serializable
internal data class AudiusResponse<T>(val data: T)

@Serializable
internal data class AudiusTrackDto(
    val id: String = "",
    val title: String = "",
    val duration: Int = 0,
    // Live API contract (verified 2026-08): "is_downloadable" / "is_download_gated".
    // Legacy "downloadable" kept only as a fallback for cached/older payloads.
    val downloadable: Boolean? = null,
    @SerialName("is_downloadable") val isDownloadable: Boolean? = null,
    @SerialName("is_download_gated") val isDownloadGated: Boolean = false,
    @SerialName("is_streamable") val isStreamable: Boolean? = null,
    @SerialName("is_stream_gated") val isStreamGated: Boolean = false,
    @SerialName("download_conditions") val downloadConditions: JsonElement? = null,
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

/**
 * Official Audius REST provider.
 *
 * Read endpoints are public. [apiKey] is optional and only used as the client-safe API key for
 * increased rate limits; a backend bearer token is intentionally unsupported in this Android app.
 */
class AudiusClient(
    private val apiKey: String,
    private val httpClient: HttpClient? = null,
) : OnlineMusicProvider, MusicStreamProvider, MusicDownloadProvider {
    override val id: String = "audius"
    override val name: String = "Audius"
    override val isConfigured: Boolean = true

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val client: HttpClient by lazy {
        httpClient ?: HttpClient(OkHttp) {
            expectSuccess = true
            engine {
                config {
                    dns(AudiusDns)
                }
            }
            install(ContentNegotiation) { json(json) }
            install(HttpTimeout) {
                connectTimeoutMillis = 10_000
                requestTimeoutMillis = 15_000
                socketTimeoutMillis = 20_000
            }
        }
    }

    override suspend fun search(query: String, page: Int): ProviderResult<List<OnlineTrack>> {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return ProviderResult.Success(emptyList())
        return request {
            client.get("$AUDIUS_BASE/tracks/search") {
                header("User-Agent", AUDIUS_USER_AGENT)
                addAppIdentification()
                parameter("query", trimmed)
                parameter("limit", 25)
                parameter("offset", page * 25)
                parameter("sort_method", "relevant")
            }.body<AudiusResponse<List<AudiusTrackDto>>>().data.map(::mapTrack)
        }
    }

    override suspend fun getTrack(id: String): ProviderResult<OnlineTrack> = request {
        client.get("$AUDIUS_BASE/tracks/$id") {
            header("User-Agent", AUDIUS_USER_AGENT)
            addAppIdentification()
        }.body<AudiusResponse<AudiusTrackDto>>().data.let(::mapTrack)
    }

    override suspend fun resolveStream(track: OnlineTrack): ProviderResult<StreamResource> {
        if (track.provider != id) return ProviderResult.Unavailable
        val fresh = when (val result = getTrack(track.id)) {
            is ProviderResult.Success -> result.data
            is ProviderResult.RateLimited -> return result
            is ProviderResult.Unauthorized -> return result
            is ProviderResult.NetworkUnavailable -> return result
            else -> return ProviderResult.Unavailable
        }
        if (!fresh.streamAllowed || fresh.streamUrl.isNullOrBlank()) return ProviderResult.Unavailable
        return ProviderResult.Success(StreamResource(fresh.streamUrl, MediaUrlKind.STREAM, "mp3", "320 kbps stream"))
    }

    override suspend fun resolveDownload(
        track: OnlineTrack,
        quality: DownloadQuality,
    ): ProviderResult<DownloadResource> {
        if (track.provider != id) return ProviderResult.Unavailable
        val fresh = when (val result = getTrack(track.id)) {
            is ProviderResult.Success -> result.data
            is ProviderResult.RateLimited -> return result
            is ProviderResult.Unauthorized -> return result
            is ProviderResult.NetworkUnavailable -> return result
            else -> return ProviderResult.Unavailable
        }
        if (!fresh.downloadAllowed || fresh.downloadUrl.isNullOrBlank()) return ProviderResult.Unauthorized
        // Audius documents original=true as the creator-uploaded original/full file.
        return ProviderResult.Success(
            DownloadResource(
                url = fresh.downloadUrl,
                format = fresh.audioFormat,
                qualityLabel = "Original",
                isOriginal = true,
            ),
        )
    }

    internal fun parseSearchFixture(payload: String): List<OnlineTrack> =
        json.decodeFromString<AudiusResponse<List<AudiusTrackDto>>>(payload).data.map(::mapTrack)

    internal fun mapTrack(dto: AudiusTrackDto): OnlineTrack {
        val streamable = dto.isStreamable != false && !dto.isStreamGated
        val declaredDownloadable = dto.downloadable == true || dto.isDownloadable == true
        val gatedDownload = dto.isDownloadGated ||
            (dto.downloadConditions != null && dto.downloadConditions !is JsonNull)
        val freelyDownloadable = declaredDownloadable && !gatedDownload
        val format = dto.originalFileName?.substringAfterLast('.', "")?.lowercase()?.takeIf(String::isNotBlank)
        return OnlineTrack(
            provider = id,
            id = dto.id,
            title = dto.title,
            artist = dto.user?.name?.ifBlank { dto.user.handle } ?: "",
            album = dto.albumName.orEmpty(),
            albumArtist = dto.user?.name?.takeIf(String::isNotBlank),
            durationMs = dto.duration * 1_000L,
            artworkUrl = dto.artwork?.large ?: dto.artwork?.medium ?: dto.artwork?.small.orEmpty(),
            isrc = dto.isrc,
            previewUrl = null,
            streamUrl = if (streamable) "$AUDIUS_BASE/tracks/${dto.id}/stream?app_name=Lyrune" else null,
            downloadUrl = if (freelyDownloadable) "$AUDIUS_BASE/tracks/${dto.id}/download?original=true&app_name=Lyrune" else null,
            streamAllowed = streamable,
            downloadAllowed = freelyDownloadable,
            audioFormat = format,
            availableDownloadFormats = if (freelyDownloadable) listOf(format ?: "original") else emptyList(),
            qualityLabel = if (freelyDownloadable) "Original" else "320 kbps stream",
            originalFileName = dto.originalFileName,
            license = dto.license.orEmpty(),
            sourceUrl = dto.permalink.takeIf { it.startsWith("http") } ?: "https://audius.co${dto.permalink}",
        )
    }

    private suspend fun <T> request(block: suspend () -> T): ProviderResult<T> = try {
        ProviderResult.Success(block())
    } catch (e: io.ktor.client.plugins.ClientRequestException) {
        when (e.response.status) {
            HttpStatusCode.TooManyRequests -> ProviderResult.RateLimited
            HttpStatusCode.Unauthorized, HttpStatusCode.Forbidden -> ProviderResult.Unauthorized
            HttpStatusCode.NotFound -> ProviderResult.Unavailable
            else -> ProviderResult.Error("Audius request failed", e.response.status.value)
        }
    } catch (e: io.ktor.client.plugins.ServerResponseException) {
        ProviderResult.Error("Audius is temporarily unavailable", e.response.status.value)
    } catch (e: java.io.IOException) {
        ProviderResult.NetworkUnavailable
    } catch (e: Exception) {
        Log.w(AUDIUS_TAG, "Request failed: ${e.javaClass.simpleName}")
        ProviderResult.Error("Audius request failed")
    }

    /** Matches the current official SDK addAppInfoMiddleware query contract. */
    private fun io.ktor.client.request.HttpRequestBuilder.addAppIdentification() {
        if (apiKey.isNotBlank()) parameter("api_key", apiKey) else parameter("app_name", "Lyrune")
    }

    fun close() {
        if (httpClient == null) runCatching { client.close() }
    }
}

/**
 * Fallback DNS resolver for Audius.
 * Tries standard system DNS first; if resolution fails due to local/ISP UDP DNS glitches,
 * falls back to the official Cloudflare Anycast IPs for api.audius.co.
 */
internal object AudiusDns : okhttp3.Dns {
    override fun lookup(hostname: String): List<java.net.InetAddress> = try {
        okhttp3.Dns.SYSTEM.lookup(hostname)
    } catch (e: java.net.UnknownHostException) {
        if (hostname.equals("api.audius.co", ignoreCase = true) ||
            hostname.equals("discoveryprovider.audius.co", ignoreCase = true)
        ) {
            listOf(
                java.net.InetAddress.getByAddress(hostname, byteArrayOf(104.toByte(), 18.toByte(), 24.toByte(), 162.toByte())),
                java.net.InetAddress.getByAddress(hostname, byteArrayOf(104.toByte(), 18.toByte(), 25.toByte(), 162.toByte())),
            )
        } else {
            throw e
        }
    }
}

