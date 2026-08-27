package dev.auralis.studio.online

import dev.auralis.studio.resolver.api.DownloadCapability
import dev.auralis.studio.resolver.api.ResolutionMode

/**
 * Pure, deterministic download source selector enforcing Phase 4 download permissions.
 *
 * Strict Invariants:
 * 1. Download eligibility requires [DownloadCapability.PERMITTED] AND [ResolutionMode.DIRECT_RESOURCE].
 * 2. Previews, STREAM_ONLY, UNKNOWN, NOT_PERMITTED, and SEGMENTED_RESOURCE formats are rejected.
 * 3. User quality preference is respected without violating permission gates.
 */
object DownloadSourceSelector {

    /**
     * Selects the single best authorized download candidate matching the user's quality preference.
     */
    fun select(
        sources: List<RecordingSource>,
        quality: DownloadQuality = DownloadQuality.BEST_AVAILABLE,
    ): RecordingSource? {
        val eligible = sources.filter { source ->
            // Local files don't need downloading
            source !is LocalRecordingSource &&
                source.downloadCapability == DownloadCapability.PERMITTED &&
                source.resolutionMode == ResolutionMode.DIRECT_RESOURCE
        }

        return eligible.maxWithOrNull(
            compareBy<RecordingSource> { qualityScore(it, quality) }
                .thenBy { metadataScore(it) }
                .thenBy { providerPriority(it.provider) }
        )
    }

    private fun qualityScore(source: RecordingSource, preference: DownloadQuality): Int {
        val format = source.audioFormat.orEmpty().lowercase()
        val formats = source.availableDownloadFormats.map(String::lowercase)
        val isWavOrOriginal = format == "wav" || "wav" in formats ||
            source.qualityLabel?.contains("original", ignoreCase = true) == true
        val isFlac = format == "flac" || "flac" in formats
        val isLossless = isWavOrOriginal || isFlac

        return when (preference) {
            DownloadQuality.BEST_AVAILABLE -> when {
                isWavOrOriginal -> 320
                isFlac -> 300
                format == "ogg" || format == "mp3" -> 180
                else -> 100
            }
            DownloadQuality.HIGH -> when {
                "mp32" in formats -> 260
                source.qualityLabel?.contains("high", ignoreCase = true) == true -> 240
                isLossless -> 220
                else -> 160
            }
            DownloadQuality.DATA_SAVER -> when {
                "mp31" in formats -> 280
                source.qualityLabel?.contains("96", ignoreCase = true) == true -> 260
                isLossless -> 80 // Deprioritize huge lossless files when user explicitly requests data saver
                else -> 160
            }
        }
    }

    private fun metadataScore(source: RecordingSource): Int =
        (if (!source.isrc.isNullOrBlank()) 40 else 0) +
            (if (source.durationMs > 0) 20 else 0) +
            (if (source.album.isNotBlank()) 10 else 0)

    private fun providerPriority(provider: String): Int = when (provider.lowercase()) {
        "audius" -> 30
        "jamendo" -> 20
        "itunes" -> 10
        "deezer" -> 5
        else -> 0
    }
}
