package dev.auralis.studio.audio.stems

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class StemMixerTest {

    private val mixer = StemMixerProcessor()

    @Test
    fun mix_vocalsOnly_producesVocals() {
        val frameCount = 1000
        val vocals = ShortArray(frameCount * 2) { 10000 }
        val inst = ShortArray(frameCount * 2) { 5000 }
        val output = ByteArray(frameCount * 4)

        val gains = StemMixGains(vocals = 1.0f, instrumental = 0.0f, bass = 0.0f)
        // Run once to let gain ramp converge
        repeat(5) {
            mixer.mixFrames(vocals, inst, null, gains, output, 0, frameCount)
        }

        val lastFrameIdx = (frameCount - 1) * 4
        val left = (output[lastFrameIdx].toInt() and 0xFF) or ((output[lastFrameIdx + 1].toInt()) shl 8)
        val right = (output[lastFrameIdx + 2].toInt() and 0xFF) or ((output[lastFrameIdx + 3].toInt()) shl 8)

        assertEquals("Left sample should match vocals", 10000.toShort(), left.toShort())
        assertEquals("Right sample should match vocals", 10000.toShort(), right.toShort())
    }

    @Test
    fun mix_instrumentalOnly_producesInstrumental() {
        val frameCount = 1000
        val vocals = ShortArray(frameCount * 2) { 8000 }
        val inst = ShortArray(frameCount * 2) { 12000 }
        val output = ByteArray(frameCount * 4)

        val gains = StemMixGains(vocals = 0.0f, instrumental = 1.0f, bass = 0.0f)
        repeat(5) {
            mixer.mixFrames(vocals, inst, null, gains, output, 0, frameCount)
        }

        val lastFrameIdx = (frameCount - 1) * 4
        val left = (output[lastFrameIdx].toInt() and 0xFF) or ((output[lastFrameIdx + 1].toInt()) shl 8)
        val right = (output[lastFrameIdx + 2].toInt() and 0xFF) or ((output[lastFrameIdx + 3].toInt()) shl 8)

        assertEquals("Left sample should match instrumental", 12000.toShort(), left.toShort())
        assertEquals("Right sample should match instrumental", 12000.toShort(), right.toShort())
    }

    @Test
    fun mix_bothFull_producesExactSumWithoutColoration() {
        val frameCount = 1000
        val vocals = ShortArray(frameCount * 2) { 7000 }
        val inst = ShortArray(frameCount * 2) { 9000 }
        val output = ByteArray(frameCount * 4)

        val gains = StemMixGains(vocals = 1.0f, instrumental = 1.0f, bass = 0.0f)
        repeat(5) {
            mixer.mixFrames(vocals, inst, null, gains, output, 0, frameCount)
        }

        val lastFrameIdx = (frameCount - 1) * 4
        val left = (output[lastFrameIdx].toInt() and 0xFF) or ((output[lastFrameIdx + 1].toInt()) shl 8)
        val right = (output[lastFrameIdx + 2].toInt() and 0xFF) or ((output[lastFrameIdx + 3].toInt()) shl 8)

        val expected = (7000 + 9000).toShort()
        assertEquals("Exact sum 16000 with zero coloring", expected, left.toShort())
        assertEquals("Exact sum 16000 with zero coloring", expected, right.toShort())
    }

    @Test
    fun mix_bothZero_producesSilence() {
        val frameCount = 1000
        val vocals = ShortArray(frameCount * 2) { 15000 }
        val inst = ShortArray(frameCount * 2) { 15000 }
        val output = ByteArray(frameCount * 4)

        val gains = StemMixGains(vocals = 0.0f, instrumental = 0.0f, bass = 0.0f)
        repeat(5) {
            mixer.mixFrames(vocals, inst, null, gains, output, 0, frameCount)
        }

        val lastFrameIdx = (frameCount - 1) * 4
        val left = (output[lastFrameIdx].toInt() and 0xFF) or ((output[lastFrameIdx + 1].toInt()) shl 8)
        val right = (output[lastFrameIdx + 2].toInt() and 0xFF) or ((output[lastFrameIdx + 3].toInt()) shl 8)

        assertEquals("Zero gain produces silence", 0.toShort(), left.toShort())
        assertEquals("Zero gain produces silence", 0.toShort(), right.toShort())
    }

    @Test
    fun mix_outOfRangeSignal_appliesSmoothSaturationProtection() {
        // If combined signal exceeds 32767, ensure it doesn't overflow / wrap around negatively
        val sampleHigh = mixer.sanitizeAndClampSample(50000f)
        assertTrue("High sample must be positive and <= 32767", sampleHigh in 25000..32767)

        val sampleLow = mixer.sanitizeAndClampSample(-50000f)
        assertTrue("Low sample must be negative and >= -32768", sampleLow in -32768..-25000)
    }

    @Test
    fun mix_quantitativeReconstruction_matchesOriginalWithInfiniteSnr() {
        val frameCount = 10000
        val original = ShortArray(frameCount * 2) { i ->
            ((kotlin.math.sin(i.toDouble() * 0.05) * 15000.0) + (kotlin.math.cos(i.toDouble() * 0.12) * 5000.0)).toInt().toShort()
        }
        val vocals = ShortArray(frameCount * 2) { i ->
            (original[i] * 0.6f).toInt().toShort()
        }
        val inst = ShortArray(frameCount * 2) { i ->
            (original[i] - vocals[i]).toShort()
        }
        val output = ByteArray(frameCount * 4)

        val gains = StemMixGains(vocals = 1.0f, instrumental = 1.0f, bass = 0.0f)
        // Let filter settle
        repeat(5) {
            mixer.mixFrames(vocals, inst, null, gains, output, 0, frameCount)
        }

        // Measure steady-state reconstruction over last 5000 frames
        val startFrame = 5000
        var maxError = 0
        var sumAbsError = 0.0
        var sumSqError = 0.0
        var sumSqOrig = 0.0
        var exactMatches = 0
        val totalTestSamples = (frameCount - startFrame) * 2

        for (frame in startFrame until frameCount) {
            val byteIdx = frame * 4
            val mixedL = (output[byteIdx].toInt() and 0xFF) or (output[byteIdx + 1].toInt() shl 8)
            val mixedR = (output[byteIdx + 2].toInt() and 0xFF) or (output[byteIdx + 3].toInt() shl 8)

            val origL = original[frame * 2].toInt()
            val origR = original[frame * 2 + 1].toInt()

            val errL = kotlin.math.abs(origL - mixedL.toShort().toInt())
            val errR = kotlin.math.abs(origR - mixedR.toShort().toInt())

            if (errL == 0) exactMatches++
            if (errR == 0) exactMatches++

            maxError = maxOf(maxError, errL, errR)
            sumAbsError += errL + errR
            sumSqError += (errL * errL + errR * errR).toDouble()
            sumSqOrig += (origL * origL + origR * origR).toDouble()
        }

        val mae = sumAbsError / totalTestSamples
        val rms = kotlin.math.sqrt(sumSqError / totalTestSamples)
        val snrDb = if (sumSqError == 0.0) Double.POSITIVE_INFINITY else 10.0 * kotlin.math.log10(sumSqOrig / sumSqError)
        val exactPercent = (exactMatches.toDouble() / totalTestSamples) * 100.0

        assertEquals("Max reconstruction error must be 0", 0, maxError)
        assertEquals("MAE must be 0.0", 0.0, mae, 1e-9)
        assertEquals("RMS error must be 0.0", 0.0, rms, 1e-9)
        assertTrue("SNR must be infinite or > 120 dB", snrDb > 120.0 || snrDb.isInfinite())
        assertEquals("100% exact sample identity", 100.0, exactPercent, 1e-6)
    }

    @Test
    fun smoothing_timeConstant_isSampleRateInvariant() {
        val mixer44k = StemMixerProcessor(sampleRateHz = 44100, timeConstantSeconds = 0.010f)
        val mixer48k = StemMixerProcessor(sampleRateHz = 48000, timeConstantSeconds = 0.010f)
        val mixer96k = StemMixerProcessor(sampleRateHz = 96000, timeConstantSeconds = 0.010f)

        assertEquals("Tau at 44.1k is ~10ms", 10.0f, mixer44k.timeConstantMs, 0.2f)
        assertEquals("Tau at 48k is ~10ms", 10.0f, mixer48k.timeConstantMs, 0.2f)
        assertEquals("Tau at 96k is ~10ms", 10.0f, mixer96k.timeConstantMs, 0.2f)
    }
}

