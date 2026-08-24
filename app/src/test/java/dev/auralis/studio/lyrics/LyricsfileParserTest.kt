package dev.auralis.studio.lyrics

import dev.auralis.studio.core.model.LyricsSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the LRCLIB `lyricsfile` (YAML) parser.
 * Format reference: https://lrclib.net/lyricsfile (CC0 draft 1.0).
 */
class LyricsfileParserTest {

    @Test
    fun `parses genuine word timing`() {
        val yaml = """
            version: "1.0"
            metadata:
              title: Still Alive
              artist: GLaDOS
            lines:
              - text: This was a triumph.
                words:
                  - text: "This "
                    start_ms: 290
                    end_ms: 536
                  - text: "was "
                    start_ms: 536
                  - text: triumph.
                    start_ms: 923
                start_ms: 290
                end_ms: 3840
        """.trimIndent()

        val result = LyricsfileParser.parse(yaml, LyricsSource.LRCLIB)
        assertNotNull(result)
        assertTrue(result!!.synced)
        assertEquals(1, result.lines.size)

        val line = result.lines[0]
        assertEquals(290L, line.timeMs)
        assertTrue(line.hasWordTiming)

        val words = line.words!!
        assertEquals(3, words.size)
        assertEquals("This ", words[0].text)
        assertEquals(290L, words[0].startTimeMs)
        assertEquals(536L, words[0].endTimeMs) // explicit end preserved
        assertEquals(-1L, words[1].endTimeMs)  // absent end stays unknown, NOT invented
    }

    @Test
    fun `words without explicit timestamps are dropped not invented`() {
        val yaml = """
            version: "1.0"
            lines:
              - text: No word timing here
                start_ms: 1000
                end_ms: 4000
        """.trimIndent()

        val result = LyricsfileParser.parse(yaml)!!
        assertTrue(result.synced)
        assertFalse(result.lines[0].hasWordTiming) // no fabricated karaoke timing
    }

    @Test
    fun `line timing without words yields synced line lyrics`() {
        val yaml = """
            version: '1.0'
            lines:
              - text: 'Waiting for the signal'
                start_ms: 12400
                end_ms: 15750
              - text: 'Watching all the lights change'
                start_ms: 15800
                end_ms: 20100
        """.trimIndent()

        val result = LyricsfileParser.parse(yaml)!!
        assertTrue(result.synced)
        assertEquals(2, result.lines.size)
        assertEquals(12_400L, result.lines[0].timeMs)
        assertFalse(result.lines[0].hasWordTiming)
    }

    @Test
    fun `overlapping vocals are preserved`() {
        val yaml = """
            version: "1.0"
            lines:
              - text: I will follow the river
                start_ms: 10000
                end_ms: 15000
              - text: Follow the river home
                start_ms: 12500
                end_ms: 16500
        """.trimIndent()

        val result = LyricsfileParser.parse(yaml)!!
        assertEquals(2, result.lines.size) // overlap kept, not merged/dropped
        assertTrue(result.lines[1].timeMs < result.lines[0].timeMs + 5000)
    }

    @Test
    fun `plain-only document returns unsynced lyrics`() {
        val yaml = """
            version: '1.0'
            plain: |
              Morning light through the window
              A new day begins
        """.trimIndent()

        val result = LyricsfileParser.parse(yaml)!!
        assertFalse(result.synced)
        assertEquals(2, result.lines.size)
        assertEquals("Morning light through the window", result.lines[0].text)
    }

    @Test
    fun `garbage input returns null instead of empty lyrics`() {
        assertNull(LyricsfileParser.parse("not a lyricsfile at all"))
        assertNull(LyricsfileParser.parse(""))
    }

    @Test
    fun `instrumental flag parsed`() {
        val doc = LyricsfileParser.parseDocument(
            """
            version: '1.0'
            instrumental: true
            """.trimIndent(),
        )
        assertNotNull(doc)
        assertTrue(doc!!.instrumental)
    }
}
