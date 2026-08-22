package dev.auralis.studio.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioPresetTest {
    @Test
    fun builtInPresetsStayInsideSupportedRealtimeRanges() {
        BuiltInPresets.all.forEach { preset ->
            assertTrue(preset.config.speed in 0.25f..3f)
            assertTrue(preset.config.pitchSemitones in -12f..12f)
            assertTrue(preset.config.reverbMix in 0f..0.6f)
            assertTrue(preset.config.eightDStrength in 0f..1f)
            assertEquals(10, preset.config.eqBandsDb.size)
        }
    }

    @Test
    fun purePlaybackResetsAndBypassesEverything() {
        val pure = BuiltInPresets.all.last().config.pure()
        assertEquals(false, pure.dspEnabled)
        assertEquals(0f, pure.pitchSemitones, 0f)
        assertEquals(1f, pure.speed, 0f)
        assertEquals(false, pure.equalizerEnabled)
        assertEquals(false, pure.eightDEnabled)
        assertEquals(0f, pure.reverbMix, 0f)
    }
}
