package dev.auralis.studio.resolver.api

/**
 * Deterministic audio-only format selection policy for LRR direct resource ingestion.
 *
 * Selection Rules:
 * 1. Must be [ResolutionMode.DIRECT_RESOURCE]
 * 2. Must be [DownloadCapability.PERMITTED]
 * 3. Prefers [ResolvedFormat.isAudioOnly] == true
 * 4. Preserves genuine source container/codec (no fake transcoding to MP3)
 * 5. Matches [AudioQualityPreference] with stable fallback if a format ID changes
 */
object ResolverFormatSelector {

    private val PREFERRED_CONTAINERS = listOf("m4a", "aac", "flac", "opus", "mp3", "ogg", "wav")

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

        // If a specific formatHint was requested and is present among eligible formats, use it.
        if (!formatHint.isNullOrBlank()) {
            val hinted = eligible.firstOrNull { it.formatId == formatHint }
            if (hinted != null) return hinted
        }

        // Sort candidates by preference
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
        )

        return sorted.firstOrNull()
    }
}
