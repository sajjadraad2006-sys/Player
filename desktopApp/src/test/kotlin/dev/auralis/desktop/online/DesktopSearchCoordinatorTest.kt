package dev.auralis.desktop.online

import dev.auralis.studio.online.MusicCatalogProvider
import dev.auralis.studio.online.OnlineTrack
import dev.auralis.studio.online.PlaybackCapability
import dev.auralis.studio.online.ProviderResult
import dev.auralis.studio.online.SearchNormalization
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopSearchCoordinatorTest {

    private class MockCatalogProvider(
        override val id: String,
        override val displayName: String,
        override val isConfigured: Boolean = true,
        private val delayMs: Long = 0L,
        private val responseGenerator: suspend (String) -> ProviderResult<List<OnlineTrack>>,
    ) : MusicCatalogProvider {
        var callCount = 0
        override suspend fun search(query: String, page: Int): ProviderResult<List<OnlineTrack>> {
            callCount++
            if (delayMs > 0) delay(delayMs)
            return responseGenerator(query)
        }
    }

    @Test
    fun testArabicQueryNormalization() {
        val rawArabic = "  أغاني   أم كلثوم  "
        val normalized = SearchNormalization.normalize(rawArabic)
        // Alif variants normalized to bare alif, excess spaces collapsed
        assertEquals("اغاني ام كلثوم", normalized)
    }

    @Test
    fun testMultiProviderAggregationAndRanking() = runBlocking {
        val streamTrack = OnlineTrack(
            provider = "audius",
            id = "t1",
            title = "Streamable Song",
            artist = "Artist A",
            streamUrl = "https://example.com/stream.mp3",
            streamAllowed = true,
        )

        val previewTrack = OnlineTrack(
            provider = "jamendo",
            id = "t2",
            title = "Preview Song",
            artist = "Artist B",
            previewUrl = "https://example.com/preview.mp3",
            streamAllowed = false,
        )

        val unavailTrack = OnlineTrack(
            provider = "jamendo",
            id = "t3",
            title = "Unavailable Song",
            artist = "Artist C",
            streamAllowed = false,
        )

        val p1 = MockCatalogProvider("audius", "Audius") {
            ProviderResult.Success(listOf(streamTrack))
        }
        val p2 = MockCatalogProvider("jamendo", "Jamendo") {
            ProviderResult.Success(listOf(unavailTrack, previewTrack))
        }

        val coordinator = DesktopSearchCoordinator(listOf(p1, p2))
        coordinator.search("test")

        // Wait for search to complete
        var state = coordinator.state.first { !it.isSearching && it.results.isNotEmpty() }
        val results = state.results

        assertEquals(3, results.size)
        // Ranking: FULL_STREAM first, then PREVIEW, then UNAVAILABLE
        assertEquals(PlaybackCapability.FULL_STREAM, results[0].playbackCapability)
        assertEquals("t1", results[0].id)
        assertEquals(PlaybackCapability.PREVIEW, results[1].playbackCapability)
        assertEquals("t2", results[1].id)
        assertEquals(PlaybackCapability.UNAVAILABLE, results[2].playbackCapability)
        assertEquals("t3", results[2].id)
    }

    @Test
    fun testDeduplicationByProviderAndId() = runBlocking {
        val track1 = OnlineTrack(
            provider = "jamendo",
            id = "dup1",
            title = "Duplicate Song",
            artist = "Artist",
            streamUrl = "https://example.com/s.mp3",
            streamAllowed = true,
        )
        val track2 = track1.copy() // Duplicate

        val p1 = MockCatalogProvider("jamendo", "Jamendo") {
            ProviderResult.Success(listOf(track1, track2))
        }

        val coordinator = DesktopSearchCoordinator(listOf(p1))
        coordinator.search("dup")

        val state = coordinator.state.first { !it.isSearching && it.results.isNotEmpty() }
        assertEquals(1, state.results.size)
        assertEquals("dup1", state.results[0].id)
    }

    @Test
    fun testCancellationLatestQueryWins() = runBlocking {
        val p1 = MockCatalogProvider("slow", "Slow", delayMs = 200) { query ->
            ProviderResult.Success(
                listOf(
                    OnlineTrack(
                        provider = "slow",
                        id = query,
                        title = "Title for $query",
                        artist = "Artist",
                        streamUrl = "https://example.com/s.mp3",
                    )
                )
            )
        }

        val coordinator = DesktopSearchCoordinator(listOf(p1))

        coordinator.search("query1")
        delay(50) // Let query1 start
        coordinator.search("query2") // Rapidly preempt with query2

        val finalState = coordinator.state.first { !it.isSearching && it.query == "query2" && it.results.isNotEmpty() }
        assertEquals("query2", finalState.query)
        assertEquals("Title for query2", finalState.results.first().title)
    }

    @Test
    fun testPartialProviderFailureDoesNotBreakSearchResults() = runBlocking {
        val goodTrack = OnlineTrack(
            provider = "good",
            id = "g1",
            title = "Good Track",
            artist = "Good Artist",
            streamUrl = "https://example.com/good.mp3",
        )

        val pGood = MockCatalogProvider("good", "Good Provider") {
            ProviderResult.Success(listOf(goodTrack))
        }
        val pBad = MockCatalogProvider("bad", "Failing Provider") {
            ProviderResult.NetworkUnavailable
        }

        val coordinator = DesktopSearchCoordinator(listOf(pGood, pBad))
        coordinator.search("test")

        val state = coordinator.state.first { !it.isSearching && it.results.isNotEmpty() }
        assertEquals(1, state.results.size)
        assertEquals("g1", state.results[0].id)
        assertEquals(ProviderStatus.SUCCESS, state.providerStatuses["good"])
        assertEquals(ProviderStatus.NETWORK_ERROR, state.providerStatuses["bad"])
    }
}
