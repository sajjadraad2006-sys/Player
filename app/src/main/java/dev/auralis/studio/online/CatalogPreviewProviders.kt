package dev.auralis.studio.online

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class ITunesResponse(val results: List<ITunesItem> = emptyList())

@Serializable
private data class ITunesItem(
    val trackId: Long = 0,
    val trackName: String = "",
    val artistName: String = "",
    val collectionName: String? = null,
    val trackTimeMillis: Long = 0,
    val previewUrl: String? = null,
    val artworkUrl100: String? = null,
    val trackViewUrl: String? = null,
    val isrc: String? = null,
)

class ITunesCatalogProvider : OnlineMusicProvider {
    override val id = "itunes"
    override val name = "iTunes"
    override val isConfigured = true
    private val client = previewClient()

    override suspend fun search(query: String, page: Int): ProviderResult<List<OnlineTrack>> = safePreviewRequest {
        val country = if (query.any { it in '\u0600'..'\u06FF' }) "SA" else "US"
        val response = client.get("https://itunes.apple.com/search") {
            parameter("term", query.trim())
            parameter("media", "music")
            parameter("entity", "song")
            parameter("country", country)
            parameter("limit", 30)
            parameter("offset", page * 30)
        }
        if (response.status != HttpStatusCode.OK) return@safePreviewRequest httpFailure(response.status)
        ProviderResult.Success(response.body<ITunesResponse>().results.mapNotNull { item ->
            val preview = item.previewUrl?.takeIf(String::isNotBlank) ?: return@mapNotNull null
            OnlineTrack(
                provider = id,
                id = item.trackId.toString(),
                title = item.trackName,
                artist = item.artistName,
                album = item.collectionName.orEmpty(),
                durationMs = item.trackTimeMillis,
                artworkUrl = item.artworkUrl100?.replace("100x100bb.jpg", "600x600bb.jpg").orEmpty(),
                isrc = item.isrc,
                previewUrl = preview,
                streamUrl = null,
                downloadUrl = null,
                streamAllowed = false,
                downloadAllowed = false,
                audioFormat = "m4a",
                qualityLabel = "30 sec preview",
                license = "Catalog / promotional preview",
                sourceUrl = item.trackViewUrl.orEmpty(),
            )
        })
    }

    override suspend fun getTrack(id: String): ProviderResult<OnlineTrack> = ProviderResult.Unavailable
}

@Serializable
private data class DeezerResponse(val data: List<DeezerItem> = emptyList())

@Serializable
private data class DeezerItem(
    val id: Long = 0,
    val title: String = "",
    val duration: Int = 0,
    val preview: String? = null,
    val link: String? = null,
    val isrc: String? = null,
    val artist: DeezerArtist? = null,
    val album: DeezerAlbum? = null,
)

@Serializable
private data class DeezerArtist(val name: String = "")

@Serializable
private data class DeezerAlbum(
    val title: String = "",
    @SerialName("cover_big") val coverBig: String? = null,
    @SerialName("cover_medium") val coverMedium: String? = null,
)

class DeezerCatalogProvider : OnlineMusicProvider {
    override val id = "deezer"
    override val name = "Deezer"
    override val isConfigured = true
    private val client = previewClient()

    override suspend fun search(query: String, page: Int): ProviderResult<List<OnlineTrack>> = safePreviewRequest {
        val response = client.get("https://api.deezer.com/search") {
            parameter("q", query.trim())
            parameter("limit", 30)
            parameter("index", page * 30)
        }
        if (response.status != HttpStatusCode.OK) return@safePreviewRequest httpFailure(response.status)
        ProviderResult.Success(response.body<DeezerResponse>().data.mapNotNull { item ->
            val preview = item.preview?.takeIf(String::isNotBlank) ?: return@mapNotNull null
            OnlineTrack(
                provider = id,
                id = item.id.toString(),
                title = item.title,
                artist = item.artist?.name.orEmpty(),
                album = item.album?.title.orEmpty(),
                durationMs = item.duration * 1_000L,
                artworkUrl = item.album?.coverBig ?: item.album?.coverMedium.orEmpty(),
                isrc = item.isrc,
                previewUrl = preview,
                streamUrl = null,
                downloadUrl = null,
                streamAllowed = false,
                downloadAllowed = false,
                audioFormat = "mp3",
                qualityLabel = "Preview",
                license = "Catalog / promotional preview",
                sourceUrl = item.link.orEmpty(),
            )
        })
    }

    override suspend fun getTrack(id: String): ProviderResult<OnlineTrack> = ProviderResult.Unavailable
}

private fun previewClient() = HttpClient(OkHttp) {
    install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true; isLenient = true }) }
    install(HttpTimeout) {
        connectTimeoutMillis = 8_000
        requestTimeoutMillis = 10_000
        socketTimeoutMillis = 12_000
    }
}

private suspend fun safePreviewRequest(
    block: suspend () -> ProviderResult<List<OnlineTrack>>,
): ProviderResult<List<OnlineTrack>> = try {
    block()
} catch (cancelled: kotlinx.coroutines.CancellationException) {
    throw cancelled
} catch (_: java.io.IOException) {
    ProviderResult.NetworkUnavailable
} catch (_: Exception) {
    ProviderResult.Error("Catalog provider unavailable")
}

private fun httpFailure(status: HttpStatusCode): ProviderResult<Nothing> = when (status) {
    HttpStatusCode.TooManyRequests -> ProviderResult.RateLimited
    HttpStatusCode.Unauthorized, HttpStatusCode.Forbidden -> ProviderResult.Unauthorized
    HttpStatusCode.NotFound -> ProviderResult.Unavailable
    else -> ProviderResult.Error("Catalog request failed", status.value)
}
