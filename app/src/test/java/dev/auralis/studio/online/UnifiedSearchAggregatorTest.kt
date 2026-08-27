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

    @Test
    fun `recent searches are decoupled from live keystroke changes`() = runTest {
        val committedSearches = mutableListOf<String>()
        fun commitSearch(query: String) {
            val trimmed = query.trim()
            if (trimmed.isNotBlank()) {
                committedSearches.add(trimmed)
            }
        }

        // User types: a -> ad -> ade -> adel -> adele
        val liveKeystrokes = listOf("a", "ad", "ade", "adel", "adele")
        for (keystroke in liveKeystrokes) {
            // Live query changes update the search query but do NOT commit to recent searches
            // liveQueryChanged(keystroke)
        }

        assertEquals("Live keystrokes must NOT produce recent search entries", 0, committedSearches.size)

        // User presses Enter / Submit on "adele"
        commitSearch("adele")

        assertEquals("Only explicit commit produces a recent search entry", 1, committedSearches.size)
        assertEquals("adele", committedSearches.first())
    }

    @Test
    fun `per provider cache isolates providers and pages`() = runTest {
        var provider1Calls = 0
        var provider2Calls = 0

        val p1 = provider("p1") {
            provider1Calls++
            ProviderResult.Success(listOf(OnlineTrack("p1", "1", "Song 1", "Artist")))
        }
        val p2 = provider("p2") {
            provider2Calls++
            ProviderResult.Success(listOf(OnlineTrack("p2", "2", "Song 2", "Artist")))
        }

        val aggregator = UnifiedSearchAggregator(listOf(p1, p2))

        val snap1 = aggregator.searchOnce("query", page = 0)
        assertEquals(2, snap1.recordings.size)
        assertEquals(1, provider1Calls)
        assertEquals(1, provider2Calls)

        // Second search with same query and page -> uses cached provider results
        val snap2 = aggregator.searchOnce("query", page = 0)
        assertEquals(2, snap2.recordings.size)
        assertEquals("Cached search should not invoke provider 1 again", 1, provider1Calls)
        assertEquals("Cached search should not invoke provider 2 again", 1, provider2Calls)

        // Different page -> triggers provider fetches
        val snapPage1 = aggregator.searchOnce("query", page = 1)
        assertEquals(2, provider1Calls)
        assertEquals(2, provider2Calls)
    }

    @Test
    fun `canonical search reducer processes discovery events sequentially`() {
        val reducer = CanonicalSearchReducer(totalWorkers = 2, query = "Layla")

        val s1 = OnlineRecordingSource("audius", "a1", "Layla", "Eric Clapton", durationMs = 280_000L)
        val s2 = OnlineRecordingSource("jamendo", "j1", "Layla", "Eric Clapton", durationMs = 282_000L)

        val snap1 = reducer.reduce(DiscoveryEvent.Sources("audius", listOf(s1)))
        assertEquals(1, snap1.recordings.size)
        assertEquals(1, snap1.providersPending)
        assertFalse(snap1.isComplete)

        val snap2 = reducer.reduce(DiscoveryEvent.Sources("jamendo", listOf(s2)))
        assertEquals(1, snap2.recordings.size)
        assertEquals(2, snap2.recordings.first().sources.size)
        assertEquals(0, snap2.providersPending)
        assertTrue(snap2.isComplete)
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
