package dev.auralis.studio.playback

import dev.auralis.studio.core.model.Track
import dev.auralis.studio.online.CanonicalRecording
import dev.auralis.studio.online.OnlinePlaylist
import dev.auralis.studio.online.OnlineRecordingSource
import dev.auralis.studio.online.OnlineTrack
import dev.auralis.studio.online.StructuredIdentity
import dev.auralis.studio.ui.ActivePlaylistSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackQueueTest {

    private fun dummyTrack(id: Long, title: String, uri: String = "content://media/$id"): Track = Track(
        id = id,
        uri = uri,
        title = title,
        artist = "Test Artist",
        album = "Test Album",
        durationMs = 200_000L,
    )

    private fun dummyRecording(id: String, title: String): CanonicalRecording = CanonicalRecording(
        identity = StructuredIdentity(
            normalizedTitle = title.lowercase(),
            normalizedArtist = "online artist",
        ),
        title = title,
        artist = "Online Artist",
        album = "Online Album",
        durationMs = 210_000L,
        artworkUrl = "https://example.com/$id.jpg",
        sources = listOf(
            OnlineRecordingSource(
                provider = "youtube",
                providerTrackId = id,
                title = title,
                artist = "Online Artist",
                album = "Online Album",
                durationMs = 210_000L,
                artworkUrl = "https://example.com/$id.jpg",
                originalTrack = OnlineTrack(
                    id = id,
                    provider = "youtube",
                    title = title,
                    artist = "Online Artist",
                    album = "Online Album",
                    durationMs = 210_000L,
                    artworkUrl = "https://example.com/$id.jpg",
                ),
            ),
        ),
    )

    @Test
    fun `queue reordering shifts items correctly within bounds`() {
        val initialQueue = mutableListOf("Song A", "Song B", "Song C", "Song D", "Song E")

        // Helper simulation of Player.moveMediaItem(fromIndex, toIndex)
        fun <T> MutableList<T>.simulateMoveMediaItem(from: Int, to: Int) {
            if (from in indices && to in indices && from != to) {
                val item = removeAt(from)
                add(to, item)
            }
        }

        // Move "Song D" (index 3) to index 1 (between A and B)
        initialQueue.simulateMoveMediaItem(3, 1)
        assertEquals(listOf("Song A", "Song D", "Song B", "Song C", "Song E"), initialQueue)

        // Move "Song A" (index 0) to end (index 4)
        initialQueue.simulateMoveMediaItem(0, 4)
        assertEquals(listOf("Song D", "Song B", "Song C", "Song E", "Song A"), initialQueue)

        // Out of bounds movement does nothing
        initialQueue.simulateMoveMediaItem(-1, 2)
        initialQueue.simulateMoveMediaItem(2, 10)
        assertEquals(listOf("Song D", "Song B", "Song C", "Song E", "Song A"), initialQueue)
    }

    @Test
    fun `playNext inserts tracks immediately after current item index`() {
        val queue = mutableListOf(
            dummyTrack(1, "Track 1"),
            dummyTrack(2, "Track 2"),
            dummyTrack(3, "Track 3"),
        )
        val currentIndex = 0 // Currently playing Track 1

        val newBatch = listOf(
            dummyTrack(10, "Recommended Mix 1"),
            dummyTrack(11, "Recommended Mix 2"),
        )

        // Insert at currentMediaItemIndex + 1
        val insertIndex = (currentIndex + 1).coerceAtMost(queue.size)
        queue.addAll(insertIndex, newBatch)

        assertEquals(5, queue.size)
        assertEquals("Track 1", queue[0].title)
        assertEquals("Recommended Mix 1", queue[1].title)
        assertEquals("Recommended Mix 2", queue[2].title)
        assertEquals("Track 2", queue[3].title)
        assertEquals("Track 3", queue[4].title)
    }

    @Test
    fun `playNext at last track appends to end of queue`() {
        val queue = mutableListOf(
            dummyTrack(1, "Track 1"),
            dummyTrack(2, "Track 2"),
        )
        val currentIndex = 1 // Currently playing Track 2 (last item)

        val newBatch = listOf(dummyTrack(99, "Encore Track"))
        val insertIndex = (currentIndex + 1).coerceAtMost(queue.size)
        queue.addAll(insertIndex, newBatch)

        assertEquals(3, queue.size)
        assertEquals("Track 2", queue[1].title)
        assertEquals("Encore Track", queue[2].title)
    }

    @Test
    fun `active playlist session preserves prior tracks without truncation`() {
        val playlist = OnlinePlaylist(
            id = "PL12345",
            title = "Best of 2026",
            author = "Curator",
            trackCount = 5,
            artworkUrl = "https://example.com/thumb.jpg",
        )
        val allRecordings = listOf(
            dummyRecording("rec1", "Track 1"),
            dummyRecording("rec2", "Track 2"),
            dummyRecording("rec3", "Track 3"),
            dummyRecording("rec4", "Track 4"),
            dummyRecording("rec5", "Track 5"),
        )

        val startIndex = 2 // User clicked Track 3
        val session = ActivePlaylistSession(
            playlist = playlist,
            allTracks = allRecordings,
            currentIndex = startIndex,
        )

        // Verify session preserved all 5 tracks
        assertEquals(5, session.allTracks.size)
        assertEquals(2, session.currentIndex)
        assertEquals("Track 3", session.allTracks[session.currentIndex].title)

        // Prior tracks (indices 0 and 1) MUST NOT be discarded
        assertEquals("Track 1", session.allTracks[0].title)
        assertEquals("Track 2", session.allTracks[1].title)
    }

    @Test
    fun `active playlist session navigates bidirectionally with correct bounds`() {
        val playlist = OnlinePlaylist(
            id = "PL_NAV",
            title = "Navigation Test",
            author = "Tester",
            trackCount = 3,
            artworkUrl = "",
        )
        val tracks = listOf(
            dummyRecording("t1", "First"),
            dummyRecording("t2", "Second"),
            dummyRecording("t3", "Third"),
        )

        val session = ActivePlaylistSession(
            playlist = playlist,
            allTracks = tracks,
            currentIndex = 1, // Start at "Second"
        )

        // Forward navigation (skipNext)
        assertTrue(session.currentIndex < session.allTracks.size - 1)
        session.currentIndex++
        assertEquals(2, session.currentIndex)
        assertEquals("Third", session.allTracks[session.currentIndex].title)

        // At end: cannot advance further
        assertFalse(session.currentIndex < session.allTracks.size - 1)

        // Backward navigation (skipPrevious)
        assertTrue(session.currentIndex > 0)
        session.currentIndex--
        assertEquals(1, session.currentIndex)
        assertEquals("Second", session.allTracks[session.currentIndex].title)

        // Backward again to start
        assertTrue(session.currentIndex > 0)
        session.currentIndex--
        assertEquals(0, session.currentIndex)
        assertEquals("First", session.allTracks[session.currentIndex].title)

        // At start: cannot go previous
        assertFalse(session.currentIndex > 0)
    }

    @Test
    fun `recommendation queue maintains deduplication and advances unplayed items`() {
        val playedIdentities = mutableSetOf<String>()

        val rec1 = dummyRecording("r1", "Recommendation 1")
        val rec2 = dummyRecording("r2", "Recommendation 2")
        val rec3 = dummyRecording("r3", "Recommendation 3")

        val rawRecommendations = listOf(rec1, rec2, rec3)

        // Record rec1 as played
        playedIdentities.add(rec1.recordingIdentity)
        rec1.sources.forEach { playedIdentities.add(it.providerTrackId) }

        val unplayed = rawRecommendations.filter { rec ->
            rec.recordingIdentity !in playedIdentities &&
                rec.sources.none { it.providerTrackId in playedIdentities }
        }

        assertEquals(2, unplayed.size)
        assertEquals("Recommendation 2", unplayed[0].title)
        assertEquals("Recommendation 3", unplayed[1].title)

        // Advance queue: pop first
        var queue = unplayed
        val nextToPlay = queue.first()
        queue = queue.drop(1)

        assertEquals("Recommendation 2", nextToPlay.title)
        assertEquals(1, queue.size)
        assertEquals("Recommendation 3", queue.first().title)
    }

    @Test
    fun `shuffle mode preserves all playlist tracks without loss`() {
        val allRecordings = (1..10).map { dummyRecording("shuf_$it", "Track $it") }
        val playlist = OnlinePlaylist(
            id = "PL_SHUF",
            title = "Shuffle Mix",
            author = "DJ",
            trackCount = 10,
            artworkUrl = "",
        )

        val shuffled = allRecordings.shuffled()
        val session = ActivePlaylistSession(
            playlist = playlist,
            allTracks = shuffled,
            currentIndex = 0,
        )

        assertEquals(10, session.allTracks.size)
        assertEquals(
            allRecordings.map { it.recordingIdentity }.toSet(),
            session.allTracks.map { it.recordingIdentity }.toSet(),
        )
    }
}
