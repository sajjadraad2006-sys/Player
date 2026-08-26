package dev.auralis.studio.resolver.api

import kotlinx.serialization.Serializable
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
)

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
