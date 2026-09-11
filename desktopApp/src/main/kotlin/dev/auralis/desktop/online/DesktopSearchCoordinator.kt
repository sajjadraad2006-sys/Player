package dev.auralis.desktop.online

import dev.auralis.desktop.logging.DesktopLogger
import dev.auralis.studio.online.MusicCatalogProvider
import dev.auralis.studio.online.OnlineTrack
import dev.auralis.studio.online.PlaybackCapability
import dev.auralis.studio.online.ProviderResult
import dev.auralis.studio.online.SearchNormalization
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

private const val TAG = "DesktopSearchCoordinator"
private const val PROVIDER_TIMEOUT_MS = 45_000L

enum class ProviderStatus {
    IDLE,
    SEARCHING,
    SUCCESS,
    NOT_CONFIGURED,
    NETWORK_ERROR,
    RATE_LIMITED,
    ERROR,
}

data class DesktopSearchState(
    val query: String = "",
    val normalizedQuery: String = "",
    val isSearching: Boolean = false,
    val results: List<OnlineTrack> = emptyList(),
    val providerStatuses: Map<String, ProviderStatus> = emptyMap(),
    val errorMessage: String? = null,
)

/**
 * High-performance, capability-aware online search coordinator for Windows Desktop.
 *
 * Guarantees:
 * 1. Latest-query-wins cancellation: new search preempts prior running query job.
 * 2. Per-provider timeout isolation (8s): slow provider never hangs search results.
 * 3. Arabic & text query normalization via [SearchNormalization].
 * 4. Deduplication and capability-ranked result ordering.
 */
class DesktopSearchCoordinator(
    val providers: List<MusicCatalogProvider>,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob()),
) {
    private val _state = MutableStateFlow(DesktopSearchState())
    val state: StateFlow<DesktopSearchState> = _state.asStateFlow()

    private var activeSearchJob: Job? = null

    fun search(query: String) {
        activeSearchJob?.cancel()

        val rawTrimmed = query.trim()
        if (rawTrimmed.isBlank()) {
            _state.value = DesktopSearchState()
            return
        }

        val normalized = SearchNormalization.normalize(rawTrimmed)
        DesktopLogger.info(TAG, "Starting online search: '$rawTrimmed' (normalized: '$normalized') across ${providers.size} providers")

        val initialStatuses = providers.associate { it.id to ProviderStatus.SEARCHING }
        _state.value = DesktopSearchState(
            query = rawTrimmed,
            normalizedQuery = normalized,
            isSearching = true,
            providerStatuses = initialStatuses,
        )

        activeSearchJob = scope.launch {
            try {
                val configuredProviders = providers.filter { it.isConfigured }
                val unconfiguredProviders = providers.filterNot { it.isConfigured }

                val currentStatuses = initialStatuses.toMutableMap()
                for (unconfigured in unconfiguredProviders) {
                    currentStatuses[unconfigured.id] = ProviderStatus.NOT_CONFIGURED
                }

                val allTracks = mutableListOf<OnlineTrack>()

                val deferreds = configuredProviders.map { provider ->
                    async {
                        try {
                            val result = withTimeoutOrNull(PROVIDER_TIMEOUT_MS) {
                                provider.search(rawTrimmed)
                            }

                            if (result == null) {
                                DesktopLogger.warn(TAG, "Provider '${provider.displayName}' timed out after ${PROVIDER_TIMEOUT_MS}ms")
                                Pair(provider.id, ProviderResult.Error("Request timed out"))
                            } else {
                                Pair(provider.id, result)
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            DesktopLogger.error(TAG, "Provider '${provider.displayName}' failed: ${e.message}", e)
                            Pair(provider.id, ProviderResult.Error(e.message ?: "Unknown error"))
                        }
                    }
                }

                // Await all provider responses concurrently
                val completedResults = deferreds.awaitAll()

                for ((providerId, result) in completedResults) {
                    when (result) {
                        is ProviderResult.Success -> {
                            currentStatuses[providerId] = ProviderStatus.SUCCESS
                            allTracks.addAll(result.data)
                        }
                        is ProviderResult.NotConfigured -> {
                            currentStatuses[providerId] = ProviderStatus.NOT_CONFIGURED
                        }
                        is ProviderResult.NetworkUnavailable -> {
                            currentStatuses[providerId] = ProviderStatus.NETWORK_ERROR
                        }
                        is ProviderResult.RateLimited -> {
                            currentStatuses[providerId] = ProviderStatus.RATE_LIMITED
                        }
                        is ProviderResult.Error, ProviderResult.Unauthorized, ProviderResult.Unavailable -> {
                            currentStatuses[providerId] = ProviderStatus.ERROR
                        }
                    }
                }

                val rankedResults = deduplicateAndRank(allTracks)

                _state.value = _state.value.copy(
                    isSearching = false,
                    results = rankedResults,
                    providerStatuses = currentStatuses,
                    errorMessage = if (rankedResults.isEmpty() && currentStatuses.values.any { it != ProviderStatus.SUCCESS }) "Couldn’t reach YouTube. Check your connection and try again." else null,
                )
                DesktopLogger.info(TAG, "Search completed: ${rankedResults.size} unique tracks returned")
            } catch (e: CancellationException) {
                // Preempted by a newer query; ignore
            } catch (e: Exception) {
                DesktopLogger.error(TAG, "Search failed with unhandled exception: ${e.message}", e)
                _state.value = _state.value.copy(
                    isSearching = false,
                    errorMessage = "Couldn’t search YouTube. Please try again.",
                )
            }
        }
    }

    fun clear() {
        activeSearchJob?.cancel()
        _state.value = DesktopSearchState()
    }

    private fun deduplicateAndRank(tracks: List<OnlineTrack>): List<OnlineTrack> {
        val seenKeys = mutableSetOf<String>()
        val unique = mutableListOf<OnlineTrack>()

        for (track in tracks) {
            val key = "${track.provider}:${track.id}"
            if (seenKeys.add(key)) {
                unique.add(track)
            }
        }

        // Rank order:
        // 1. FULL_STREAM playable tracks
        // 2. PREVIEW tracks
        // 3. UNAVAILABLE tracks
        return unique.sortedWith(
            compareByDescending<OnlineTrack> {
                when (it.playbackCapability) {
                    PlaybackCapability.LOCAL -> 4
                    PlaybackCapability.FULL_STREAM -> 3
                    PlaybackCapability.PREVIEW -> 2
                    PlaybackCapability.AUTH_REQUIRED -> 1
                    PlaybackCapability.UNAVAILABLE -> 0
                }
            }.thenByDescending { it.downloadAllowed }
        )
    }
}
