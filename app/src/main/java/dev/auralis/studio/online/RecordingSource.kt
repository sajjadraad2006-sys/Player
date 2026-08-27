package dev.auralis.studio.online

import dev.auralis.studio.core.model.Track
import dev.auralis.studio.resolver.api.DownloadCapability
import dev.auralis.studio.resolver.api.DownloadCapabilityBasis
import dev.auralis.studio.resolver.api.ResolutionMode

/**
 * Universal model for an individual recording source candidate discovered from any catalog or local library.
 *
 * Invariant: Preserves explicit URL semantics:
 * - [canonicalUrl] is the stable provider webpage / permalink (e.g. https://audius.co/artist/song).
 * - Temporary signed CDN URLs or ephemeral streaming tokens must NEVER be stored as canonical source state.
 */
sealed interface RecordingSource {
    val provider: String
    val providerTrackId: String
    val title: String
    val artist: String
    val album: String
    val albumArtist: String?
    val durationMs: Long
    val artworkUrl: String
    val isrc: String?
    val playbackCapability: PlaybackCapability
    val downloadCapability: DownloadCapability
    val downloadCapabilityBasis: DownloadCapabilityBasis
    val resolutionMode: ResolutionMode
    val audioFormat: String?
    val availableDownloadFormats: List<String>
    val qualityLabel: String?
    val canonicalUrl: String
    val license: String
    val licenseUrl: String

    val isLocal: Boolean get() = this is LocalRecordingSource

    val isDownloadableFullTrack: Boolean
        get() = downloadCapability == DownloadCapability.PERMITTED &&
            resolutionMode == ResolutionMode.DIRECT_RESOURCE
}

/**
 * Candidate source originating from an online music provider (Audius, Jamendo, iTunes, Deezer, etc.).
 */
data class OnlineRecordingSource(
    override val provider: String,
    override val providerTrackId: String,
    override val title: String,
    override val artist: String,
    override val album: String = "",
    override val albumArtist: String? = null,
    override val durationMs: Long = 0L,
    override val artworkUrl: String = "",
    override val isrc: String? = null,
    override val playbackCapability: PlaybackCapability = PlaybackCapability.UNAVAILABLE,
    override val downloadCapability: DownloadCapability = DownloadCapability.UNKNOWN,
    override val downloadCapabilityBasis: DownloadCapabilityBasis = DownloadCapabilityBasis.NONE,
    override val resolutionMode: ResolutionMode = ResolutionMode.DIRECT_RESOURCE,
    override val audioFormat: String? = null,
    override val availableDownloadFormats: List<String> = emptyList(),
    override val qualityLabel: String? = null,
    override val canonicalUrl: String = "",
    override val license: String = "",
    override val licenseUrl: String = "",
    // Original legacy OnlineTrack reference for backward-compatible provider resolution
    val originalTrack: OnlineTrack? = null,
) : RecordingSource

/**
 * Candidate source originating from local on-device MediaStore / SAF storage.
 */
data class LocalRecordingSource(
    override val provider: String = "local",
    override val providerTrackId: String,
    val localDatabaseId: Long,
    val contentUri: String,
    override val title: String,
    override val artist: String = "",
    override val album: String = "",
    override val albumArtist: String? = null,
    override val durationMs: Long = 0L,
    override val artworkUrl: String = "",
    override val isrc: String? = null,
    override val playbackCapability: PlaybackCapability = PlaybackCapability.LOCAL,
    override val downloadCapability: DownloadCapability = DownloadCapability.PERMITTED,
    override val downloadCapabilityBasis: DownloadCapabilityBasis = DownloadCapabilityBasis.PROVIDER_EXPLICIT,
    override val resolutionMode: ResolutionMode = ResolutionMode.DIRECT_RESOURCE,
    override val audioFormat: String? = "audio/mpeg",
    override val availableDownloadFormats: List<String> = emptyList(),
    override val qualityLabel: String? = "Local",
    override val canonicalUrl: String = contentUri,
    override val license: String = "Local file",
    override val licenseUrl: String = "",
    val originalLocalTrack: Track? = null,
) : RecordingSource

/**
 * Maps an existing [OnlineTrack] to a neutral [RecordingSource] with explicit capabilities.
 */
fun OnlineTrack.toRecordingSource(): RecordingSource {
    val playbackCap = when {
        streamAllowed && !streamUrl.isNullOrBlank() -> PlaybackCapability.FULL_STREAM
        !previewUrl.isNullOrBlank() -> PlaybackCapability.PREVIEW
        else -> PlaybackCapability.UNAVAILABLE
    }

    val (downloadCap, downloadBasis) = when (provider) {
        "audius" -> if (downloadAllowed && !downloadUrl.isNullOrBlank()) {
            DownloadCapability.PERMITTED to DownloadCapabilityBasis.PROVIDER_EXPLICIT
        } else {
            DownloadCapability.STREAM_ONLY to DownloadCapabilityBasis.PROVIDER_EXPLICIT
        }
        "jamendo" -> if (downloadAllowed && !downloadUrl.isNullOrBlank()) {
            DownloadCapability.PERMITTED to DownloadCapabilityBasis.PROVIDER_EXPLICIT
        } else {
            DownloadCapability.STREAM_ONLY to DownloadCapabilityBasis.PROVIDER_EXPLICIT
        }
        "itunes", "deezer" -> DownloadCapability.NOT_PERMITTED to DownloadCapabilityBasis.PROVIDER_EXPLICIT
        else -> DownloadCapability.UNKNOWN to DownloadCapabilityBasis.NONE
    }

    return OnlineRecordingSource(
        provider = provider,
        providerTrackId = id,
        title = title,
        artist = artist,
        album = album,
        albumArtist = albumArtist,
        durationMs = durationMs,
        artworkUrl = artworkUrl,
        isrc = isrc,
        playbackCapability = playbackCap,
        downloadCapability = downloadCap,
        downloadCapabilityBasis = downloadBasis,
        resolutionMode = ResolutionMode.DIRECT_RESOURCE,
        audioFormat = audioFormat,
        availableDownloadFormats = availableDownloadFormats,
        qualityLabel = qualityLabel,
        canonicalUrl = sourceUrl,
        license = license,
        licenseUrl = licenseUrl,
        originalTrack = this,
    )
}

/**
 * Maps a local on-device [Track] to a [LocalRecordingSource].
 */
fun Track.toLocalRecordingSource(): LocalRecordingSource = LocalRecordingSource(
    providerTrackId = id.toString(),
    localDatabaseId = id,
    contentUri = uri,
    title = title,
    artist = artist,
    album = album,
    albumArtist = null,
    durationMs = durationMs,
    artworkUrl = artworkUri.orEmpty(),
    isrc = null,
    playbackCapability = PlaybackCapability.LOCAL,
    downloadCapability = DownloadCapability.PERMITTED,
    downloadCapabilityBasis = DownloadCapabilityBasis.PROVIDER_EXPLICIT,
    resolutionMode = ResolutionMode.DIRECT_RESOURCE,
    audioFormat = mimeType,
    qualityLabel = "On-Device",
    canonicalUrl = uri,
    originalLocalTrack = this,
)
