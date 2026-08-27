package dev.auralis.studio.online

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import java.util.LinkedHashMap
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

data class ProviderFailure(val providerId: String, val reason: FailureReason)

enum class FailureReason { NOT_CONFIGURED, NETWORK, TIMEOUT, RATE_LIMIT, UNAUTHORIZED, UNAVAILABLE, PROVIDER_ERROR }

typealias UnifiedSearchResult = SearchSnapshot

sealed interface DiscoveryEvent {
    data class Sources(val providerId: String, val sources: List<RecordingSource>, val fromCache: Boolean = false) : DiscoveryEvent
    data class Failure(val providerId: String, val failure: ProviderFailure) : DiscoveryEvent
    data class Completed(val providerId: String) : DiscoveryEvent
}

class CanonicalSearchReducer(
    private val totalWorkers: Int,
    private val query: String,
    initialFailures: List<ProviderFailure> = emptyList(),
) {
    private val groups = mutableListOf<UnifiedTrackMerger.GroupBuilder>()
    private val failureMap = ConcurrentHashMap<String, ProviderFailure>()
    private val finishedProviders = mutableSetOf<String>()

    init {
        initialFailures.forEach { failureMap[it.providerId] = it }
    }

    fun reduce(event: DiscoveryEvent): SearchSnapshot {
        when (event) {
            is DiscoveryEvent.Sources -> {
                finishedProviders.add(event.providerId)
                if (event.sources.isNotEmpty()) {
                    for (candidate in event.sources) {
                        val matching = groups.firstOrNull { it.canAdmit(candidate) }
                        if (matching != null) {
                            matching.addSource(candidate)
                        } else {
                            val newGroup = UnifiedTrackMerger.GroupBuilder()
                            newGroup.addSource(candidate)
                            groups.add(newGroup)
                        }
                    }
                }
            }
            is DiscoveryEvent.Failure -> {
                failureMap[event.providerId] = event.failure
                finishedProviders.add(event.providerId)
            }
            is DiscoveryEvent.Completed -> {
                finishedProviders.add(event.providerId)
            }
        }

        val remaining = maxOf(0, totalWorkers - finishedProviders.size)
        val canonicalRecordings = groups.map { it.build() }
        val rankedRecordings = CanonicalSearchRanker.rank(canonicalRecordings, query)

        return SearchSnapshot(
            recordings = rankedRecordings,
            failures = failureMap.values.toList(),
            isComplete = remaining <= 0,
            providersPending = remaining,
            fromCache = (event as? DiscoveryEvent.Sources)?.fromCache ?: false,
        )
    }
}

