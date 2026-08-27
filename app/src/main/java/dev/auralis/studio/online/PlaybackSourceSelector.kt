package dev.auralis.studio.online

/**
 * Pure, deterministic playback source selector and failover candidate ranking.
 *
 * Invariant:
 * 1. Playback ranking is strictly capability-based: LOCAL > FULL_STREAM > PREVIEW.
 * 2. Promotional previews (30-sec clips) must NEVER beat a verified full-length stream.
 * 3. Local sources participate only when readable.
 */
object PlaybackSourceSelector {

    /**
     * Returns an ordered list of viable playback candidates for failover execution.
     */
    fun rank(
        sources: List<RecordingSource>,
        isLocalUriReadable: (String) -> Boolean = { true },
    ): List<RecordingSource> {
        val valid = sources.filter { source ->
            when (source.playbackCapability) {
                PlaybackCapability.LOCAL -> {
                    val local = source as? LocalRecordingSource
                    local != null && isLocalUriReadable(local.contentUri)
                }
                PlaybackCapability.FULL_STREAM, PlaybackCapability.PREVIEW -> true
                PlaybackCapability.AUTH_REQUIRED, PlaybackCapability.UNAVAILABLE -> false
            }
        }

        return valid.sortedWith(
            compareByDescending<RecordingSource> { capabilityPriority(it.playbackCapability) }
                .thenByDescending { if (it.durationMs > 60_000L) 100 else 0 } // Prefer full tracks over short/preview
                .thenByDescending { metadataScore(it) }
                .thenBy { it.provider } // Deterministic provider tie-breaker
        )
    }

    /**
     * Selects the single best candidate for immediate playback.
     */
    fun select(
        sources: List<RecordingSource>,
        isLocalUriReadable: (String) -> Boolean = { true },
    ): RecordingSource? = rank(sources, isLocalUriReadable).firstOrNull()

    private fun capabilityPriority(capability: PlaybackCapability): Int = when (capability) {
        PlaybackCapability.LOCAL -> 300
        PlaybackCapability.FULL_STREAM -> 200
        PlaybackCapability.PREVIEW -> 100
        PlaybackCapability.AUTH_REQUIRED -> 0
        PlaybackCapability.UNAVAILABLE -> -1
    }

    private fun metadataScore(source: RecordingSource): Int =
        (if (!source.isrc.isNullOrBlank()) 40 else 0) +
            (if (source.durationMs > 0) 20 else 0) +
            (if (source.album.isNotBlank()) 10 else 0)
}
