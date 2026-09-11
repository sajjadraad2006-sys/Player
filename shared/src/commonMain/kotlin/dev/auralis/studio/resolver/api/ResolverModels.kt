package dev.auralis.studio.resolver.api

import kotlinx.serialization.Serializable
import java.security.MessageDigest
import java.util.UUID

/**
 * Explicit intent for a resolver request.
 */
@Serializable
enum class ResolveIntent {
    INSPECT,
    PLAYBACK,
    DOWNLOAD,
}

/**
 * Stable resolver request containing canonical source identity and intent.
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
) {
    fun validate(): ResolverError? {
        val cleanUrl = sourceUrl?.trim().orEmpty()
        val cleanId = sourceId?.trim().orEmpty()

        if (cleanUrl.isBlank() && cleanId.isBlank()) {
            return ResolverError.unsupported(
                source = cleanUrl,
                details = "Both sourceUrl and sourceId cannot be blank.",
            )
        }

        when (sourceType) {
            ResolverSourceType.GENERIC_URL, ResolverSourceType.AUDIO_STREAM -> {
                if (cleanUrl.isBlank()) {
                    return ResolverError.unsupported(
                        source = cleanUrl,
                        details = "sourceUrl is required for sourceType $sourceType.",
                    )
                }
            }
            ResolverSourceType.MUSIC_TRACK -> {
                if (cleanUrl.isBlank() && cleanId.isBlank()) {
                    return ResolverError.unsupported(
                        source = cleanUrl,
                        details = "sourceId or sourceUrl is required for MUSIC_TRACK.",
                    )
                }
            }
        }

        if (extraOptions.size > 10) {
            return ResolverError.unknown(
                source = cleanUrl,
                message = "Too many extra options provided (max 10).",
                details = "extraOptions count=${extraOptions.size}",
            )
        }

        for ((k, v) in extraOptions) {
            if (k.length > 64 || v.length > 256) {
                return ResolverError.unknown(
                    source = cleanUrl,
                    message = "Extra option key/value exceeds length limit.",
                    details = "Key length=${k.length}, Value length=${v.length}",
                )
            }
            if (SecretSanitizer.isForbiddenOption(k)) {
                return ResolverError.unknown(
                    source = cleanUrl,
                    message = "Forbidden configuration option: $k",
                    details = "Option '$k' is not permitted.",
                )
            }
        }

        return null
    }
}

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

@Serializable
enum class AvailabilityState {
    AVAILABLE,
    RESTRICTED,
    UNAVAILABLE,
    UNKNOWN,
}

@Serializable
enum class DrmState {
    NONE,
    PRESENT,
    POSSIBLE,
    UNKNOWN,
}

@Serializable
enum class DeliveryBlockReason {
    NONE,
    DRM_PROTECTED,
    DRM_POSSIBLE,
    SENSITIVE_AUTH_REQUIRED,
    UNSUPPORTED_POST_DATA,
    UNSUPPORTED_BROWSER_TARGET,
    SEGMENTED_STREAM,
    PERMISSION_DENIED,
}

@Serializable
enum class MediaKind {
    AUDIO_ONLY,
    VIDEO_ONLY,
    MUXED_AUDIO_VIDEO,
    UNKNOWN,
}

@Serializable
data class DeliveryRequirements(
    val requiresCustomHeaders: Boolean = false,
    val requiredSafeHeaderKeys: List<String> = emptyList(),
    val requiresBrowserImpersonation: Boolean = false,
    val requiresPostRequestData: Boolean = false,
    val isSegmented: Boolean = false,
    val hasDrm: Boolean = false,
)

@Serializable
enum class ResolutionMode {
    DIRECT_RESOURCE,
    SEGMENTED_RESOURCE,
    ENGINE_REQUIRED,
}

@Serializable
enum class DownloadCapability {
    PERMITTED,
    STREAM_ONLY,
    UNKNOWN,
    REQUIRES_AUTHORIZATION,
    NOT_PERMITTED,
}

@Serializable
enum class DownloadCapabilityBasis {
    PROVIDER_EXPLICIT,
    LICENSE_METADATA,
    TEST_FIXTURE,
    NONE,
}

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
    val blockReason: DeliveryBlockReason = DeliveryBlockReason.NONE,
    val deliveryRequirements: DeliveryRequirements = DeliveryRequirements(),
    val downloadCapability: DownloadCapability = DownloadCapability.UNKNOWN,
    val downloadCapabilityBasis: DownloadCapabilityBasis = DownloadCapabilityBasis.NONE,
)

@Serializable
data class EphemeralResourceLease(
    val requestId: String,
    val formatId: String,
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val resolvedAtEpochMs: Long = System.currentTimeMillis(),
    val knownExpiryEpochMs: Long? = null,
    val resolutionMode: ResolutionMode = ResolutionMode.DIRECT_RESOURCE,
    val mediaKind: MediaKind = MediaKind.AUDIO_ONLY,
) {
    val isExpired: Boolean
        get() = knownExpiryEpochMs != null && System.currentTimeMillis() >= knownExpiryEpochMs
}

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
    val hasPermittedDirectDownload: Boolean
        get() = formats.any {
            it.resolutionMode == ResolutionMode.DIRECT_RESOURCE &&
                it.downloadCapability == DownloadCapability.PERMITTED &&
                it.drmState == DrmState.NONE &&
                it.blockReason == DeliveryBlockReason.NONE
        }

    val permittedDirectFormats: List<ResolvedFormatDescriptor>
        get() = formats.filter {
            it.resolutionMode == ResolutionMode.DIRECT_RESOURCE &&
                it.downloadCapability == DownloadCapability.PERMITTED &&
                it.drmState == DrmState.NONE &&
                it.blockReason == DeliveryBlockReason.NONE
        }
}

@Serializable
data class ResolverDownloadDescriptor(
    val schemaVersion: Int = 1,
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
    val downloadCapability: DownloadCapability = DownloadCapability.UNKNOWN,
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
    )

    private val FORBIDDEN_EXTRA_OPTION_PATTERNS = listOf(
        "exec", "command", "path", "file", "proxy", "tls", "ssl", "cert",
        "cookie", "auth", "token", "password", "template", "outtmpl",
        "postprocessor", "downloader", "external", "runtime", "script",
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

    fun isForbiddenOption(key: String): Boolean {
        val lower = key.trim().lowercase()
        return FORBIDDEN_EXTRA_OPTION_PATTERNS.any { lower.contains(it) }
    }

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
        try {
            val uri = java.net.URI(url.trim())
            val cleanHost = uri.host ?: ""
            val cleanPath = uri.path ?: ""
            val scheme = uri.scheme ?: "https"
            val queryRedacted = if (!uri.query.isNullOrBlank()) "?<redacted>" else ""
            return "$scheme://$cleanHost$cleanPath$queryRedacted"
        } catch (_: Exception) {
            val queryIndex = url.indexOf('?')
            val base = if (queryIndex != -1) url.substring(0, queryIndex) else url
            val cleanBase = base.replace(Regex("""https?://[^/@]+@"""), "https://")
            return if (queryIndex != -1) "$cleanBase?<redacted>" else cleanBase
        }
    }

    fun sanitizeLog(message: String): String {
        var clean = message
        clean = clean.replace(Regex("""https?://[^\s"'<>]+(?:\?[^\s"'<>]+)""")) { match ->
            sanitizeUrl(match.value)
        }
        clean = clean.replace(Regex("""(?i)(bearer|token|key|cookie|password)[:=\s]+[a-zA-Z0-9_\-\.]+""")) { match ->
            "${match.groupValues[1]}: <redacted>"
        }
        return clean
    }
}

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

    data class ClassificationResult(
        val resolutionMode: ResolutionMode,
        val blockReason: DeliveryBlockReason,
    )

    fun classify(
        protocol: String?,
        manifestUrl: String?,
        hasFragments: Boolean,
        hasRequestData: Boolean,
        hasDrm: Boolean,
        impersonate: String?,
        headers: Map<String, String> = emptyMap(),
    ): ResolutionMode = classifyWithReason(
        protocol, manifestUrl, hasFragments, hasRequestData, hasDrm, impersonate, headers,
    ).resolutionMode

    fun classifyWithReason(
        protocol: String?,
        manifestUrl: String?,
        hasFragments: Boolean,
        hasRequestData: Boolean,
        hasDrm: Boolean,
        impersonate: String?,
        headers: Map<String, String> = emptyMap(),
    ): ClassificationResult {
        val proto = protocol?.lowercase()?.trim().orEmpty()
        val isSegmented = proto in SEGMENTED_PROTOCOLS ||
            !manifestUrl.isNullOrBlank() ||
            hasFragments ||
            proto.contains("m3u8") ||
            proto.contains("dash")

        return when {
            hasDrm -> ClassificationResult(ResolutionMode.ENGINE_REQUIRED, DeliveryBlockReason.DRM_PROTECTED)
            headers.keys.any { SecretSanitizer.isSensitiveHeader(it) } ->
                ClassificationResult(ResolutionMode.ENGINE_REQUIRED, DeliveryBlockReason.SENSITIVE_AUTH_REQUIRED)
            hasRequestData -> ClassificationResult(ResolutionMode.ENGINE_REQUIRED, DeliveryBlockReason.UNSUPPORTED_POST_DATA)
            !impersonate.isNullOrBlank() -> ClassificationResult(ResolutionMode.ENGINE_REQUIRED, DeliveryBlockReason.UNSUPPORTED_BROWSER_TARGET)
            isSegmented -> ClassificationResult(ResolutionMode.SEGMENTED_RESOURCE, DeliveryBlockReason.SEGMENTED_STREAM)
            proto.startsWith("http") || proto.isBlank() -> ClassificationResult(ResolutionMode.DIRECT_RESOURCE, DeliveryBlockReason.NONE)
            else -> ClassificationResult(ResolutionMode.ENGINE_REQUIRED, DeliveryBlockReason.NONE)
        }
    }
}
