package dev.auralis.studio.audio.visualizer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TemporalSmoothingTest {
    @Test fun `elapsed time gives equivalent result across refresh rates`() {
        fun simulate(fps: Int): Float {
            var value = 0f
            repeat(fps) { value = TemporalSmoothing.next(value, 1f, 1f / fps, 0.05f, 0.16f) }
            return value
        }
        assertEquals(simulate(60), simulate(120), 0.002f)
        assertEquals(simulate(60), simulate(90), 0.002f)
    }

    @Test fun `attack is faster than release and zero stays zero`() {
        val attack = TemporalSmoothing.next(0f, 1f, 0.016f, 0.05f, 0.16f)
        val releaseDrop = 1f - TemporalSmoothing.next(1f, 0f, 0.016f, 0.05f, 0.16f)
        assertTrue(attack > releaseDrop)
        assertEquals(0f, TemporalSmoothing.next(0f, 0f, 0.016f, 0.05f, 0.16f), 0f)
    }
}
