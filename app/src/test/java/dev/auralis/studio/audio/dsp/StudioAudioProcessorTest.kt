package dev.auralis.studio.audio.dsp

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import dev.auralis.studio.core.model.AudioConfig
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class StudioAudioProcessorTest {
    @Test
    fun bypassReturnsPcmUnchanged() {
        val processor = configuredProcessor(AudioConfig(dspEnabled = false))
        val samples = shortArrayOf(0, 1_000, -1_000, 20_000, -20_000, 32_000, -32_000, 0)
        val output = process(processor, samples)
        assertArrayEquals(samples, output)
    }

    @Test
    fun limiterContainsAggressivelyAmplifiedSignal() {
        val processor = configuredProcessor(AudioConfig(preampDb = 6f, bassDb = 12f, trebleDb = 12f, limiterEnabled = true))
        val samples = ShortArray(4_096) { if (it % 2 == 0) 30_000 else -30_000 }
        val output = process(processor, samples)
        val peak = output.maxOf { kotlin.math.abs(it.toInt()) }
        assertTrue("Limiter peak was $peak", peak <= 32_113)
    }

    private fun configuredProcessor(config: AudioConfig): StudioAudioProcessor = StudioAudioProcessor().apply {
        update(config)
        configure(AudioFormat(48_000, 2, C.ENCODING_PCM_16BIT))
        flush()
    }

    private fun process(processor: StudioAudioProcessor, samples: ShortArray): ShortArray {
        val input = ByteBuffer.allocateDirect(samples.size * 2).order(ByteOrder.nativeOrder())
        samples.forEach(input::putShort)
        input.flip()
        processor.queueInput(input)
        val output = processor.output.order(ByteOrder.nativeOrder())
        return ShortArray(output.remaining() / 2) { output.short }
    }
}
