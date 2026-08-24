package dev.auralis.studio.online

import dev.auralis.studio.core.model.Track

/** Result type for online provider operations. */
sealed class ProviderResult<out T> {
    data class Success<T>(val data: T) : ProviderResult<T>()
    data class Error(val message: String, val code: Int = 0) : ProviderResult<Nothing>()
    object NotConfigured : ProviderResult<Nothing>()
    object NetworkUnavailable : ProviderResult<Nothing>()
    object RateLimited : ProviderResult<Nothing>()
}

/**
 * Explicit capability of a remote audio URL.
 *
 * A URL must never silently change meaning: a [PREVIEW] asset (~30 s promo clip)
 * is NOT a full track and must never be ingested into the Library as one.
 */
enum class MediaUrlKind { PREVIEW, STREAM, FULL_DOWNLOAD }

/** Represents an audio track from an online provider. */
data class OnlineTrack(
    val provider: String,
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    /** Expected duration of the FULL track according to catalog metadata. */
    val durationMs: Long,
    val artworkUrl: String,
    /** Short promotional preview (~30 s) or null. */
    val previewUrl: String?,
    /** Full-length stream or null. */
    val streamUrl: String?,
    /** Authorized full-track download URL or null. */
    val downloadUrl: String?,
    /** True only when [downloadUrl] points to a genuine authorized full-track download. */
    val downloadAllowed: Boolean,
    val license: String,
    val licenseUrl: String = "",
    val sourceUrl: String = "",
) {
    /** Stable cross-restart download identity: `provider:id`. */
    val downloadKey: String get() = "$provider:$id"

    /** Best playable URL for immediate streaming (full stream preferred over preview). */
    val bestStreamUrl: String? get() = streamUrl ?: previewUrl

    /**
     * What the app may legally persist locally.
     * Preview-only results must surface as "Preview" in the UI, never "Download".
     */
    val isDownloadableFullTrack: Boolean get() = downloadAllowed && !downloadUrl.isNullOrBlank()
}

/** Converts an [OnlineTrack] to a playable [Track] (for streaming). */
fun OnlineTrack.toStreamableTrack(): Track? {
    val url = bestStreamUrl ?: return null
    return Track(
        id                = -1L,
        uri               = url,
        title             = title,
        artist            = artist,
        album             = album,
        albumId           = null,
        durationMs        = durationMs,
        dateAddedSeconds  = 0L,
        displayName       = "$artist - $title",
        mimeType          = null,
        sizeBytes         = 0L,
        trackNumber       = null,
        year              = null,
    )
}

/**
 * Provider architecture contract.
 * Implementations must be stateless and independently togglable.
 * All methods return [ProviderResult] — callers never see raw exceptions.
 *
 * A catalog provider (search/metadata/preview) is distinct from a download
 * provider: being searchable does NOT authorize full-track downloads.
 */
interface OnlineMusicProvider {
    val name: String
    val isConfigured: Boolean

    suspend fun search(query: String, page: Int = 0): ProviderResult<List<OnlineTrack>>
    suspend fun getTrack(id: String): ProviderResult<OnlineTrack>
}
