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
            assertEquals(0.40, client.versionMismatchPenalty("Song", "Song (Acoustic)"), 0.01)
        } finally {
            client.close()
        }
    }

    @Test fun studioTrackNeverBeatenByLiveOrAcousticDueToDurationProximity() {
        val client = LrclibClient()
        try {
            val studio = LrclibTrack(1, "Closer", "Artist", "Album", 200.0, syncedLyrics = "[00:01.00]lyrics")
            val acoustic = LrclibTrack(2, "Closer (Acoustic)", "Artist", "Album", 199.5, syncedLyrics = "[00:01.00]acoustic")
            val live = LrclibTrack(3, "Closer (Live)", "Artist", "Album", 199.8, syncedLyrics = "[00:01.00]live")
            val cover = LrclibTrack(4, "Closer (Cover)", "Artist", "Album", 200.0, syncedLyrics = "[00:01.00]cover")
            val karaoke = LrclibTrack(5, "Closer (Karaoke)", "Artist", "Album", 200.0, syncedLyrics = "[00:01.00]karaoke")
            val slowed = LrclibTrack(6, "Closer (Slowed)", "Artist", "Album", 200.0, syncedLyrics = "[00:01.00]slowed")
            val spedUp = LrclibTrack(7, "Closer (Sped Up)", "Artist", "Album", 200.0, syncedLyrics = "[00:01.00]sped")

            assertEquals(
                studio,
                client.pickBestCandidate(listOf(acoustic, live, cover, karaoke, slowed, spedUp, studio), "Closer", "Artist", "Album", 200_000),
            )
        } finally {
            client.close()
        }
    }

    @Test fun arabicQueryMatchesLatinCandidateWithEquivalentArtist() {
        val client = LrclibClient()
        try {
            val latinTrack = LrclibTrack(1, "Tamally Maak", "Amr Diab", "Tamally Maak", 269.0, syncedLyrics = "[00:01.00]Tamally maak")
            val candidate = client.pickBestCandidate(listOf(latinTrack), "تملي معاك", "عمرو دياب", "", 269_000)
            assertEquals(latinTrack, candidate)
        } finally {
            client.close()
        }
    }

    @Test fun acceptsYouTubeTrackWithVideoIntroVarianceWhenMetadataMatches() {
        val client = LrclibClient()
        try {
            val track = LrclibTrack(1, "In the End", "Linkin Park", "Hybrid Theory", 216.0, syncedLyrics = "[00:01.00]It starts with")
            // YouTube rip with 12 seconds of intro/outro (228 seconds total)
            val candidate = client.pickBestCandidate(listOf(track), "In the End", "Linkin Park", "", 228_000)
            assertEquals(track, candidate)
        } finally {
            client.close()
        }
    }
}


