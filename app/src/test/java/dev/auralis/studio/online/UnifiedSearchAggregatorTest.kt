package dev.auralis.studio.online

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
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
        val result = UnifiedSearchAggregator(listOf(working, failing)).searchOnce("song")
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
        val job = launch { UnifiedSearchAggregator(listOf(slow), timeoutMs = 60_000).search("old").collect {} }
        started.await()
        job.cancelAndJoin()
        assertTrue(cancelled.isCompleted)
    }

    @Test
    fun `identical rapid query uses short lived cache`() = runTest {
        var calls = 0
        val source = provider("source") { calls++; ProviderResult.Success(listOf(track("audius"))) }
        val aggregator = UnifiedSearchAggregator(listOf(source))
        assertFalse(aggregator.searchOnce("song").fromCache)
        assertTrue(aggregator.searchOnce("song").fromCache)
        assertEquals(1, calls)
    }

    @Test
    fun `provider timeout is isolated and classified`() = runTest {
        val slow = provider("slow") {
            delay(5_000)
            ProviderResult.Success(emptyList())
        }
        val result = UnifiedSearchAggregator(listOf(slow), timeoutMs = 100).searchOnce("song")
        assertEquals(FailureReason.TIMEOUT, result.failures.single().reason)
        assertTrue(result.tracks.isEmpty())
    }

    @OptIn(kotlinx.coroutines.FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun `query A cancellation prevents stale updates after query B becomes active`() = runTest {
        val queryAStarted = CompletableDeferred<Unit>()
        val slowProvider = object : MusicCatalogProvider {
            override val id = "slow"
            override val displayName = "slow"
            override val isConfigured = true
            override suspend fun search(query: String, page: Int): ProviderResult<List<OnlineTrack>> {
                return if (query == "queryA") {
                    queryAStarted.complete(Unit)
                    delay(5_000L)
                    ProviderResult.Success(listOf(OnlineTrack(id, "1", "Result A", "Artist")))
                } else {
                    ProviderResult.Success(listOf(OnlineTrack(id, "2", "Result B", "Artist")))
                }
            }
        }
        val aggregator = UnifiedSearchAggregator(listOf(slowProvider))

        val searchQuery = kotlinx.coroutines.flow.MutableStateFlow("")
        val emittedTitles = mutableListOf<String>()

        val searchJob = launch {
            searchQuery
                .debounce(50)
                .map { it.trim() }
                .distinctUntilChanged()
                .flatMapLatest { query ->
                    if (query.isBlank()) flowOf(SearchSnapshot.EMPTY)
                    else aggregator.search(query)
                }
                .collect { snapshot ->
                    snapshot.recordings.firstOrNull()?.let { emittedTitles.add(it.title) }
                }
        }

        searchQuery.value = "queryA"
        queryAStarted.await()

        // Query B supersedes Query A
        searchQuery.value = "queryB"
        delay(300)

        searchJob.cancelAndJoin()

        assertTrue("Result B should be emitted", emittedTitles.contains("Result B"))
        assertFalse("Result A must NEVER be emitted after being cancelled by Query B", emittedTitles.contains("Result A"))
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
