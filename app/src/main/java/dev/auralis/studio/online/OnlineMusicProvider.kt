package dev.auralis.studio.online

import dev.auralis.studio.core.model.Track

/** Result type for one provider operation. Raw transport exceptions never reach UI code. */
sealed class ProviderResult<out T> {
    data class Success<T>(val data: T) : ProviderResult<T>()
    data class Error(val message: String, val code: Int = 0) : ProviderResult<Nothing>()
    object NotConfigured : ProviderResult<Nothing>()
    object NetworkUnavailable : ProviderResult<Nothing>()
    object RateLimited : ProviderResult<Nothing>()
    object Unauthorized : ProviderResult<Nothing>()
    object Unavailable : ProviderResult<Nothing>()
}

/** A remote URL always has one explicit meaning. */
enum class MediaUrlKind { PREVIEW, STREAM, FULL_DOWNLOAD }

enum class DownloadQuality { BEST_AVAILABLE, HIGH, DATA_SAVER }

data class StreamResource(
    val url: String,
    val kind: MediaUrlKind,
    val format: String? = null,
    val qualityLabel: String? = null,
)

data class DownloadResource(
    val url: String,
    val format: String? = null,
    val qualityLabel: String? = null,
    val isOriginal: Boolean = false,
)

interface MusicCatalogProvider {
    val id: String
    val displayName: String
    val isConfigured: Boolean
    suspend fun search(query: String, page: Int = 0): ProviderResult<List<OnlineTrack>>
}

interface MusicStreamProvider {
    val id: String
    suspend fun resolveStream(track: OnlineTrack): ProviderResult<StreamResource>
}

interface MusicDownloadProvider {
    val id: String
    suspend fun resolveDownload(
        track: OnlineTrack,
        quality: DownloadQuality = DownloadQuality.BEST_AVAILABLE,
    ): ProviderResult<DownloadResource>
}

/**
 * Normalized provider recording. Preview, stream and authorized full-download URLs are distinct.
 */
data class OnlineTrack(
    val provider: String,
    val id: String,
    val title: String,
    val artist: String,
    val album: String = "",
    val albumArtist: String? = null,
    /** Expected duration of the complete recording according to provider metadata. */
    val durationMs: Long = 0,
    val artworkUrl: String = "",
    val isrc: String? = null,
    val previewUrl: String? = null,
    val streamUrl: String? = null,
    val downloadUrl: String? = null,
    val streamAllowed: Boolean = !streamUrl.isNullOrBlank(),
    val downloadAllowed: Boolean = false,
    val audioFormat: String? = null,
    val availableDownloadFormats: List<String> = emptyList(),
    val qualityLabel: String? = null,
    val originalFileName: String? = null,
    val license: String = "",
    val licenseUrl: String = "",
    val sourceUrl: String = "",
) {
    /** Provider-scoped job identity, never a recording identity. */
    val downloadKey: String get() = "$provider:$id"

    val bestStreamUrl: String?
        get() = streamUrl.takeIf { streamAllowed && !it.isNullOrBlank() } ?: previewUrl

    val bestStreamKind: MediaUrlKind?
        get() = when {
            streamAllowed && !streamUrl.isNullOrBlank() -> MediaUrlKind.STREAM
            !previewUrl.isNullOrBlank() -> MediaUrlKind.PREVIEW
            else -> null
        }

    val isDownloadableFullTrack: Boolean
        get() = downloadAllowed && !downloadUrl.isNullOrBlank()

    val structuredIdentity: StructuredIdentity get() = RecordingIdentity.forTrack(this)
    val recordingIdentity: String get() = structuredIdentity.stableKey
}

typealias ProviderTrack = OnlineTrack

/** Backward-compatible base used by existing providers while capabilities are split explicitly. */
interface OnlineMusicProvider : MusicCatalogProvider {
    val name: String
    override val displayName: String get() = name
    override val id: String get() = name.lowercase().replace(' ', '_')
    suspend fun getTrack(id: String): ProviderResult<OnlineTrack>
}

fun OnlineTrack.toStreamableTrack(): Track? {
    val url = bestStreamUrl ?: return null
    return Track(
        id = -1L,
        uri = url,
        title = title,
        artist = artist,
        album = album,
        albumId = null,
        durationMs = durationMs,
        dateAddedSeconds = 0L,
        displayName = "$artist - $title",
        mimeType = audioFormat,
        sizeBytes = 0L,
        trackNumber = null,
        year = null,
    )
}
