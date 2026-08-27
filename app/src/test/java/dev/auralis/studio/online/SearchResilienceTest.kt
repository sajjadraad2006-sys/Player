package dev.auralis.studio.online

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchResilienceTest {

    private class FakeProvider(
        override val id: String,
        private val delayMs: Long = 0L,
        private val shouldThrow: Boolean = false,
        private val shouldTimeout: Boolean = false,
        private val tracks: List<OnlineTrack> = emptyList(),
    ) : MusicCatalogProvider {
        override val displayName: String = id
        override val isConfigured: Boolean = true

        override suspend fun search(query: String, page: Int): ProviderResult<List<OnlineTrack>> {
            if (delayMs > 0) delay(delayMs)
            if (shouldTimeout) {
                delay(30_000L) // Exceeds aggregator timeout
            }
            if (shouldThrow) {
                throw RuntimeException("Network crashed for $id")
            }
            return ProviderResult.Success(tracks)
        }
    }

    private fun sampleTrack(provider: String, id: String, title: String): OnlineTrack = OnlineTrack(
        provider = provider,
        id = id,
        title = title,
        artist = "Artist",
        streamAllowed = true,
        streamUrl = "https://example.com/stream",
    )

    @Test
    fun testProviderFailureIsolation_siblingSuccessPreserved() = runTest {
        val failingProvider = FakeProvider("failing", shouldThrow = true)
        val slowTimeoutProvider = FakeProvider("timeout_p", shouldTimeout = true)
        val healthyProvider = FakeProvider(
            "healthy",
            tracks = listOf(sampleTrack("healthy", "h1", "Healthy Track")),
        )

        val aggregator = UnifiedSearchAggregator(
            providers = listOf(failingProvider, slowTimeoutProvider, healthyProvider),
            timeoutMs = 100L, // 100ms test timeout
        )

        val finalSnapshot = aggregator.searchOnce("test")

        assertEquals("Healthy provider tracks must be emitted despite sibling failures", 1, finalSnapshot.recordings.size)
        assertEquals("Healthy Track", finalSnapshot.recordings.first().title)

        val failureIds = finalSnapshot.failures.map { it.providerId }.toSet()
        assertTrue("Failing provider must be recorded in failures", failureIds.contains("failing"))
        assertTrue("Timeout provider must be recorded in failures", failureIds.contains("timeout_p"))
    }

    @Test
    fun testFlowCancellation_cancelledFlowDoesNotEmitFurther() = runTest {
        val slowProvider = FakeProvider(
            "slow",
            delayMs = 2_000L,
            tracks = listOf(sampleTrack("slow", "s1", "Slow Result")),
        )

        val aggregator = UnifiedSearchAggregator(
            providers = listOf(slowProvider),
            timeoutMs = 5_000L,
        )

        val collectedSnapshots = mutableListOf<SearchSnapshot>()
        val job = launch {
            aggregator.search("test query").collect { snapshot ->
                collectedSnapshots.add(snapshot)
            }
        }

        // Give a tiny head start, then cancel
        delay(50L)
        job.cancel()
        job.join()

        // After cancellation, no further snapshots should have been emitted from the slow provider
        // Either 0 or 1 snapshots collected, but the slow provider's result should not have arrived
        assertTrue("After cancellation, at most partial snapshots", collectedSnapshots.size <= 1)
    }
}
