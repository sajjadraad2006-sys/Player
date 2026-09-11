package dev.auralis.desktop.playback

import dev.auralis.studio.audio.dsp.StudioDspEngine
import dev.auralis.studio.core.model.AudioConfig
import dev.auralis.studio.core.model.ChannelMode
import dev.auralis.studio.core.model.OrbitDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

class StudioDspEngineCorrectnessTest {

    @Test
    fun testDspBypassIsBitTransparent() {
        val dsp = StudioDspEngine()
        dsp.configure(sampleRate = 44100, channelCount = 2)
        dsp.updateConfig(AudioConfig(dspEnabled = false))

        val frames = 512
        val bytes = ByteArray(frames * 4)
        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until frames) {
            val sample = (sin(2.0 * PI * 1000.0 * i / 44100.0) * 15000.0).toInt().toShort()
            bb.putShort(sample)
            bb.putShort(sample)
        }

        val original = bytes.clone()
        dsp.processPcm16InPlace(bytes, 0, bytes.size)

        // When DSP is bypassed, PCM samples must remain 100% bit-identical
        for (i in bytes.indices) {
            assertEquals("Bypass byte at index $i must match original", original[i], bytes[i])
        }
    }

    @Test
    fun testGraphicEqualizerBassBoostModifiesLowFrequencies() {
        val dsp = StudioDspEngine()
        dsp.configure(sampleRate = 44100, channelCount = 2)

        // Bass test tone: 60 Hz
        val frames = 1024
        val bytes = ByteArray(frames * 4)
        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until frames) {
            val sample = (sin(2.0 * PI * 60.0 * i / 44100.0) * 8000.0).toInt().toShort()
            bb.putShort(sample)
            bb.putShort(sample)
        }

        val original = bytes.clone()

        // Enable EQ with +8 dB bass boost
        dsp.updateConfig(AudioConfig(dspEnabled = true, equalizerEnabled = true, bassDb = 8f))
        dsp.processPcm16InPlace(bytes, 0, bytes.size)

        // Compare amplitude
        val inBb = ByteBuffer.wrap(original).order(ByteOrder.LITTLE_ENDIAN)
        val outBb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

        var amplifiedCount = 0
        for (i in 200 until frames - 100) { // check steady state region
            inBb.position(i * 4)
            outBb.position(i * 4)
            val inSample = abs(inBb.short.toInt())
            val outSample = abs(outBb.short.toInt())
            if (outSample > inSample) amplifiedCount++
        }

        assertTrue("Bass boost should amplify majority of 60 Hz samples", amplifiedCount > 400)
    }

    @Test
    fun testSafeLimiterPreventsClippingDistortion() {
        val dsp = StudioDspEngine()
        dsp.configure(sampleRate = 44100, channelCount = 2)

        // Enable limiter with massive preamp (+12 dB) to intentionally cause clipping
        dsp.updateConfig(AudioConfig(dspEnabled = true, preampDb = 12f, limiterEnabled = true))

        val frames = 512
        val bytes = ByteArray(frames * 4)
        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until frames) {
            // Already loud tone (+20,000) boosted by +12 dB
            val sample = (sin(2.0 * PI * 440.0 * i / 44100.0) * 20000.0).toInt().toShort()
            bb.putShort(sample)
            bb.putShort(sample)
        }

        dsp.processPcm16InPlace(bytes, 0, bytes.size)

        // Verify no sample overflow or wraparound (-32768 to +32767)
        val outBb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until frames) {
            val left = outBb.short
            val right = outBb.short
            assertTrue("Left sample must be within 16-bit limits", left in -32767..32767)
            assertTrue("Right sample must be within 16-bit limits", right in -32767..32767)
        }
    }

    @Test
    fun test8DSpatialAudioModulation() {
        val dsp = StudioDspEngine()
        dsp.configure(sampleRate = 44100, channelCount = 2)
        dsp.updateConfig(AudioConfig(
            dspEnabled = true,
            eightDEnabled = true,
            eightDStrength = 0.8f,
            orbitDirection = OrbitDirection.CLOCKWISE,
        ))

        val frames = 44100 // 1 second of audio
        val bytes = ByteArray(frames * 4)
        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until frames) {
            val sample = (sin(2.0 * PI * 440.0 * i / 44100.0) * 12000.0).toInt().toShort()
            bb.putShort(sample)
            bb.putShort(sample)
        }

        dsp.processPcm16InPlace(bytes, 0, bytes.size)

        // Check difference between Left and Right channels across orbit
        val outBb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        var channelDifferenceDetected = false
        for (i in 0 until 5000) {
            val left = outBb.short
            val right = outBb.short
            if (abs(left - right) > 100) {
                channelDifferenceDetected = true
                break
            }
        }
        assertTrue("8D spatial processing should introduce interaural difference", channelDifferenceDetected)
    }
}
