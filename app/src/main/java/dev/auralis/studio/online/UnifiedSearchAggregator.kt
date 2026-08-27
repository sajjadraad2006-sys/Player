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

class UnifiedSearchAggregator(
    private val providers: List<MusicCatalogProvider>,
    private val localDiscovery: LocalLibraryDiscovery? = null,
    private val timeoutMs: Long = 12_000L,
    private val cacheTtlMs: Long = 90_000L,
    private val clockMs: () -> Long = System::currentTimeMillis,
    private val debugLogging: Boolean = false,
) {
    private data class Cached(val createdAt: Long, val result: SearchSnapshot)
    private val cache = object : LinkedHashMap<String, Cached>(12, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Cached>?): Boolean = size > 10
    }

    /**
     * Progressive search Flow.
     * Emits incremental snapshots as individual providers complete, preserving stable group identity.
     *
     * Invariants:
     * 1. Cancellation propagates immediately (CancellationException is NEVER swallowed).
     * 2. Provider failure or timeout in one provider does not cancel or corrupt sibling providers.
     * 3. Old queries are structurally cancelled at ViewModel collection level.
     * 4. Empty or zero-provider searches always emit a valid complete snapshot.
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

        val configHash = configuredProviders.map { it.id }.sorted().joinToString(",")
        val normalizedQuery = SearchNormalization.normalize(trimmed)
        val cacheKey = "$normalizedQuery#$page#$configHash"

        val cachedHit = synchronized(cache) {
            cache[cacheKey]?.takeIf { clockMs() - it.createdAt <= cacheTtlMs }?.result
        }
        if (cachedHit != null) {
            send(cachedHit.copy(fromCache = true))
            return@channelFlow
        }

        val hasLocal = localDiscovery != null && page == 0
        val totalWorkers = configuredProviders.size + (if (hasLocal) 1 else 0)

        if (totalWorkers == 0) {
            send(
                SearchSnapshot(
                    recordings = emptyList(),
                    failures = initialFailures,
                    isComplete = true,
                    providersPending = 0,
                )
            )
            return@channelFlow
        }

        val activeGroupList = mutableListOf<CanonicalRecording>()
        val failureMap = ConcurrentHashMap<String, ProviderFailure>()
        initialFailures.forEach { failureMap[it.providerId] = it }

        val pendingProviders = AtomicInteger(totalWorkers)
        val mergeMutex = Mutex()

        // 1. Instant local library discovery (on-device, zero-latency)
        if (hasLocal) {
            launch {
                try {
                    val localSources = localDiscovery!!.search(trimmed)
                    val currentList = mergeMutex.withLock {
                        if (localSources.isNotEmpty()) {
                            val merged = UnifiedTrackMerger.mergeSources(localSources, activeGroupList)
                            activeGroupList.clear()
                            activeGroupList.addAll(merged)
                        }
                        activeGroupList.toList()
                    }
                    val remaining = pendingProviders.decrementAndGet()
                    val snapshot = SearchSnapshot(
                        recordings = currentList,
                        failures = failureMap.values.toList(),
                        isComplete = remaining <= 0,
                        providersPending = remaining,
                    )
                    send(snapshot)
                    if (remaining <= 0) {
                        val sanitized = snapshot.sanitizeForCache()
                        synchronized(cache) { cache[cacheKey] = Cached(clockMs(), sanitized) }
                    }
                } catch (c: CancellationException) {
                    throw c
                } catch (e: Exception) {
                    if (debugLogging) Log.w("UnifiedSearch", "Local discovery failed: ${e.message}")
                    val remaining = pendingProviders.decrementAndGet()
                    val currentList = mergeMutex.withLock { activeGroupList.toList() }
                    val snapshot = SearchSnapshot(
                        recordings = currentList,
                        failures = failureMap.values.toList(),
                        isComplete = remaining <= 0,
                        providersPending = remaining,
                    )
                    send(snapshot)
                    if (remaining <= 0) {
                        val sanitized = snapshot.sanitizeForCache()
                        synchronized(cache) { cache[cacheKey] = Cached(clockMs(), sanitized) }
                    }
                }
            }
        }

        // 2. Parallel remote catalog discovery
        for (provider in configuredProviders) {
            launch {
                var wasCancelled = false
                try {
                    val outcome = withTimeout(timeoutMs) {
                        provider.search(trimmed, page)
                    }
                    when (outcome) {
                        is ProviderResult.Success -> {
                            val remoteSources = outcome.data.map(OnlineTrack::toRecordingSource)
                            if (remoteSources.isNotEmpty()) {
                                mergeMutex.withLock {
                                    val merged = UnifiedTrackMerger.mergeSources(remoteSources, activeGroupList)
                                    activeGroupList.clear()
                                    activeGroupList.addAll(merged)
                                }
                            }
                        }
                        ProviderResult.NetworkUnavailable -> failureMap[provider.id] = ProviderFailure(provider.id, FailureReason.NETWORK)
                        ProviderResult.RateLimited -> failureMap[provider.id] = ProviderFailure(provider.id, FailureReason.RATE_LIMIT)
                        ProviderResult.NotConfigured -> failureMap[provider.id] = ProviderFailure(provider.id, FailureReason.NOT_CONFIGURED)
                        ProviderResult.Unauthorized -> failureMap[provider.id] = ProviderFailure(provider.id, FailureReason.UNAUTHORIZED)
                        ProviderResult.Unavailable -> failureMap[provider.id] = ProviderFailure(provider.id, FailureReason.UNAVAILABLE)
                        is ProviderResult.Error -> failureMap[provider.id] = ProviderFailure(
                            provider.id,
                            if (outcome.code == -408) FailureReason.TIMEOUT else FailureReason.PROVIDER_ERROR,
                        )
                    }
                } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
                    failureMap[provider.id] = ProviderFailure(provider.id, FailureReason.TIMEOUT)
                } catch (cancelled: CancellationException) {
                    wasCancelled = true
                    throw cancelled
                } catch (_: Exception) {
                    failureMap[provider.id] = ProviderFailure(provider.id, FailureReason.PROVIDER_ERROR)
                } finally {
                    val remaining = pendingProviders.decrementAndGet()
                    val currentList = mergeMutex.withLock { activeGroupList.toList() }
                    val snapshot = SearchSnapshot(
                        recordings = currentList,
                        failures = failureMap.values.toList(),
                        isComplete = remaining <= 0,
                        providersPending = remaining,
                    )
                    if (!wasCancelled) {
                        send(snapshot)
                        if (remaining <= 0) {
                            val sanitized = snapshot.sanitizeForCache()
                            synchronized(cache) { cache[cacheKey] = Cached(clockMs(), sanitized) }
                        }
                    }
                }
            }
        }
    }

    private fun SearchSnapshot.sanitizeForCache(): SearchSnapshot {
        val sanitizedRecordings = recordings.map { rec ->
            rec.copy(
                sources = rec.sources.map { source ->
                    if (source is OnlineRecordingSource && source.originalTrack != null) {
                        val sanitizedTrack = source.originalTrack.copy(
                            previewUrl = null,
                            streamUrl = null,
                            downloadUrl = null,
                        )
                        source.copy(originalTrack = sanitizedTrack)
                    } else {
                        source
                    }
                }
            )
        }
        return copy(recordings = sanitizedRecordings)
    }

    /**
     * Backward-compatible one-shot search derived from the progressive Flow.
     */
    suspend fun searchOnce(query: String, page: Int = 0): SearchSnapshot =
        search(query, page).last()

    fun invalidate() = synchronized(cache) { cache.clear() }
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
