package dev.auralis.studio.resolver.api

import kotlinx.serialization.Serializable
import java.security.MessageDigest
import java.util.UUID

/**
 * Explicit intent for a resolver request.
 *
 * - [INSPECT]: Inspect metadata and available format descriptors without generating delivery leases.
 * - [PLAYBACK]: Resolve a fresh, immediately usable playback resource lease.
 * - [DOWNLOAD]: Resolve a fresh, immediately usable download resource lease.
 */
@Serializable
enum class ResolveIntent {
    INSPECT,
    PLAYBACK,
    DOWNLOAD,
}

/**
 * Stable resolver request containing canonical source identity and intent.
 *
 * Signed temporary media URLs must NEVER be persisted as canonical source identity.
 */
@Serializable
data class ResolverRequest(
    val requestId: String = UUID.randomUUID().toString(),
    val sourceUrl: String? = null,
    val sourceId: String? = null,
    val sourceType: ResolverSourceType = ResolverSourceType.GENERIC_URL,
    val intent: ResolveIntent = ResolveIntent.INSPECT,
    val preferredAudioQuality: AudioQualityPreference = AudioQualityPreference.BEST,
    val formatHint: String? = null,
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
 * High-level availability state of a resolved media source.
 */
@Serializable
enum class AvailabilityState {
    AVAILABLE,
    RESTRICTED,
    UNAVAILABLE,
    UNKNOWN,
}

/**
 * Digital Rights Management (DRM) state.
 *
 * Conservative rule: [PRESENT] and [POSSIBLE] cannot be direct download candidates.
 */
@Serializable
enum class DrmState {
    NONE,
    PRESENT,
    POSSIBLE,
    UNKNOWN,
}

/**
 * Media stream classification.
 */
@Serializable
enum class MediaKind {
    AUDIO_ONLY,
    VIDEO_ONLY,
    MUXED_AUDIO_VIDEO,
    UNKNOWN,
}

/**
 * Technical delivery requirements and constraints for a format.
 */
@Serializable
data class DeliveryRequirements(
    val requiresCustomHeaders: Boolean = false,
    val requiredSafeHeaderKeys: List<String> = emptyList(),
    val requiresBrowserImpersonation: Boolean = false,
    val requiresPostRequestData: Boolean = false,
    val isSegmented: Boolean = false,
    val hasDrm: Boolean = false,
)

/**
 * Mode required to process or deliver a resolved format.
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
 * Stable format descriptor representing a media representation.
 *
 * CRITICAL INVARIANT: This descriptor MUST NOT contain:
 * - signed delivery URLs
 * - temporary stream/download URLs
 * - cookies or session tokens
 * - Authorization headers
 * - temporary EJS solver tokens
 */
@Serializable
data class ResolvedFormatDescriptor(
    val formatId: String,
    val mediaKind: MediaKind = MediaKind.AUDIO_ONLY,
    val container: String? = null,
    val extension: String? = null,
    val audioCodec: String? = null,
    val videoCodec: String? = null,
    val bitrateKbps: Double? = null,
    val sampleRateHz: Int? = null,
    val channels: Int? = null,
    val fileSizeBytes: Long? = null,
    val approximateFileSizeBytes: Long? = null,
    val protocol: String? = null,
    val resolutionMode: ResolutionMode = ResolutionMode.DIRECT_RESOURCE,
    val drmState: DrmState = DrmState.NONE,
    val deliveryRequirements: DeliveryRequirements = DeliveryRequirements(),
    val downloadCapability: DownloadCapability = DownloadCapability.UNKNOWN,
    val downloadCapabilityBasis: DownloadCapabilityBasis = DownloadCapabilityBasis.NONE,
)

/**
 * Short-lived, in-memory lease for immediate media playback or download execution.
 *
 * CRITICAL INVARIANT:
 * - Strictly in-memory; NEVER persisted to Room, DataStore, or persistent storage.
 * - NEVER persisted across process death.
 * - Fresh re-resolution is required whenever a lease expires.
 */
@Serializable
data class EphemeralResourceLease(
    val requestId: String,
    val formatId: String,
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val resolvedAtElapsedRealtimeMs: Long = System.currentTimeMillis(),
    val knownExpiryEpochMs: Long? = null,
    val resolutionMode: ResolutionMode = ResolutionMode.DIRECT_RESOURCE,
    val mediaKind: MediaKind = MediaKind.AUDIO_ONLY,
) {
    val isExpired: Boolean
        get() = knownExpiryEpochMs != null && System.currentTimeMillis() >= knownExpiryEpochMs
}

/**
 * Details of an individual media format available from a resolved resource.
 * Maintained for backward compatibility with direct callers.
 */
@Serializable
data class ResolvedFormat(
    val formatId: String? = null,
    val url: String = "",
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
 * Normalized result representing resolved media metadata and available format descriptors.
 *
 * Does not force nonexistent metadata (nullable means unknown; no fake placeholder strings).
 */
@Serializable
data class ResolvedMedia(
    val schemaVersion: Int = 1,
    val source: String,
    val sourceId: String? = null,
    val webpageUrl: String? = null,
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val durationMs: Long? = null,
    val artworkUrl: String? = null,
    val availability: AvailabilityState = AvailabilityState.AVAILABLE,
    val formats: List<ResolvedFormatDescriptor> = emptyList(),
    val rawFormats: List<ResolvedFormat> = emptyList(),
) {
    /** Returns true if any format is technically direct, non-DRM, and legally permitted for download. */
    val hasPermittedDirectDownload: Boolean
        get() = formats.any {
            it.resolutionMode == ResolutionMode.DIRECT_RESOURCE &&
                it.downloadCapability == DownloadCapability.PERMITTED &&
                it.drmState == DrmState.NONE
        }

    /** All direct formats that are authorized for download. */
    val permittedDirectFormats: List<ResolvedFormatDescriptor>
        get() = formats.filter {
            it.resolutionMode == ResolutionMode.DIRECT_RESOURCE &&
                it.downloadCapability == DownloadCapability.PERMITTED &&
                it.drmState == DrmState.NONE
        }
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

/**
 * Central secret and URL sanitizer for LRR logs, DTOs, and error messages.
 */
object SecretSanitizer {
    private val SENSITIVE_HEADER_KEYS = setOf(
        "cookie",
        "authorization",
        "proxy-authorization",
        "x-api-key",
        "x-auth-token",
        "set-cookie",
    )

    private val SAFE_HEADER_KEYS = setOf(
        "user-agent",
        "referer",
        "origin",
        "accept",
        "accept-language",
        "range",
    )

    fun isSensitiveHeader(name: String): Boolean {
        val lower = name.trim().lowercase()
        return lower in SENSITIVE_HEADER_KEYS ||
            lower.contains("token") ||
            lower.contains("secret") ||
            lower.contains("password")
    }

    fun isSafeHeader(name: String): Boolean =
        name.trim().lowercase() in SAFE_HEADER_KEYS

    fun sanitizeHeaders(headers: Map<String, String>): Map<String, String> {
        return headers.mapValues { (k, v) ->
            if (isSensitiveHeader(k)) "<redacted>" else v
        }
    }

    fun extractSafeHeaders(headers: Map<String, String>): Map<String, String> {
        return headers.filter { (k, _) -> isSafeHeader(k) }
    }

    fun sanitizeUrl(url: String): String {
        if (url.isBlank()) return url
        val queryIndex = url.indexOf('?')
        if (queryIndex == -1) return url
        val base = url.substring(0, queryIndex)
        return "$base?<redacted>"
    }

    fun sanitizeLog(message: String): String {
        var clean = message
        clean = clean.replace(Regex("""https?://[^\s"'<>]+(?:\?[^\s"'<>]+)""")) { match ->
            sanitizeUrl(match.value)
        }
        clean = clean.replace(Regex("""(?i)(bearer|token|key|cookie)[:=\s]+[a-zA-Z0-9_\-\.]+""")) { match ->
            "${match.groupValues[1]}: <redacted>"
        }
        return clean
    }
}

/**
 * Dedicated classifier enforcing the Delivery Classification Matrix.
 *
 * Invariant: url != null does NOT imply DIRECT_RESOURCE.
 */
object FormatDeliveryClassifier {
    private val SEGMENTED_PROTOCOLS = setOf(
        "m3u8",
        "m3u8_native",
        "http_dash_segments",
        "dash",
        "mss",
        "ism",
        "f4m",
    )

    fun classify(
        protocol: String?,
        manifestUrl: String?,
        hasFragments: Boolean,
        hasRequestData: Boolean,
        hasDrm: Boolean,
        impersonate: String?,
        headers: Map<String, String> = emptyMap(),
    ): ResolutionMode {
        val proto = protocol?.lowercase()?.trim().orEmpty()
        val isSegmented = proto in SEGMENTED_PROTOCOLS ||
            !manifestUrl.isNullOrBlank() ||
            hasFragments ||
            proto.contains("m3u8") ||
            proto.contains("dash")

        return when {
            hasDrm -> ResolutionMode.ENGINE_REQUIRED
            hasRequestData -> ResolutionMode.ENGINE_REQUIRED
            !impersonate.isNullOrBlank() -> ResolutionMode.ENGINE_REQUIRED
            headers.keys.any { SecretSanitizer.isSensitiveHeader(it) } -> ResolutionMode.ENGINE_REQUIRED
            isSegmented -> ResolutionMode.SEGMENTED_RESOURCE
            proto.startsWith("http") || proto.isBlank() -> ResolutionMode.DIRECT_RESOURCE
            else -> ResolutionMode.ENGINE_REQUIRED
        }
    }
}
