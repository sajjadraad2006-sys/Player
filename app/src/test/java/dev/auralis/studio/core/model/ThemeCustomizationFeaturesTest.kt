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
}
