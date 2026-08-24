package dev.auralis.studio.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Proves the offset contract: a stored `+100 ms` user offset means EXACTLY
 * +100 ms of delay in active-line selection — and −100 ms the exact opposite.
 *
 * The single timing equation under test:
 *   effectiveLyricsTime = playbackPosition − offsetMs
 * (a positive offset delays lyrics; source timestamps are never rewritten)
 */
class OffsetMathTest {

    private fun lyrics(offsetMs: Long) = SyncedLyrics(
        lines = listOf(
            LyricsLine(10_000L, "line A"),
            LyricsLine(20_000L, "line B"),
            LyricsLine(30_000L, "line C"),
        ),
        synced = true,
        source = LyricsSource.LRCLIB,
        offsetMs = offsetMs,
    )

    @Test
    fun `zero offset activates line at its raw timestamp`() {
        val l = lyrics(0)
        assertEquals(0, l.activeIndex(10_000L))
        assertEquals(1, l.activeIndex(20_000L))
    }

    @Test
    fun `plus 100ms means exactly 100ms later activation`() {
        val l = lyrics(+100)
        // Line B starts at 20000; with +100 offset it must activate at 20100, not before.
        assertEquals(0, l.activeIndex(20_099L))
        assertEquals(1, l.activeIndex(20_100L))
        assertEquals(1, l.activeIndex(20_150L))
    }

    @Test
    fun `minus 100ms means exactly 100ms earlier activation`() {
        val l = lyrics(-100)
        assertEquals(0, l.activeIndex(19_899L))
        assertEquals(1, l.activeIndex(19_900L))
    }

    @Test
    fun `offset never rewrites source timestamps`() {
        val l = lyrics(+100)
        assertEquals(20_000L, l.lines[1].timeMs)
        assertEquals(20_100L, l.seekPositionFor(l.lines[1]))
    }
}
