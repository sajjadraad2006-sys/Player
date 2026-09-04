package dev.auralis.studio.online

import dev.auralis.studio.core.model.Track
import dev.auralis.studio.resolver.api.DownloadCapability
import dev.auralis.studio.resolver.api.DownloadCapabilityBasis
import dev.auralis.studio.resolver.api.ResolutionMode

enum class IsrcEvidenceBasis {
    PROVIDER_EXPLICIT,
    EMBEDDED_METADATA,
    INFERRED,
}

data class IsrcEvidence(
    val value: String,
    val basis: IsrcEvidenceBasis,
) {
    val isTrusted: Boolean get() = basis != IsrcEvidenceBasis.INFERRED
}

/**
 * Universal model for an individual recording source candidate discovered from any catalog or local library.
 *
 * Invariant: Preserves explicit URL semantics:
 * - [canonicalUrl] is the stable provider webpage / permalink (e.g. https://audius.co/artist/song), or null if none.
 * - Local content URIs and temporary signed CDN URLs must NEVER be stored as canonical URLs.
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
    val isrcEvidence: IsrcEvidence?
    val playbackCapability: PlaybackCapability
    val downloadCapability: DownloadCapability
    val downloadCapabilityBasis: DownloadCapabilityBasis
    val resolutionMode: ResolutionMode
    val audioFormat: String?
    val availableDownloadFormats: List<String>
    val qualityLabel: String?
    val canonicalUrl: String?
    val license: String
    val licenseUrl: String

    val isLocal: Boolean get() = this is LocalRecordingSource

    val isDownloadableFullTrack: Boolean
        get() = !isLocal &&
            downloadCapability == DownloadCapability.PERMITTED &&
            resolutionMode == ResolutionMode.DIRECT_RESOURCE

    val trustedIsrc: String?
        get() = isrcEvidence?.takeIf { it.isTrusted }?.value
            ?: RecordingIdentity.normalizedIsrc(isrc)
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
    override val isrcEvidence: IsrcEvidence? = isrc?.let { norm ->
        RecordingIdentity.normalizedIsrc(norm)?.let { IsrcEvidence(it, IsrcEvidenceBasis.PROVIDER_EXPLICIT) }
    },
    override val playbackCapability: PlaybackCapability = PlaybackCapability.UNAVAILABLE,
    override val downloadCapability: DownloadCapability = DownloadCapability.UNKNOWN,
    override val downloadCapabilityBasis: DownloadCapabilityBasis = DownloadCapabilityBasis.NONE,
    override val resolutionMode: ResolutionMode = ResolutionMode.DIRECT_RESOURCE,
    override val audioFormat: String? = null,
    override val availableDownloadFormats: List<String> = emptyList(),
    override val qualityLabel: String? = null,
    override val canonicalUrl: String? = null,
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
    override val isrcEvidence: IsrcEvidence? = isrc?.let { norm ->
        RecordingIdentity.normalizedIsrc(norm)?.let { IsrcEvidence(it, IsrcEvidenceBasis.EMBEDDED_METADATA) }
    },
    override val playbackCapability: PlaybackCapability = PlaybackCapability.LOCAL,
    override val downloadCapability: DownloadCapability = DownloadCapability.NOT_PERMITTED,
    override val downloadCapabilityBasis: DownloadCapabilityBasis = DownloadCapabilityBasis.NONE,
    override val resolutionMode: ResolutionMode = ResolutionMode.DIRECT_RESOURCE,
    override val audioFormat: String? = "audio/mpeg",
    override val availableDownloadFormats: List<String> = emptyList(),
    override val qualityLabel: String? = "Local",
    override val canonicalUrl: String? = null,
    override val license: String = "Local file",
    override val licenseUrl: String = "",
    val originalLocalTrack: Track? = null,
) : RecordingSource {
    override val isDownloadableFullTrack: Boolean get() = false
}

/**
 * Maps an existing [OnlineTrack] to a neutral [RecordingSource] with explicit capabilities.
 */
fun OnlineTrack.toRecordingSource(): RecordingSource {
    val playbackCap = when {
        provider.equals("youtube", true) -> PlaybackCapability.FULL_STREAM
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
        "youtube" -> DownloadCapability.PERMITTED to DownloadCapabilityBasis.PROVIDER_EXPLICIT
        else -> DownloadCapability.UNKNOWN to DownloadCapabilityBasis.NONE
    }

    val normIsrc = RecordingIdentity.normalizedIsrc(isrc)
    val isrcEv = normIsrc?.let { IsrcEvidence(it, IsrcEvidenceBasis.PROVIDER_EXPLICIT) }

    // Strip ephemeral delivery URLs so canonical sources are strictly metadata-only
    val metadataBridgeTrack = this.copy(
        previewUrl = null,
        streamUrl = null,
        downloadUrl = null,
    )

    return OnlineRecordingSource(
        provider = provider,
        providerTrackId = id,
        title = title,
        artist = artist,
        album = album,
        albumArtist = albumArtist,
        durationMs = durationMs,
        artworkUrl = artworkUrl,
        isrc = normIsrc ?: isrc,
        isrcEvidence = isrcEv,
        playbackCapability = playbackCap,
        downloadCapability = downloadCap,
        downloadCapabilityBasis = downloadBasis,
        resolutionMode = ResolutionMode.DIRECT_RESOURCE,
        audioFormat = audioFormat,
        availableDownloadFormats = availableDownloadFormats,
        qualityLabel = qualityLabel,
        canonicalUrl = sourceUrl.takeIf { it.isNotBlank() },
        license = license,
        licenseUrl = licenseUrl,
        originalTrack = metadataBridgeTrack,
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
    isrcEvidence = null,
    playbackCapability = PlaybackCapability.LOCAL,
    downloadCapability = DownloadCapability.NOT_PERMITTED,
    downloadCapabilityBasis = DownloadCapabilityBasis.NONE,
    resolutionMode = ResolutionMode.DIRECT_RESOURCE,
    audioFormat = mimeType,
    qualityLabel = "On-Device",
    canonicalUrl = null,
    originalLocalTrack = this,
)
