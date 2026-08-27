package dev.auralis.studio.online

/**
 * Immutable snapshot of progressive search discovery results.
 */
data class SearchSnapshot(
    val recordings: List<CanonicalRecording>,
    val failures: List<ProviderFailure> = emptyList(),
    val isComplete: Boolean = false,
    val providersPending: Int = 0,
    val fromCache: Boolean = false,
) {
    /** Backward compatibility accessor */
    val tracks: List<CanonicalRecording> get() = recordings

    companion object {
        val EMPTY = SearchSnapshot(
            recordings = emptyList(),
            failures = emptyList(),
            isComplete = true,
            providersPending = 0,
            fromCache = false,
        )
    }
}
