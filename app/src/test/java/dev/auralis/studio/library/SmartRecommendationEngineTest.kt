package dev.auralis.studio.library

import dev.auralis.studio.core.model.Track
import dev.auralis.studio.online.OnlineTrack
import dev.auralis.studio.online.ProviderResult
import dev.auralis.studio.online.YouTubeCatalogProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartRecommendationEngineTest {

    private class FakeYouTubeCatalogProvider(
        private val watchNextResults: Map<String, List<OnlineTrack>> = emptyMap(),
        private val searchResults: Map<String, List<OnlineTrack>> = emptyMap(),
    ) : YouTubeCatalogProvider() {

        override suspend fun fetchWatchNext(videoId: String): ProviderResult<List<OnlineTrack>> {
            val list = watchNextResults[videoId] ?: emptyList()
            return ProviderResult.Success(list)
        }

        override suspend fun search(query: String, page: Int): ProviderResult<List<OnlineTrack>> {
            val list = searchResults[query] ?: emptyList()
            return ProviderResult.Success(list)
        }
    }

    @Test
    fun getOnlineRecommendations_withDirectYouTubeUri_callsFetchWatchNext() = runBlocking {
        val targetVideoId = "dQw4w9WgXcQ"
        val recommendedTrack = OnlineTrack(
            provider = "youtube",
            id = "nextSong1234",
            title = "Algorithmic Radio Hit",
            artist = "YouTube Artist",
            durationMs = 210_000L,
            sourceUrl = "https://www.youtube.com/watch?v=nextSong1234",
        )

        val fakeCatalog = FakeYouTubeCatalogProvider(
            watchNextResults = mapOf(
                targetVideoId to listOf(recommendedTrack)
            )
        )

        val engine = SmartRecommendationEngine(
            unifiedSearch = null,
            networkMonitor = null,
            youtubeCatalog = fakeCatalog,
        )

        val currentTrack = Track(
            id = 1L,
            uri = "https://www.youtube.com/watch?v=$targetVideoId",
            title = "Never Gonna Give You Up",
            artist = "Rick Astley",
        )

        val recommendations = engine.getOnlineRecommendations(currentTrack, limit = 10)
        assertEquals(1, recommendations.size)
        assertEquals("Algorithmic Radio Hit", recommendations[0].title)
        assertEquals("YouTube Artist", recommendations[0].artist)
    }

    @Test
    fun getOnlineRecommendations_withLocalMp3_resolvesTopMatchAndCallsWatchNext() = runBlocking {
        val resolvedVideoId = "resolvedVid1"
        val recommendedTrack = OnlineTrack(
            provider = "youtube",
            id = "recSong99999",
            title = "Related Hit",
            artist = "Matched Artist",
            durationMs = 180_000L,
            sourceUrl = "https://www.youtube.com/watch?v=recSong99999",
        )

        val fakeCatalog = FakeYouTubeCatalogProvider(
            searchResults = mapOf(
                "Queen Bohemian Rhapsody" to listOf(
                    OnlineTrack(
                        provider = "youtube",
                        id = resolvedVideoId,
                        title = "Bohemian Rhapsody",
                        artist = "Queen",
                    )
                )
            ),
            watchNextResults = mapOf(
                resolvedVideoId to listOf(recommendedTrack)
            )
        )

        val engine = SmartRecommendationEngine(
            unifiedSearch = null,
            networkMonitor = null,
            youtubeCatalog = fakeCatalog,
        )

        val localTrack = Track(
            id = 42L,
            uri = "content://media/external/audio/media/42",
            title = "Bohemian Rhapsody",
            artist = "Queen",
        )

        val recommendations = engine.getOnlineRecommendations(localTrack, limit = 10)
        assertEquals(1, recommendations.size)
        assertEquals("Related Hit", recommendations[0].title)
    }

    @Test
    fun getOnlineRecommendations_filtersOutCurrentTrackIdFromWatchNext() = runBlocking {
        val videoId = "song1111111"
        val currentTrackOnline = OnlineTrack(
            provider = "youtube",
            id = videoId,
            title = "Current Playing Song",
            artist = "Current Artist",
        )
        val nextTrackOnline = OnlineTrack(
            provider = "youtube",
            id = "song2222222",
            title = "Next Different Song",
            artist = "Another Artist",
        )

        val fakeCatalog = FakeYouTubeCatalogProvider(
            watchNextResults = mapOf(
                videoId to listOf(currentTrackOnline, nextTrackOnline)
            )
        )

        val engine = SmartRecommendationEngine(
            unifiedSearch = null,
            networkMonitor = null,
            youtubeCatalog = fakeCatalog,
        )

        val currentTrack = Track(
            id = 1L,
            uri = "https://www.youtube.com/watch?v=$videoId",
            title = "Current Playing Song",
            artist = "Current Artist",
        )

        val recommendations = engine.getOnlineRecommendations(currentTrack, limit = 10)
        assertEquals(1, recommendations.size)
        assertEquals("Next Different Song", recommendations[0].title)
    }
}
