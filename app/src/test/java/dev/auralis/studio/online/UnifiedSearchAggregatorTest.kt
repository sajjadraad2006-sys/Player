package dev.auralis.studio.online

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UnifiedSearchAggregatorTest {
    @Test
    fun `one provider failure still returns another provider results`() = runTest {
        val working = provider("working") { ProviderResult.Success(listOf(track("audius"))) }
        val failing = provider("failing") { ProviderResult.Error("hidden transport detail") }
        val result = UnifiedSearchAggregator(listOf(working, failing)).search("song")
        assertEquals(1, result.tracks.size)
        assertEquals(FailureReason.PROVIDER_ERROR, result.failures.single().reason)
    }

    @Test
    fun `rapid query cancellation reaches obsolete provider work`() = runTest {
        val started = CompletableDeferred<Unit>()
        val cancelled = CompletableDeferred<Unit>()
        val slow = provider("slow") {
            started.complete(Unit)
            try { awaitCancellation() } finally { cancelled.complete(Unit) }
        }
        val job = async { UnifiedSearchAggregator(listOf(slow), timeoutMs = 60_000).search("old") }
        started.await()
        job.cancelAndJoin()
        assertTrue(cancelled.isCompleted)
    }

    @Test
    fun `identical rapid query uses short lived cache`() = runTest {
        var calls = 0
        val source = provider("source") { calls++; ProviderResult.Success(listOf(track("audius"))) }
        val aggregator = UnifiedSearchAggregator(listOf(source))
        assertFalse(aggregator.search("song").fromCache)
        assertTrue(aggregator.search("song").fromCache)
        assertEquals(1, calls)
    }

    @Test
    fun `provider timeout is isolated and classified`() = runTest {
        val slow = provider("slow") {
            delay(5_000)
            ProviderResult.Success(emptyList())
        }
        val result = UnifiedSearchAggregator(listOf(slow), timeoutMs = 100).search("song")
        assertEquals(FailureReason.TIMEOUT, result.failures.single().reason)
        assertTrue(result.tracks.isEmpty())
    }

    private fun provider(providerId: String, block: suspend () -> ProviderResult<List<OnlineTrack>>) =
        object : MusicCatalogProvider {
            override val id = providerId
            override val displayName = providerId
            override val isConfigured = true
            override suspend fun search(query: String, page: Int) = block()
        }

    private fun track(provider: String) = OnlineTrack(provider, "1", "Song", "Artist", durationMs = 180_000)
}
