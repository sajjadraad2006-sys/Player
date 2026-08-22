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

/** Represents an audio track from an online provider. */
data class OnlineTrack(
    val provider: String,
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val artworkUrl: String,
    val streamUrl: String?,
    val downloadUrl: String?,
    val downloadAllowed: Boolean,
    val license: String,
    val licenseUrl: String = "",
    val sourceUrl: String = "",
)

/** Converts an [OnlineTrack] to a playable [Track] (for streaming). */
fun OnlineTrack.toStreamableTrack(): Track? {
    val url = streamUrl ?: return null
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
 */
interface OnlineMusicProvider {
    val name: String
    val isConfigured: Boolean

    suspend fun search(query: String, page: Int = 0): ProviderResult<List<OnlineTrack>>
    suspend fun getTrack(id: String): ProviderResult<OnlineTrack>
}
