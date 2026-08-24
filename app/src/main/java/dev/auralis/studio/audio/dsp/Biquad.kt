package dev.auralis.studio.audio.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

internal class Biquad {
    private var b0 = 1.0
    private var b1 = 0.0
    private var b2 = 0.0
    private var a1 = 0.0
    private var a2 = 0.0
    private var z1 = 0.0
    private var z2 = 0.0

    fun setPeaking(sampleRate: Int, frequency: Float, gainDb: Float, q: Float = 1f) {
        val safeFrequency = frequency.coerceIn(20f, sampleRate * 0.45f).toDouble()
        val a = 10.0.pow(gainDb / 40.0)
        val omega = 2.0 * PI * safeFrequency / sampleRate
        val alpha = sin(omega) / (2.0 * q.coerceAtLeast(0.1f))
        val cosOmega = cos(omega)
        val rawB0 = 1.0 + alpha * a
        val rawB1 = -2.0 * cosOmega
        val rawB2 = 1.0 - alpha * a
        val rawA0 = 1.0 + alpha / a
        val rawA1 = -2.0 * cosOmega
        val rawA2 = 1.0 - alpha / a
        b0 = rawB0 / rawA0
        b1 = rawB1 / rawA0
        b2 = rawB2 / rawA0
        a1 = rawA1 / rawA0
        a2 = rawA2 / rawA0
    }

    fun setLowPass(sampleRate: Int, cutoffFreq: Float, q: Float = 0.7071f) {
        val safeFrequency = cutoffFreq.coerceIn(20f, sampleRate * 0.45f).toDouble()
        val omega = 2.0 * PI * safeFrequency / sampleRate
        val cosOmega = cos(omega)
        val alpha = sin(omega) / (2.0 * q.coerceAtLeast(0.1f))
        val rawB0 = (1.0 - cosOmega) / 2.0
        val rawB1 = 1.0 - cosOmega
        val rawB2 = (1.0 - cosOmega) / 2.0
        val rawA0 = 1.0 + alpha
        val rawA1 = -2.0 * cosOmega
        val rawA2 = 1.0 - alpha
        b0 = rawB0 / rawA0
        b1 = rawB1 / rawA0
        b2 = rawB2 / rawA0
        a1 = rawA1 / rawA0
        a2 = rawA2 / rawA0
    }

    fun setHighPass(sampleRate: Int, cutoffFreq: Float, q: Float = 0.7071f) {
        val safeFrequency = cutoffFreq.coerceIn(20f, sampleRate * 0.45f).toDouble()
        val omega = 2.0 * PI * safeFrequency / sampleRate
        val cosOmega = cos(omega)
        val alpha = sin(omega) / (2.0 * q.coerceAtLeast(0.1f))
        val rawB0 = (1.0 + cosOmega) / 2.0
        val rawB1 = -(1.0 + cosOmega)
        val rawB2 = (1.0 + cosOmega) / 2.0
        val rawA0 = 1.0 + alpha
        val rawA1 = -2.0 * cosOmega
        val rawA2 = 1.0 - alpha
        b0 = rawB0 / rawA0
        b1 = rawB1 / rawA0
        b2 = rawB2 / rawA0
        a1 = rawA1 / rawA0
        a2 = rawA2 / rawA0
    }

    fun setBandPass(sampleRate: Int, centerFreq: Float, q: Float = 1.0f) {
        val safeFrequency = centerFreq.coerceIn(20f, sampleRate * 0.45f).toDouble()
        val omega = 2.0 * PI * safeFrequency / sampleRate
        val cosOmega = cos(omega)
        val alpha = sin(omega) / (2.0 * q.coerceAtLeast(0.1f))
        val rawB0 = alpha
        val rawB1 = 0.0
        val rawB2 = -alpha
        val rawA0 = 1.0 + alpha
        val rawA1 = -2.0 * cosOmega
        val rawA2 = 1.0 - alpha
        b0 = rawB0 / rawA0
        b1 = rawB1 / rawA0
        b2 = rawB2 / rawA0
        a1 = rawA1 / rawA0
        a2 = rawA2 / rawA0
    }

    fun process(input: Float): Float {
        val output = b0 * input + z1
        z1 = b1 * input - a1 * output + z2
        z2 = b2 * input - a2 * output
        return output.toFloat()
    }

    fun reset() {
        z1 = 0.0
        z2 = 0.0
    }
}