class UnifiedSearchAggregator(
    private val providers: List<MusicCatalogProvider>,
    private val localDiscovery: LocalLibraryDiscovery? = null,
    private val timeoutMs: Long = 12_000L,
    private val cacheTtlMs: Long = 90_000L,
    private val clockMs: () -> Long = System::currentTimeMillis,
    private val debugLogging: Boolean = false,
) {
    private data class ProviderCacheKey(val providerId: String, val query: String, val page: Int)
    private data class CachedSources(val createdAt: Long, val sources: List<RecordingSource>)

    private val providerCache = object : LinkedHashMap<ProviderCacheKey, CachedSources>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<ProviderCacheKey, CachedSources>?): Boolean = size > 60
    }

    private fun getProviderCache(providerId: String, query: String, page: Int): List<RecordingSource>? =
        synchronized(providerCache) {
            val key = ProviderCacheKey(providerId, SearchNormalization.normalize(query), page)
            providerCache[key]?.takeIf { clockMs() - it.createdAt <= cacheTtlMs }?.sources
        }

    private fun putProviderCache(providerId: String, query: String, page: Int, sources: List<RecordingSource>) {
        val sanitized = sources.map { source ->
            if (source is OnlineRecordingSource && source.originalTrack != null) {
                val cleanTrack = source.originalTrack.copy(
                    previewUrl = null,
                    streamUrl = null,
                    downloadUrl = null,
                )
                source.copy(originalTrack = cleanTrack)
            } else {
                source
            }
        }
        val key = ProviderCacheKey(providerId, SearchNormalization.normalize(query), page)
        synchronized(providerCache) {
            providerCache[key] = CachedSources(clockMs(), sanitized)
        }
    }

    /**
     * Progressive search Flow using a single-writer reducer.
     * Emits incremental snapshots as individual providers complete, preserving stable group identity.
     *
     * Invariants:
     * 1. Cancellation propagates immediately (CancellationException is NEVER swallowed).
     * 2. Provider failure or timeout in one provider does not cancel or corrupt sibling providers.
     * 3. Old queries are structurally cancelled at ViewModel collection level.
     * 4. Empty or zero-provider searches always emit a valid complete snapshot.
     * 5. Local results arrive first and emit immediately without waiting for remote networks.
     */
    fun search(query: String, page: Int = 0): Flow<SearchSnapshot> = channelFlow {
        val trimmed = query.trim()
        if (trimmed.isBlank()) {
            send(SearchSnapshot.EMPTY)
            return@channelFlow
        }

        val configuredProviders = providers.filter(MusicCatalogProvider::isConfigured)
        val initialFailures = providers.filterNot(MusicCatalogProvider::isConfigured)
            .map { ProviderFailure(it.id, FailureReason.NOT_CONFIGURED) }

        val hasLocal = localDiscovery != null && page == 0
        val totalWorkers = configuredProviders.size + (if (hasLocal) 1 else 0)

        val reducer = CanonicalSearchReducer(totalWorkers, trimmed, initialFailures)

        if (totalWorkers == 0) {
            send(reducer.reduce(DiscoveryEvent.Completed("none")))
            return@channelFlow
        }

        val eventChannel = kotlinx.coroutines.channels.Channel<DiscoveryEvent>(kotlinx.coroutines.channels.Channel.UNLIMITED)

        // 1. Launch provider discovery in background workers (they only emit events to eventChannel)
        launch {
            // Local discovery first (instant on-device)
            if (hasLocal) {
                try {
                    val localSources = localDiscovery!!.search(trimmed)
                    eventChannel.send(DiscoveryEvent.Sources("local", localSources))
                } catch (c: CancellationException) {
                    throw c
                } catch (e: Exception) {
                    if (debugLogging) Log.w("UnifiedSearch", "Local discovery failed: ${e.message}")
                    eventChannel.send(DiscoveryEvent.Failure("local", ProviderFailure("local", FailureReason.PROVIDER_ERROR)))
                }
            }

            // Remote providers in parallel
            val providerJobs = configuredProviders.map { provider ->
                launch {
                    try {
                        val cached = getProviderCache(provider.id, trimmed, page)
                        if (cached != null) {
                            eventChannel.send(DiscoveryEvent.Sources(provider.id, cached, fromCache = true))
                            return@launch
                        }

                        val outcome = withTimeout(timeoutMs) {
                            provider.search(trimmed, page)
                        }
                        when (outcome) {
                            is ProviderResult.Success -> {
                                val sources = outcome.data.map(OnlineTrack::toRecordingSource)
                                putProviderCache(provider.id, trimmed, page, sources)
                                eventChannel.send(DiscoveryEvent.Sources(provider.id, sources))
                            }
                            ProviderResult.NetworkUnavailable -> eventChannel.send(DiscoveryEvent.Failure(provider.id, ProviderFailure(provider.id, FailureReason.NETWORK)))
                            ProviderResult.RateLimited -> eventChannel.send(DiscoveryEvent.Failure(provider.id, ProviderFailure(provider.id, FailureReason.RATE_LIMIT)))
                            ProviderResult.NotConfigured -> eventChannel.send(DiscoveryEvent.Failure(provider.id, ProviderFailure(provider.id, FailureReason.NOT_CONFIGURED)))
                            ProviderResult.Unauthorized -> eventChannel.send(DiscoveryEvent.Failure(provider.id, ProviderFailure(provider.id, FailureReason.UNAUTHORIZED)))
                            ProviderResult.Unavailable -> eventChannel.send(DiscoveryEvent.Failure(provider.id, ProviderFailure(provider.id, FailureReason.UNAVAILABLE)))
                            is ProviderResult.Error -> eventChannel.send(DiscoveryEvent.Failure(provider.id, ProviderFailure(provider.id, if (outcome.code == -408) FailureReason.TIMEOUT else FailureReason.PROVIDER_ERROR)))
                        }
                    } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
                        eventChannel.send(DiscoveryEvent.Failure(provider.id, ProviderFailure(provider.id, FailureReason.TIMEOUT)))
                    } catch (c: CancellationException) {
                        throw c
                    } catch (_: Exception) {
                        eventChannel.send(DiscoveryEvent.Failure(provider.id, ProviderFailure(provider.id, FailureReason.PROVIDER_ERROR)))
                    }
                }
            }

            providerJobs.forEach { it.join() }
            eventChannel.close()
        }

        // 2. Single-writer reducer consumes events sequentially and emits updated snapshots
        for (event in eventChannel) {
            val snapshot = reducer.reduce(event)
            send(snapshot)
        }
    }

    /**
     * Backward-compatible one-shot search derived from the progressive Flow.
     */
    suspend fun searchOnce(query: String, page: Int = 0): SearchSnapshot =
        search(query, page).last()

    fun invalidate() = synchronized(providerCache) { providerCache.clear() }
}

class MusicProviderRegistry(
    val catalogProviders: List<MusicCatalogProvider>,
    streamProviders: List<MusicStreamProvider>,
    downloadProviders: List<MusicDownloadProvider>,
) {
    private val streams = streamProviders.associateBy(MusicStreamProvider::id)
    private val downloads = downloadProviders.associateBy(MusicDownloadProvider::id)

    suspend fun resolveStream(track: OnlineTrack): ProviderResult<StreamResource> =
        streams[track.provider]?.resolveStream(track)
            ?: track.bestStreamUrl?.let { ProviderResult.Success(StreamResource(it, track.bestStreamKind ?: MediaUrlKind.PREVIEW)) }
            ?: ProviderResult.Unavailable

    suspend fun resolveDownload(
        track: OnlineTrack,
        quality: DownloadQuality,
    ): ProviderResult<DownloadResource> = downloads[track.provider]?.resolveDownload(track, quality)
        ?: ProviderResult.Unavailable

    /** No bearer token or API secret is ever attached to a client-side media request. */
    fun downloadHeaders(@Suppress("UNUSED_PARAMETER") track: OnlineTrack): Map<String, String> = emptyMap()
}
