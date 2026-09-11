package dev.auralis.studio.audio.dsp

import dev.auralis.studio.audio.visualizer.PcmAudioTap
import dev.auralis.studio.core.model.AudioConfig
import dev.auralis.studio.core.model.ChannelMode
import dev.auralis.studio.core.model.OrbitDirection
import dev.auralis.studio.core.model.OrbitMath
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Pure Kotlin PCM audio DSP engine shared between Android and Windows Desktop.
 * Processes 16-bit stereo PCM samples with zero allocations in the hot audio processing loop.
 *
 * Pipeline Order:
 * Preamp -> Graphic EQ / Bass / Treble -> Channel Matrix -> Stereo Width / Balance
 * -> 8D Spatial Orbit -> Reverb -> Limiter -> Visualizer PcmAudioTap tap.
 */
class StudioDspEngine(
    val pcmTap: PcmAudioTap = PcmAudioTap(),
) {
    private var activeConfig = AudioConfig()
    private var sampleRate = 44_100
    private var channelCount = 2
    private var phase = 0.0
    private var smoothedPan = 0f
    private var smoothedWet = 0f
    private var leftFilters = emptyList<Biquad>()
    private var rightFilters = emptyList<Biquad>()
    private var filterSignature = 0
    private var leftDelay = FloatArray(1)
    private var rightDelay = FloatArray(1)
    private var delayIndex = 0

    fun configure(sampleRate: Int, channelCount: Int = 2) {
        this.sampleRate = sampleRate.coerceAtLeast(8000)
        this.channelCount = channelCount.coerceIn(1, 8)
        pcmTap.configure(this.sampleRate)
        rebuildDelays()
        rebuildFilters(activeConfig, force = true)
    }

    fun updateConfig(config: AudioConfig) {
        activeConfig = config
        rebuildFilters(config)
    }

    fun reset() {
        phase = 0.0
        smoothedPan = 0f
        smoothedWet = 0f
        leftFilters.forEach(Biquad::reset)
        rightFilters.forEach(Biquad::reset)
        leftDelay.fill(0f)
        rightDelay.fill(0f)
        delayIndex = 0
        pcmTap.clear()
    }

    /**
     * Processes interleaved 16-bit PCM bytes in-place or from [input] to [output].
     * Avoids all object allocations in the hot loop.
     *
     * @param buffer byte array containing 16-bit little-endian PCM samples
     * @param offset start offset in [buffer]
     * @param length number of valid bytes in [buffer]
     */
    fun processPcm16InPlace(buffer: ByteArray, offset: Int, length: Int) {
        val frameBytes = channelCount * 2
        val config = activeConfig

        if (!config.dspEnabled) {
            // DSP bypassed: feed visualizer tap and return
            var pos = offset
            val end = offset + length
            while (pos + frameBytes <= end) {
                val leftSample = (buffer[pos].toInt() and 0xFF) or (buffer[pos + 1].toInt() shl 8)
                val left = leftSample.toShort() / 32768f
                val right = if (channelCount > 1) {
                    val rightSample = (buffer[pos + 2].toInt() and 0xFF) or (buffer[pos + 3].toInt() shl 8)
                    rightSample.toShort() / 32768f
                } else left
                pcmTap.write((left + right) * 0.5f)
                pos += frameBytes
            }
            return
        }

        var pos = offset
        val end = offset + length
        while (pos + frameBytes <= end) {
            // Read 16-bit signed little-endian PCM
            val leftSample = (buffer[pos].toInt() and 0xFF) or (buffer[pos + 1].toInt() shl 8)
            var left = leftSample.toShort() / 32768f

            var right = if (channelCount > 1) {
                val rightSample = (buffer[pos + 2].toInt() and 0xFF) or (buffer[pos + 3].toInt() shl 8)
                rightSample.toShort() / 32768f
            } else {
                left
            }

            // Preamp Gain
            val gain = 10f.pow(config.preampDb / 20f)
            left *= gain
            right *= gain

            // Graphic Equalizer & Tone
            if (config.equalizerEnabled || config.bassDb != 0f || config.trebleDb != 0f) {
                for (i in leftFilters.indices) {
                    left = leftFilters[i].process(left)
                }
                for (i in rightFilters.indices) {
                    right = rightFilters[i].process(right)
                }
            }

            // Channel Mode Matrix
            val (mappedLeft, mappedRight) = applyChannelMode(left, right, config.channelMode)
            left = mappedLeft
            right = mappedRight

            // Stereo Width
            val mid = (left + right) * 0.5f
            val side = (left - right) * 0.5f * config.stereoWidth
            left = mid + side
            right = mid - side

            // Balance
            val balanceLeft = if (config.balance > 0f) 1f - config.balance else 1f
            val balanceRight = if (config.balance < 0f) 1f + config.balance else 1f
            left *= balanceLeft
            right *= balanceRight

            // 8D Audio
            if (config.eightDEnabled && channelCount > 1 && config.eightDStrength > 0.0001f) {
                phase = OrbitMath.advance(phase, config.eightDRateHz, sampleRate)
                val strength = config.eightDStrength.coerceIn(0f, 1f)
                val targetPan = OrbitMath.pan(phase, config.orbitDirection) * strength
                smoothedPan += (targetPan - smoothedPan) * 0.0015f
                val leftPan = sqrt(((1f - smoothedPan) * 0.5f).coerceIn(0f, 1f)) * 1.4142f
                val rightPan = sqrt(((1f + smoothedPan) * 0.5f).coerceIn(0f, 1f)) * 1.4142f
                val crossfeed = if (config.orbitDirection == OrbitDirection.PING_PONG) {
                    0f
                } else {
                    (0.018f + 0.018f * ((cos(phase) + 1.0) * 0.5).toFloat()) * strength
                }
                val originalLeft = left
                left = (left * leftPan) + (right * crossfeed)
                right = (right * rightPan) + (originalLeft * crossfeed)
            } else {
                smoothedPan = 0f
            }

            // Reverb
            smoothedWet += (config.reverbMix - smoothedWet) * 0.0008f
            if (smoothedWet > 0.0001f) {
                val delayedLeft = leftDelay[delayIndex]
                val delayedRight = rightDelay[delayIndex]
                leftDelay[delayIndex] = (left + delayedRight * 0.52f).coerceIn(-1.2f, 1.2f)
                rightDelay[delayIndex] = (right + delayedLeft * 0.49f).coerceIn(-1.2f, 1.2f)
                left = left * (1f - smoothedWet) + delayedLeft * smoothedWet
                right = right * (1f - smoothedWet) + delayedRight * smoothedWet
                delayIndex = (delayIndex + 1) % leftDelay.size
            }

            // Safe Limiter
            if (config.limiterEnabled) {
                left = softLimit(left)
                right = softLimit(right)
            }

            // Convert back to 16-bit signed PCM
            val outLeftShort = (left.coerceIn(-1f, 1f) * 32767f).toInt().toShort()
            buffer[pos] = (outLeftShort.toInt() and 0xFF).toByte()
            buffer[pos + 1] = ((outLeftShort.toInt() shr 8) and 0xFF).toByte()

            if (channelCount > 1) {
                val outRightShort = (right.coerceIn(-1f, 1f) * 32767f).toInt().toShort()
                buffer[pos + 2] = (outRightShort.toInt() and 0xFF).toByte()
                buffer[pos + 3] = ((outRightShort.toInt() shr 8) and 0xFF).toByte()
            }

            // Feed visualizer tap (mean of stereo channels)
            pcmTap.write((left + right) * 0.5f)

            pos += frameBytes
        }
    }

    private fun rebuildFilters(config: AudioConfig, force: Boolean = false) {
        val signature = 31 * config.eqBandsDb.hashCode() + 17 * config.bassDb.hashCode() + config.trebleDb.hashCode() + sampleRate
        if (!force && signature == filterSignature) return
        filterSignature = signature
        val frequencies = listOf(31f, 62f, 125f, 250f, 500f, 1000f, 2000f, 4000f, 8000f, 16000f)
        val gains = config.eqBandsDb.toMutableList()
        if (gains.size < 10) {
            while (gains.size < 10) gains.add(0f)
        }
        gains[1] = (gains[1] + config.bassDb).coerceIn(-18f, 18f)
        gains[8] = (gains[8] + config.trebleDb).coerceIn(-18f, 18f)
        leftFilters = frequencies.mapIndexed { index, frequency ->
            Biquad().apply { setPeaking(sampleRate, frequency, gains[index], 1.15f) }
        }
        rightFilters = frequencies.mapIndexed { index, frequency ->
            Biquad().apply { setPeaking(sampleRate, frequency, gains[index], 1.15f) }
        }
    }

    private fun rebuildDelays() {
        val delayFrames = (sampleRate * 0.043f).toInt().coerceAtLeast(1)
        leftDelay = FloatArray(delayFrames)
        rightDelay = FloatArray(delayFrames)
        delayIndex = 0
    }

    private fun applyChannelMode(left: Float, right: Float, mode: ChannelMode): Pair<Float, Float> = when (mode) {
        ChannelMode.STEREO -> left to right
        ChannelMode.MONO -> ((left + right) * 0.5f).let { it to it }
        ChannelMode.LEFT -> left to left
        ChannelMode.RIGHT -> right to right
        ChannelMode.SWAP -> right to left
    }

    private fun softLimit(value: Float): Float {
        val absolute = abs(value)
        if (absolute <= 0.88f) return value
        val compressed = 0.88f + (absolute - 0.88f) * 0.14f
        return if (value < 0f) -compressed.coerceAtMost(0.98f) else compressed.coerceAtMost(0.98f)
    }
}
