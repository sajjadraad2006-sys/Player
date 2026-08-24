package dev.auralis.studio.audio.visualizer

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class VisualizerAnalyzerTest {
    @Test fun silenceProducesNearZeroBands() {
        val bands = analyzeTone(0f)
        assertTrue(bands.all { it < 0.01f })
    }

    @Test fun lowMidAndHighTonesDominateExpectedRegions() {
        val low = analyzeTone(100f).maxIndex()
        val mid = analyzeTone(1_000f).maxIndex()
        val high = analyzeTone(10_000f).maxIndex()
        assertTrue("low index=$low", low in 8..14)
        assertTrue("mid index=$mid", mid in 24..31)
        assertTrue("high index=$high", high in 40..47)
        assertTrue(low < mid && mid < high)
    }

    private fun analyzeTone(frequencyHz: Float): FloatArray {
        val tap = PcmAudioTap()
        tap.configure(48_000)
        repeat(8192) { index ->
            val sample = if (frequencyHz == 0f) 0f else (0.8 * sin(2.0 * PI * frequencyHz * index / 48_000.0)).toFloat()
            tap.write(sample)
        }
        val analyzer = VisualizerAnalyzer(tap)
        analyzer.analyzeFrame()
        return analyzer.bands.value
    }

    private fun FloatArray.maxIndex(): Int = indices.maxBy { this[it] }
}
