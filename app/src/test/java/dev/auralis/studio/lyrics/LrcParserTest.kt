package dev.auralis.studio.lyrics

import dev.auralis.studio.core.model.LyricsSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LrcParserTest {

    @Test
    fun parse_plainLyrics_returnsUnsynced() {
        val plain = """
            Hello world
            This is a plain lyrics text
            Without timestamps
        """.trimIndent()

        val parsed = LrcParser.parse(plain, LyricsSource.LOCAL_LRC)
        assertFalse(parsed.synced)
        assertEquals(3, parsed.lines.size)
        assertEquals("Hello world", parsed.lines[0].text)
        assertEquals(-1L, parsed.lines[0].timeMs)
        assertNull(parsed.lines[0].words)
    }

    @Test
    fun parse_lineSyncedLrc_returnsSortedLines() {
        val lrc = """
            [ti:Test Title]
            [ar:Test Artist]
            [00:15.50]Second line
            [00:05.00]First line
            [00:30.00]Third line
        """.trimIndent()

        val parsed = LrcParser.parse(lrc, LyricsSource.LRCLIB)
        assertTrue(parsed.synced)
        assertEquals(3, parsed.lines.size)
        assertEquals("First line", parsed.lines[0].text)
        assertEquals(5000L, parsed.lines[0].timeMs)
        assertEquals("Second line", parsed.lines[1].text)
        assertEquals(15500L, parsed.lines[1].timeMs)
        assertEquals("Third line", parsed.lines[2].text)
        assertEquals(30000L, parsed.lines[2].timeMs)
    }

    @Test
    fun parse_enhancedLrc_parsesGenuineWordTimestamps() {
        val lrc = """
            [00:12.34]<00:12.34>Hello <00:12.80>world <00:13.20>this <00:13.70>is <00:14.10>synced
        """.trimIndent()

        val parsed = LrcParser.parse(lrc, LyricsSource.LRCLIB)
        assertTrue(parsed.synced)
        assertEquals(1, parsed.lines.size)

        val line = parsed.lines[0]
        assertEquals(12340L, line.timeMs)
        assertEquals("Hello world this is synced", line.text)
        assertTrue(line.hasWordTiming)
        assertNotNull(line.words)

        val words = line.words!!
        assertEquals(5, words.size)
        assertEquals("Hello ", words[0].text)
        assertEquals(12340L, words[0].startTimeMs)

        assertEquals("world ", words[1].text)
        assertEquals(12800L, words[1].startTimeMs)

        assertEquals("this ", words[2].text)
        assertEquals(13200L, words[2].startTimeMs)

        assertEquals("is ", words[3].text)
        assertEquals(13700L, words[3].startTimeMs)

        assertEquals("synced", words[4].text)
        assertEquals(14100L, words[4].startTimeMs)
    }

    @Test
    fun toLrcString_roundTripsCorrectly() {
        val lrc = """
            [00:05.00]First line
            [00:15.50]Second line
        """.trimIndent()

        val parsed = LrcParser.parse(lrc)
        val serialized = LrcParser.toLrcString(parsed)
        val reparsed = LrcParser.parse(serialized)

        assertEquals(parsed.lines.size, reparsed.lines.size)
        assertEquals(parsed.lines[0].timeMs, reparsed.lines[0].timeMs)
        assertEquals(parsed.lines[0].text, reparsed.lines[0].text)
        assertEquals(parsed.lines[1].timeMs, reparsed.lines[1].timeMs)
        assertEquals(parsed.lines[1].text, reparsed.lines[1].text)
    }

    @Test
    fun parse_extractsOffsetTag() {
        val lrc = """
            [ti:Test Title]
            [ar:Test Artist]
            [offset:+1500]
            [00:05.00]First line
            [00:15.50]Second line
        """.trimIndent()

        val parsed = LrcParser.parse(lrc)
        assertTrue(parsed.synced)
        assertEquals(1500L, parsed.offsetMs)
        assertEquals(2, parsed.lines.size)
    }
}
