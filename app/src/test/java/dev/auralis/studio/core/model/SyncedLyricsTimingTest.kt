package dev.auralis.studio.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SyncedLyricsTimingTest {
    private val lyrics = SyncedLyrics(
        lines = listOf(LyricsLine(1_000, "one"), LyricsLine(2_000, "two"), LyricsLine(3_000, "three")),
        synced = true,
        source = LyricsSource.LOCAL_LRC,
        offsetMs = 250,
    )

    @Test fun `boundaries and offset select genuine timestamp`() {
        assertEquals(-1, lyrics.activeIndex(1_249))
        assertEquals(0, lyrics.activeIndex(1_250))
        assertEquals(1, lyrics.activeIndex(2_250))
        assertEquals(2, lyrics.activeIndex(9_000))
    }

    @Test fun `window and tap seek use separate offset`() {
        val window = lyrics.windowAt(2_250)
        assertEquals("one", window.previous?.text)
        assertEquals("two", window.current?.text)
        assertEquals("three", window.next?.text)
        assertEquals(2_250, lyrics.seekPositionFor(lyrics.lines[1]))
    }

    @Test fun `plain lyrics have no active line`() {
        val plain = lyrics.copy(synced = false)
        assertEquals(-1, plain.activeIndex(5_000))
        assertNull(plain.windowAt(5_000).current)
    }
}
