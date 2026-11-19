package dev.auralis.studio.lyrics

import dev.auralis.studio.core.model.LyricsLine
import dev.auralis.studio.core.model.LyricsSource
import dev.auralis.studio.core.model.LyricsWord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class YouTubeLyricsTranslationTest {

    @Test
    fun lyricsLine_supportsTranslationProperty() {
        val word = LyricsWord(startTimeMs = 1000L, text = "Hello")
        val line = LyricsLine(
            timeMs = 1000L,
            text = "Hello world",
            words = listOf(word),
            translation = "مرحبا بالعالم",
        )

        assertEquals(1000L, line.timeMs)
        assertEquals("Hello world", line.text)
        assertEquals("مرحبا بالعالم", line.translation)
        assertTrue(line.hasWordTiming)
    }

    @Test
    fun lrcParser_parsesDualLanguageLinesWithSeparator() {
        val lrc = """
            [ti:Test Song]
            [ar:Test Artist]
            [00:12.50] Hello darkness my old friend || مرحبا بالظلام صديقي القديم
            [00:18.20] I've come to talk with you again || جئت لأتحدث معك مرة أخرى
            [00:24.00] Plain line without translation
        """.trimIndent()

        val parsed = LrcParser.parse(lrc, LyricsSource.LOCAL_LRC)

        assertTrue(parsed.synced)
        assertEquals(3, parsed.lines.size)

        assertEquals("Hello darkness my old friend", parsed.lines[0].text)
        assertEquals("مرحبا بالظلام صديقي القديم", parsed.lines[0].translation)
        assertEquals(12_500L, parsed.lines[0].timeMs)

        assertEquals("I've come to talk with you again", parsed.lines[1].text)
        assertEquals("جئت لأتحدث معك مرة أخرى", parsed.lines[1].translation)
        assertEquals(18_200L, parsed.lines[1].timeMs)

        assertEquals("Plain line without translation", parsed.lines[2].text)
        assertNull(parsed.lines[2].translation)
        assertEquals(24_000L, parsed.lines[2].timeMs)
    }

    @Test
    fun lyricsTranslator_parseTranslationBody_handlesSingleAndMultiSegmentOutputs() {
        // Single segment
        val singleJson = """[[["مرحبا بك","Hello",null,null,10]],null,"en"]"""
        val singleResult = LyricsTranslator.parseTranslationBody(singleJson)
        assertEquals("مرحبا بك", singleResult)

        // Multi segment with newlines
        val multiJson = """[[["السطر الأول\n","Line 1\n",null,null,10],["السطر الثاني","Line 2",null,null,10]],null,"en"]"""
        val multiResult = LyricsTranslator.parseTranslationBody(multiJson)
        assertEquals("السطر الأول\nالسطر الثاني", multiResult)

        // Empty / invalid JSON
        val emptyResult = LyricsTranslator.parseTranslationBody("[]")
        assertNull(emptyResult)
    }

    @Test
    fun timestampAlignment_alignsTranslatedCaptionsWithinTolerance() {
        val originalLines = listOf(
            LyricsLine(timeMs = 5_000L, text = "First English line"),
            LyricsLine(timeMs = 10_000L, text = "Second English line"),
            LyricsLine(timeMs = 15_000L, text = "Third English line"),
        )

        val translatedLines = listOf(
            LyricsLine(timeMs = 5_100L, text = "السطر العربي الأول"),
            LyricsLine(timeMs = 10_050L, text = "السطر العربي الثاني"),
            LyricsLine(timeMs = 15_200L, text = "السطر العربي الثالث"),
        )

        val aligned = originalLines.mapIndexed { idx, orig ->
            val match = translatedLines.firstOrNull { abs(it.timeMs - orig.timeMs) < 1200L }
                ?: translatedLines.getOrNull(idx)
            if (match != null && match.text.isNotBlank() && match.text != orig.text) {
                orig.copy(translation = match.text)
            } else {
                orig
            }
        }

        assertEquals(3, aligned.size)
        assertEquals("السطر العربي الأول", aligned[0].translation)
        assertEquals("السطر العربي الثاني", aligned[1].translation)
        assertEquals("السطر العربي الثالث", aligned[2].translation)
        assertEquals(5_000L, aligned[0].timeMs)
        assertEquals(10_000L, aligned[1].timeMs)
        assertEquals(15_000L, aligned[2].timeMs)
    }
}
