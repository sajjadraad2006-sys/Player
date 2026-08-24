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

    @Test fun requestedLiveVersionBeatsStudioCandidateEvenWhenStudioDurationIsCloser() {
        val client = LrclibClient()
        try {
            val studio = LrclibTrack(1, "Northern Lights", "Artist", "Album", 240.0, syncedLyrics = "[00:01.00]studio")
            val live = LrclibTrack(2, "Northern Lights (Live)", "Artist", "Album", 246.0, syncedLyrics = "[00:01.00]live")
            assertEquals(
                live,
                client.pickBestCandidate(listOf(studio, live), "Northern Lights (Live)", "Artist", "Album", 241_000),
            )
        } finally {
            client.close()
        }
    }

    @Test fun studioQueryRejectsOtherwiseCloseRemixCandidate() {
        val client = LrclibClient()
        try {
            val original = LrclibTrack(1, "Song", "Artist", "Album", 181.0, plainLyrics = "original")
            val remix = LrclibTrack(2, "Song (Remix)", "Artist", "Album", 180.0, syncedLyrics = "[00:01.00]remix")
            assertEquals(
                original,
                client.pickBestCandidate(listOf(remix, original), "Song", "Artist", "Album", 180_000),
            )
            assertEquals(0.24, client.versionMismatchPenalty("Song", "Song (Acoustic)"), 0.0)
        } finally {
            client.close()
        }
    }
}
