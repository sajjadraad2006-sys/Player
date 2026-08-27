package dev.auralis.studio.resolver.api

/**
 * Deterministic audio-only format selection policy for LRR direct resource ingestion and playback.
 *
 * Selection Rules:
 * 1. Must be [ResolutionMode.DIRECT_RESOURCE] (or caller-allowed mode)
 * 2. Must be [DownloadCapability.PERMITTED] for download intents
 * 3. Excludes DRM-protected formats ([DrmState.NONE] required)
 * 4. Prefers [MediaKind.AUDIO_ONLY] over muxed video
 * 5. Preserves genuine source container/codec (no fake transcoding)
 * 6. Matches [AudioQualityPreference] with deterministic formatId tie-break
 */
object ResolverFormatSelector {

    private val PREFERRED_CONTAINERS = listOf("m4a", "aac", "flac", "opus", "mp3", "ogg", "wav")

    fun selectBestDescriptor(
        formats: List<ResolvedFormatDescriptor>,
        preference: AudioQualityPreference = AudioQualityPreference.BEST,
        formatHint: String? = null,
        requirePermittedDownload: Boolean = true,
    ): ResolvedFormatDescriptor? {
        val eligible = formats.filter {
            it.resolutionMode == ResolutionMode.DIRECT_RESOURCE &&
                it.drmState == DrmState.NONE &&
                it.blockReason == DeliveryBlockReason.NONE &&
                !it.deliveryRequirements.hasDrm &&
                (!requirePermittedDownload || it.downloadCapability == DownloadCapability.PERMITTED)
        }

        if (eligible.isEmpty()) return null

        if (!formatHint.isNullOrBlank()) {
            val hinted = eligible.firstOrNull { it.formatId == formatHint }
            if (hinted != null) return hinted
        }

        val sorted = eligible.sortedWith(
            compareByDescending<ResolvedFormatDescriptor> { it.mediaKind == MediaKind.AUDIO_ONLY }
                .thenByDescending { format ->
                    val ext = (format.extension ?: format.container ?: "").lowercase()
                    PREFERRED_CONTAINERS.indexOf(ext).let { if (it >= 0) 100 - it else 0 }
                }
                .thenBy { format ->
                    val bitrate = format.bitrateKbps?.toInt() ?: 128
                    when (preference) {
                        AudioQualityPreference.BEST -> -bitrate
                        AudioQualityPreference.HIGH -> kotlin.math.abs(bitrate - 256)
                        AudioQualityPreference.MEDIUM -> kotlin.math.abs(bitrate - 128)
                        AudioQualityPreference.LOW -> bitrate
                    }
                }
                .thenBy { it.formatId } // Deterministic tie-breaker
        )

        return sorted.firstOrNull()
    }

    fun selectBestAudioFormat(
        formats: List<ResolvedFormat>,
        preference: AudioQualityPreference = AudioQualityPreference.BEST,
        formatHint: String? = null,
    ): ResolvedFormat? {
        val eligible = formats.filter {
            it.resolutionMode == ResolutionMode.DIRECT_RESOURCE &&
                it.downloadCapability == DownloadCapability.PERMITTED
        }

        if (eligible.isEmpty()) return null

        if (!formatHint.isNullOrBlank()) {
            val hinted = eligible.firstOrNull { it.formatId == formatHint }
            if (hinted != null) return hinted
        }

        val sorted = eligible.sortedWith(
            compareByDescending<ResolvedFormat> { it.isAudioOnly }
                .thenByDescending { format ->
                    val ext = (format.extension ?: "").lowercase()
                    PREFERRED_CONTAINERS.indexOf(ext).let { if (it >= 0) 100 - it else 0 }
                }
                .thenBy { format ->
                    val bitrate = format.bitrateKbps ?: 128
                    when (preference) {
                        AudioQualityPreference.BEST -> -bitrate
                        AudioQualityPreference.HIGH -> kotlin.math.abs(bitrate - 256)
                        AudioQualityPreference.MEDIUM -> kotlin.math.abs(bitrate - 128)
                        AudioQualityPreference.LOW -> bitrate
                    }
                }
                .thenBy { it.formatId ?: "" }
        )

        return sorted.firstOrNull()
    }
}
