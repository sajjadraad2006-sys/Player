package dev.auralis.studio.resolver.api

import kotlinx.serialization.Serializable
import java.security.MessageDigest
import java.util.UUID

/**
 * Stable resolver request containing canonical source identity.
 *
 * Signed temporary media URLs must NEVER be persisted as canonical source identity.
 */
@Serializable
data class ResolverRequest(
    val requestId: String = UUID.randomUUID().toString(),
    val sourceUrl: String? = null,
    val sourceId: String? = null,
    val sourceType: ResolverSourceType = ResolverSourceType.GENERIC_URL,
    val preferredAudioQuality: AudioQualityPreference = AudioQualityPreference.BEST,
    val extraOptions: Map<String, String> = emptyMap(),
)

@Serializable
enum class ResolverSourceType {
    GENERIC_URL,
    MUSIC_TRACK,
    AUDIO_STREAM,
}

@Serializable
enum class AudioQualityPreference {
    BEST,
    HIGH,
    MEDIUM,
    LOW,
}

/**
 * Legal / policy permission model for downloading a resolved format.
 * Distinct from [ResolutionMode] which describes technical delivery mechanism.
 */
@Serializable
enum class DownloadCapability {
    /** Explicitly permitted for local download / offline storage. */
    PERMITTED,
    /** Technical playback stream available, but offline downloading is not permitted. */
    STREAM_ONLY,
    /** Download permission cannot be proven from trusted provenance/contract. */
    UNKNOWN,
    /** Requires private account authentication or gated access. */
    REQUIRES_AUTHORIZATION,
    /** Explicitly forbidden by copyright/terms or provider restriction. */
    NOT_PERMITTED,
}

/**
 * Provenance / basis underpinning a [DownloadCapability] decision.
 */
@Serializable
enum class DownloadCapabilityBasis {
    /** Explicit authorized provider contract (e.g. Audius ungated full track, Jamendo authorized download). */
    PROVIDER_EXPLICIT,
    /** Explicit open machine-readable license metadata (e.g. CC-BY, Public Domain). */
    LICENSE_METADATA,
    /** Local testing harness / synthetic test fixture with explicit test authorization. */
    TEST_FIXTURE,
    /** No trusted download authorization basis found. */
    NONE,
}

/**
 * Normalized result representing resolved media metadata and available formats.
 *
 * Does not force nonexistent metadata (e.g. no fake artist names).
 */
@Serializable
data class ResolvedMedia(
    val source: String,
    val sourceId: String? = null,
    val webpageUrl: String? = null,
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val durationMs: Long? = null,
    val artworkUrl: String? = null,
    val formats: List<ResolvedFormat> = emptyList(),
) {
    /** Returns true if any format is technically direct and legally permitted for download. */
    val hasPermittedDirectDownload: Boolean
        get() = formats.any {
            it.resolutionMode == ResolutionMode.DIRECT_RESOURCE &&
                it.downloadCapability == DownloadCapability.PERMITTED
        }

    /** All direct formats that are authorized for download. */
    val permittedDirectFormats: List<ResolvedFormat>
        get() = formats.filter {
            it.resolutionMode == ResolutionMode.DIRECT_RESOURCE &&
                it.downloadCapability == DownloadCapability.PERMITTED
        }
}

/**
 * Details of an individual media format available from a resolved resource.
 */
@Serializable
data class ResolvedFormat(
    val formatId: String? = null,
    val url: String,
    val extension: String? = null,
    val mimeType: String? = null,
    val audioCodec: String? = null,
    val videoCodec: String? = null,
    val bitrateKbps: Int? = null,
    val sampleRateHz: Int? = null,
    val fileSizeBytes: Long? = null,
    val protocol: String? = null,
    val httpHeaders: Map<String, String> = emptyMap(),
    val expiresAtEpochMs: Long? = null,
    val resolutionMode: ResolutionMode = ResolutionMode.DIRECT_RESOURCE,
    val isAudioOnly: Boolean = true,
    val downloadCapability: DownloadCapability = DownloadCapability.UNKNOWN,
    val downloadCapabilityBasis: DownloadCapabilityBasis = DownloadCapabilityBasis.NONE,
)

/**
 * Mode required to process or download a resolved format.
 */
@Serializable
enum class ResolutionMode {
    /** Direct HTTP(S) stream/file that standard OkHttp/WorkManager can download. */
    DIRECT_RESOURCE,
    /** Segmented stream (e.g. HLS/DASH) requiring segment parsing/assembly. */
    SEGMENTED_RESOURCE,
    /** Complex resource requiring native engine processing or demuxing. */
    ENGINE_REQUIRED,
}

/**
 * Stable download descriptor persisted into WorkManager.
 *
 * MUST NOT persist:
 * - temporary signed CDN URLs
 * - cookies or session tokens
 * - Authorization headers
 * - temporary EJS solver tokens
 */
@Serializable
data class ResolverDownloadDescriptor(
    val requestId: String = UUID.randomUUID().toString(),
    val source: String,
    val sourceId: String? = null,
    val canonicalUrl: String,
    val title: String,
    val artist: String? = null,
    val album: String? = null,
    val artworkUrl: String? = null,
    val expectedDurationMs: Long? = null,
    val qualityPreference: AudioQualityPreference = AudioQualityPreference.BEST,
    val formatHint: String? = null,
    val recordingIdentity: String = deriveRecordingIdentity(artist, title),
    val downloadCapability: DownloadCapability = DownloadCapability.PERMITTED,
    val downloadCapabilityBasis: DownloadCapabilityBasis = DownloadCapabilityBasis.NONE,
) {
    /**
     * Stable unique download key.
     * Uses sourceId when present; falls back to SHA-256 of canonical URL.
     * Never uses String.hashCode() or transient query tokens.
     */
    val downloadKey: String
        get() {
            val cleanSource = source.lowercase().trim().replace(Regex("[^a-z0-9_]"), "_")
            val id = if (!sourceId.isNullOrBlank()) {
                sourceId.trim()
            } else {
                sha256Hex(canonicalUrl.trim())
            }
            return "lrr:$cleanSource:$id"
        }

    companion object {
        fun deriveRecordingIdentity(artist: String?, title: String): String {
            val cleanArtist = (artist ?: "").trim().lowercase()
            val cleanTitle = title.trim().lowercase()
            return if (cleanArtist.isNotBlank()) "$cleanArtist - $cleanTitle" else cleanTitle
        }

        fun sha256Hex(input: String): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
            return digest.joinToString("") { "%02x".format(it) }
        }
    }
}
