package dev.auralis.studio.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class PitchMathTest {
    @Test
    fun canonicalSemitoneConversionsAreExactWithinFloatTolerance() {
        assertEquals(1f, PitchMath.semitonesToFactor(0f), 0.00001f)
        assertEquals(2f, PitchMath.semitonesToFactor(12f), 0.00001f)
        assertEquals(0.5f, PitchMath.semitonesToFactor(-12f), 0.00001f)
    }

    @Test
    fun factorRoundTripPreservesSemitones() {
        listOf(-12f, -7f, -2f, 0f, 3f, 7f, 12f).forEach { semitones ->
            assertEquals(semitones, PitchMath.factorToSemitones(PitchMath.semitonesToFactor(semitones)), 0.0001f)
        }
    }

    @Test
    fun vinylModeUsesMathematicallyLinkedSpeedAndPitch() {
        val slow = AudioConfig(speed = 0.75f, mode = PitchTempoMode.VINYL)
        val fast = AudioConfig(speed = 1.25f, mode = PitchTempoMode.VINYL)
        assertEquals(0.75f, slow.effectivePitchFactor, 0f)
        assertEquals(1.25f, fast.effectivePitchFactor, 0f)
        assertEquals(12f * kotlin.math.log2(0.75f), slow.effectivePitchSemitones, 0.0001f)
        assertEquals(12f * kotlin.math.log2(1.25f), fast.effectivePitchSemitones, 0.0001f)
    }

    @Test
    fun tempoLockAlwaysPreservesOriginalPitch() {
        listOf(0.25f, 0.75f, 1f, 1.25f, 3f).forEach { speed ->
            assertEquals(1f, AudioConfig(speed = speed, mode = PitchTempoMode.TEMPO_LOCK).effectivePitchFactor, 0f)
        }
    }
}
