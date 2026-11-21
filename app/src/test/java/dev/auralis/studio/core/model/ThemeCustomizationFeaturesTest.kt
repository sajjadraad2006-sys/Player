package dev.auralis.studio.core.model

import dev.auralis.studio.ui.components.MoodVibe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeCustomizationFeaturesTest {

    @Test
    fun `theme settings default values are sound and complete`() {
        val settings = ThemeSettings()
        assertEquals(LyricsDisplayStyle.APPLE_MUSIC_BLUR, settings.lyricsStyle)
        assertEquals(LyricsTextSize.STANDARD, settings.lyricsTextSize)
        assertEquals(PlayerVisualStyle.MODERN_GLASS, settings.playerStyle)
        assertEquals(SeekbarStyle.WAVY, settings.seekbarStyle)
        assertEquals(NavigationBarStyle.LIQUID_BUBBLE, settings.navBarStyle)
        assertTrue(settings.lyricsGlowActive)
        assertTrue(settings.lyricsBlurInactive)
        assertTrue(settings.lyricsWordByWordAnimation)
    }

    @Test
    fun `all lyrics display styles exist and are distinct`() {
        val styles = LyricsDisplayStyle.entries
        assertEquals(4, styles.size)
        assertTrue(styles.contains(LyricsDisplayStyle.APPLE_MUSIC_BLUR))
        assertTrue(styles.contains(LyricsDisplayStyle.KARAOKE_GLOW))
        assertTrue(styles.contains(LyricsDisplayStyle.DYNAMIC_CARD))
        assertTrue(styles.contains(LyricsDisplayStyle.CLASSIC_CLEAN))
    }

    @Test
    fun `all player visual styles exist and are distinct`() {
        val styles = PlayerVisualStyle.entries
        assertEquals(4, styles.size)
        assertTrue(styles.contains(PlayerVisualStyle.MODERN_GLASS))
        assertTrue(styles.contains(PlayerVisualStyle.WAVEFORM_HERO))
        assertTrue(styles.contains(PlayerVisualStyle.VINYL_STUDIO))
        assertTrue(styles.contains(PlayerVisualStyle.CINEMATIC_BLUR))
    }

    @Test
    fun `all seekbar styles exist and are distinct`() {
        val styles = SeekbarStyle.entries
        assertEquals(3, styles.size)
        assertTrue(styles.contains(SeekbarStyle.WAVY))
        assertTrue(styles.contains(SeekbarStyle.WAVEFORM_BAR))
        assertTrue(styles.contains(SeekbarStyle.MINIMAL_LINE))
    }

    @Test
    fun `all navigation bar styles exist and are distinct`() {
        val styles = NavigationBarStyle.entries
        assertEquals(2, styles.size)
        assertTrue(styles.contains(NavigationBarStyle.LIQUID_BUBBLE))
        assertTrue(styles.contains(NavigationBarStyle.CLASSIC_DOCK))
    }

    @Test
    fun `mood vibe matches track keywords accurately`() {
        val danceTrack = Track(
            id = 1L,
            title = "Summer Party Dance Remix",
            artist = "DJ Pulse",
            album = "Club Anthems",
            durationMs = 210_000L,
            uri = "content://media/1",
        )
        val chillTrack = Track(
            id = 2L,
            title = "Midnight Ambient Rain",
            artist = "Lofi Dreams",
            album = "Relaxing Waves",
            durationMs = 180_000L,
            uri = "content://media/2",
        )
        val acousticTrack = Track(
            id = 3L,
            title = "Acoustic Guitar Memories",
            artist = "Strings",
            album = "Unplugged Sessions",
            durationMs = 195_000L,
            uri = "content://media/3",
        )

        assertTrue(MoodVibe.ENERGIZE.matchesTrack(danceTrack))
        assertFalse(MoodVibe.ENERGIZE.matchesTrack(chillTrack))

        assertTrue(MoodVibe.CHILL.matchesTrack(chillTrack))
        assertTrue(MoodVibe.ACOUSTIC.matchesTrack(acousticTrack))
        assertTrue(MoodVibe.LATE_NIGHT.matchesTrack(chillTrack))
    }

    @Test
    fun `liquid navigation bar slot index accounts for RTL mirroring`() {
        val itemCount = 5
        fun computeSlot(selectedIndex: Int, isRtl: Boolean): Int {
            return if (isRtl && itemCount > 0) (itemCount - 1 - selectedIndex).coerceAtLeast(0) else selectedIndex
        }

        // In LTR, Home (0) stays at slot 0, Settings (4) stays at slot 4
        assertEquals(0, computeSlot(0, isRtl = false))
        assertEquals(2, computeSlot(2, isRtl = false))
        assertEquals(4, computeSlot(4, isRtl = false))

        // In RTL, Home (0) mirrors to physical slot 4, Settings (4) mirrors to physical slot 0
        assertEquals(4, computeSlot(0, isRtl = true))
        assertEquals(2, computeSlot(2, isRtl = true))
        assertEquals(0, computeSlot(4, isRtl = true))
    }

    @Test
    fun `lyrics line without word timestamps generates monotonically increasing synthetic words`() {
        val lineText = "Every night in my dreams I see you"
        val lineStartMs = 10_000L
        val lineDurationMs = 5_000L
        val rawTokens = lineText.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        val totalChars = rawTokens.sumOf { it.length }.coerceAtLeast(1)

        var runningStart = lineStartMs
        val syntheticWords = rawTokens.mapIndexed { wIdx, token ->
            val wDur = if (wIdx == rawTokens.lastIndex) {
                (lineStartMs + lineDurationMs - runningStart).coerceAtLeast(120L)
            } else {
                (token.length.toFloat() / totalChars * lineDurationMs).toLong().coerceAtLeast(120L)
            }
            val wStart = runningStart
            val wEnd = wStart + wDur
            runningStart = wEnd
            val separator = if (wIdx < rawTokens.lastIndex) " " else ""
            LyricsWord(startTimeMs = wStart, endTimeMs = wEnd, text = token + separator)
        }

        assertEquals(8, syntheticWords.size)
        assertTrue(syntheticWords.first().startTimeMs >= lineStartMs)
        for (i in 0 until syntheticWords.size - 1) {
            assertTrue(
                "Word $i end (${syntheticWords[i].endTimeMs}) should equal next word start (${syntheticWords[i + 1].startTimeMs})",
                syntheticWords[i].endTimeMs == syntheticWords[i + 1].startTimeMs,
            )
            assertTrue(syntheticWords[i].text.endsWith(" "))
        }
        assertFalse(syntheticWords.last().text.endsWith(" "))
        assertTrue(syntheticWords.last().endTimeMs >= lineStartMs + lineDurationMs)
    }

    @Test
    fun `seekbar fraction calculation handles edge boundaries gracefully`() {
        val range = 0f..240_000f
        val rangeSpan = (range.endInclusive - range.start).coerceAtLeast(0.001f)

        fun fraction(value: Float): Float = ((value - range.start) / rangeSpan).coerceIn(0f, 1f)

        assertEquals(0f, fraction(0f), 0.0001f)
        assertEquals(0.5f, fraction(120_000f), 0.0001f)
        assertEquals(1f, fraction(240_000f), 0.0001f)
        assertEquals(0f, fraction(-500f), 0.0001f)
        assertEquals(1f, fraction(300_000f), 0.0001f)
    }
}
