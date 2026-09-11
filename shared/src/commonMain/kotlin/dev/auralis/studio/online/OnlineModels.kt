package dev.auralis.studio.online

import kotlinx.serialization.Serializable

/**
 * Domain model describing the playback capability of a recording source.
 *
 * Deterministic ranking order:
 * LOCAL > FULL_STREAM > PREVIEW > AUTH_REQUIRED > UNAVAILABLE
 */
@Serializable
enum class PlaybackCapability {
    /** On-device local audio file, confirmed readable. */
    LOCAL,
    /** Verified full-length streamable audio. */
    FULL_STREAM,
    /** Promotional preview clip (e.g. 30 seconds). */
    PREVIEW,
    /** Requires private authentication or subscription tier. */
    AUTH_REQUIRED,
    /** No playback mechanism available. */
    UNAVAILABLE;

    val isPlayable: Boolean
        get() = this == LOCAL || this == FULL_STREAM || this == PREVIEW

    val isFullTrack: Boolean
        get() = this == LOCAL || this == FULL_STREAM
}

/** A remote URL always has one explicit meaning. */
@Serializable
enum class MediaUrlKind {
    PREVIEW,
    STREAM,
    FULL_DOWNLOAD,
}

@Serializable
enum class DownloadQuality {
    BEST_AVAILABLE,
    HIGH,
    DATA_SAVER,
}

@Serializable
data class StreamResource(
    val url: String,
    val kind: MediaUrlKind,
    val format: String? = null,
    val qualityLabel: String? = null,
)

@Serializable
data class DownloadResource(
    val url: String,
    val format: String? = null,
    val qualityLabel: String? = null,
    val isOriginal: Boolean = false,
)

/** Result type for one provider operation. Raw transport exceptions never reach UI code. */
@Serializable
sealed class ProviderResult<out T> {
    @Serializable
    data class Success<T>(val data: T) : ProviderResult<T>()
    @Serializable
    data class Error(val message: String, val code: Int = 0) : ProviderResult<Nothing>()
    @Serializable
    data object NotConfigured : ProviderResult<Nothing>()
    @Serializable
    data object NetworkUnavailable : ProviderResult<Nothing>()
    @Serializable
    data object RateLimited : ProviderResult<Nothing>()
    @Serializable
    data object Unauthorized : ProviderResult<Nothing>()
    @Serializable
    data object Unavailable : ProviderResult<Nothing>()
}

/**
 * Normalized provider recording. Preview, stream, and authorized full-download URLs are distinct.
 */
@Serializable
data class OnlineTrack(
    val provider: String,
    val id: String,
    val title: String,
    val artist: String,
    val album: String = "",
    val albumArtist: String? = null,
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
    val downloadKey: String get() = "$provider:$id"

    val bestStreamUrl: String?
        get() = streamUrl.takeIf { streamAllowed && !it.isNullOrBlank() } ?: previewUrl

    val bestStreamKind: MediaUrlKind?
        get() = when {
            streamAllowed && !streamUrl.isNullOrBlank() -> MediaUrlKind.STREAM
            !previewUrl.isNullOrBlank() -> MediaUrlKind.PREVIEW
            else -> null
        }

    val playbackCapability: PlaybackCapability
        get() = when {
            streamAllowed && !streamUrl.isNullOrBlank() -> PlaybackCapability.FULL_STREAM
            !previewUrl.isNullOrBlank() -> PlaybackCapability.PREVIEW
            else -> PlaybackCapability.UNAVAILABLE
        }

    val isDownloadableFullTrack: Boolean
        get() = downloadAllowed && !downloadUrl.isNullOrBlank()
}

/**
 * Common catalog provider interface.
 */
interface MusicCatalogProvider {
    val id: String
    val displayName: String
    val isConfigured: Boolean
    suspend fun search(query: String, page: Int = 0): ProviderResult<List<OnlineTrack>>
}
