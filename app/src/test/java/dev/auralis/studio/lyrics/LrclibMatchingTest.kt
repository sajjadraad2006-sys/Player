package dev.auralis.studio.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LrclibMatchingTest {
    @Test fun exactMetadataWinsAndMissingLyricsAreRejected() {
        val client = LrclibClient()
        try {
            val exact = LrclibTrack(1, "Song", "Artist", "Album", 180.0, plainLyrics = "lyrics")
            val empty = LrclibTrack(2, "Song", "Artist", "Album", 180.0)
            val wrong = LrclibTrack(3, "Different", "Someone", "Else", 180.0, plainLyrics = "wrong")
            assertEquals(exact, client.pickBestCandidate(listOf(empty, wrong, exact), "Song", "Artist", "Album", 180_000))
            assertNull(client.pickBestCandidate(listOf(empty, wrong), "Song", "Artist", "Album", 180_000))
        } finally {
            client.close()
        }
    }
}
