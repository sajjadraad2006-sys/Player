package dev.auralis.studio.online

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withTimeout
import java.util.LinkedHashMap

data class ProviderFailure(val providerId: String, val reason: FailureReason)

enum class FailureReason { NOT_CONFIGURED, NETWORK, TIMEOUT, RATE_LIMIT, UNAUTHORIZED, UNAVAILABLE, PROVIDER_ERROR }

data class UnifiedSearchResult(
    val tracks: List<UnifiedTrack>,
    val failures: List<ProviderFailure> = emptyList(),
    val fromCache: Boolean = false,
)

class UnifiedSearchAggregator(
    private val providers: List<MusicCatalogProvider>,
    private val timeoutMs: Long = 12_000L,
    private val cacheTtlMs: Long = 90_000L,
    private val clockMs: () -> Long = System::currentTimeMillis,
    private val debugLogging: Boolean = false,
) {
    private data class Cached(val createdAt: Long, val result: UnifiedSearchResult)
    private val cache = object : LinkedHashMap<String, Cached>(12, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Cached>?): Boolean = size > 10
    }

    suspend fun search(query: String, page: Int = 0): UnifiedSearchResult {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return UnifiedSearchResult(emptyList())
        val key = "${trimmed.lowercase()}#$page"
        synchronized(cache) {
            cache[key]?.takeIf { clockMs() - it.createdAt <= cacheTtlMs }?.let {
                return it.result.copy(fromCache = true)
            }
        }

        val outcomes = supervisorScope {
            providers.filter(MusicCatalogProvider::isConfigured).map { provider ->
                async {
                    try {
                        provider.id to withTimeout(timeoutMs) { provider.search(trimmed, page) }
                    } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
                        provider.id to ProviderResult.Error("timeout", -408)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        provider.id to ProviderResult.Error("provider_error")
                    }
                }
            }.map { it.await() }
        }

        val tracks = mutableListOf<OnlineTrack>()
        val failures = providers.filterNot(MusicCatalogProvider::isConfigured)
            .map { ProviderFailure(it.id, FailureReason.NOT_CONFIGURED) }.toMutableList()
        outcomes.forEach { (providerId, result) ->
            when (result) {
                is ProviderResult.Success -> {
                    tracks += result.data
                    if (debugLogging) Log.d("UnifiedSearch", "query=$trimmed provider=$providerId results=${result.data.size}")
                }
                ProviderResult.NetworkUnavailable -> failures += ProviderFailure(providerId, FailureReason.NETWORK)
                ProviderResult.RateLimited -> failures += ProviderFailure(providerId, FailureReason.RATE_LIMIT)
                ProviderResult.NotConfigured -> failures += ProviderFailure(providerId, FailureReason.NOT_CONFIGURED)
                ProviderResult.Unauthorized -> failures += ProviderFailure(providerId, FailureReason.UNAUTHORIZED)
                ProviderResult.Unavailable -> failures += ProviderFailure(providerId, FailureReason.UNAVAILABLE)
                is ProviderResult.Error -> failures += ProviderFailure(
                    providerId,
                    if (result.code == -408) FailureReason.TIMEOUT else FailureReason.PROVIDER_ERROR,
                )
            }
        }

        val merged = UnifiedTrackMerger.merge(tracks)
        if (debugLogging) merged.forEach {
            Log.d("UnifiedSearch", "identity=${it.recordingIdentity} sources=${it.sources.joinToString { source -> source.provider }}")
        }
        val result = UnifiedSearchResult(merged, failures)
        synchronized(cache) { cache[key] = Cached(clockMs(), result) }
        return result
    }

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
