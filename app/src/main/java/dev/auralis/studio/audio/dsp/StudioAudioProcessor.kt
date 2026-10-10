package dev.auralis.studio.audio.dsp

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.AudioProcessor.UnhandledAudioFormatException
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import dev.auralis.studio.core.model.AudioConfig
import dev.auralis.studio.core.model.ChannelMode
import dev.auralis.studio.core.model.OrbitDirection
import dev.auralis.studio.core.model.OrbitMath
import dev.auralis.studio.audio.visualizer.PcmAudioTap
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Low-allocation PCM processing for tone, stereo, spatial and dynamics effects.
 * Order:
 * Preamp → Graphic EQ → Stereo matrix → 8D orbit → Reverb → Safe limiter.
 */
@UnstableApi
class StudioAudioProcessor(
    private val pcmTap: PcmAudioTap = PcmAudioTap(),
) : BaseAudioProcessor() {
    private val requestedConfig = AtomicReference(AudioConfig())
    private var activeConfig = AudioConfig()
    private var sampleRate = 44_100
    private var channelCount = 2
    private var phase = 0.0
    private var smoothedPan = 0f
    private var smoothedBassPan = 0f
    private var smoothedSidePan = 0f
    private var headShadowL = 0f
    private var headShadowR = 0f
    private val crossoverLowL = Biquad()
    private val crossoverLowR = Biquad()
    private val crossoverHighL = Biquad()
    private val crossoverHighR = Biquad()
    private val itdBufferL = FloatArray(64)
    private val itdBufferR = FloatArray(64)
    private var itdIndex = 0
    private var smoothedWet = 0f
    private var leftFilters = emptyList<Biquad>()
    private var rightFilters = emptyList<Biquad>()
    private var filterSignature = 0
    private var leftDelay = FloatArray(1)
    private var rightDelay = FloatArray(1)
    private var delayIndex = 0

    fun update(config: AudioConfig) {
        requestedConfig.set(config)
    }

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            throw UnhandledAudioFormatException(inputAudioFormat)
        }
        sampleRate = inputAudioFormat.sampleRate
        channelCount = inputAudioFormat.channelCount
        pcmTap.configure(sampleRate)
        crossoverLowL.setLowPass(sampleRate, 220f, 0.7071f)
        crossoverLowR.setLowPass(sampleRate, 220f, 0.7071f)
        crossoverHighL.setHighPass(sampleRate, 3400f, 0.7071f)
        crossoverHighR.setHighPass(sampleRate, 3400f, 0.7071f)
        rebuildDelays()
        rebuildFilters(requestedConfig.get(), force = true)
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val bytes = inputBuffer.remaining()
        if (bytes == 0) return
        val output = replaceOutputBuffer(bytes).order(ByteOrder.nativeOrder())
        inputBuffer.order(ByteOrder.nativeOrder())
        val config = requestedConfig.get()
        activeConfig = config
        rebuildFilters(config)

        if (!config.dspEnabled) {
            // Even when DSP is bypassed, feed visualizer cheaply
            val savedPos = inputBuffer.position()
            val savedLimit = inputBuffer.limit()
            tapBypass(inputBuffer)
            inputBuffer.position(savedPos)
            inputBuffer.limit(savedLimit)
            output.put(inputBuffer)
            output.flip()
            return
        }

        val frameBytes = channelCount * 2
        while (inputBuffer.remaining() >= frameBytes) {
            var left = inputBuffer.short / 32768f
            var right = if (channelCount > 1) inputBuffer.short / 32768f else left

            val gain = 10f.pow(config.preampDb / 20f)
            left *= gain
            right *= gain

            // Equalizer
            if (config.equalizerEnabled || config.bassDb != 0f || config.trebleDb != 0f) {
                leftFilters.forEach { left = it.process(left) }
                rightFilters.forEach { right = it.process(right) }
            }

            val channelPair = applyChannelMode(left, right, config.channelMode)
            left = channelPair.first
            right = channelPair.second

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
                smoothedPan += (targetPan - smoothedPan) * 0.002f

                // Dynamic trajectories per mode
                val targetBassPan = when (config.eightDMode) {
                    dev.auralis.studio.core.model.EightDMode.BASS_SEPARATION ->
                        OrbitMath.bassPan(phase, config.orbitDirection, config.eightDBassSeparation) * strength
                    dev.auralis.studio.core.model.EightDMode.DYNAMIC_ORBIT ->
                        OrbitMath.bassPan(phase, config.orbitDirection, 0.22f) * strength
                    else -> targetPan
                }
                smoothedBassPan += (targetBassPan - smoothedBassPan) * 0.002f

                val targetSidePan = when (config.eightDMode) {
                    dev.auralis.studio.core.model.EightDMode.VOCAL_SEPARATION ->
                        OrbitMath.sidePan(phase, config.orbitDirection, config.eightDVocalSeparation) * strength
                    dev.auralis.studio.core.model.EightDMode.BASS_SEPARATION ->
                        OrbitMath.sidePan(phase, config.orbitDirection, 0.35f) * strength
                    else -> targetPan
                }
                smoothedSidePan += (targetSidePan - smoothedSidePan) * 0.002f

                // Crossover frequency decomposition
                val lowL = crossoverLowL.process(left)
                val lowR = crossoverLowR.process(right)
                val highL = crossoverHighL.process(left)
                val highR = crossoverHighR.process(right)
                val midBandL = left - lowL - highL
                val midBandR = right - lowR - highR

                // Mid/Side vocal extraction for dual-voice separation
                val midCenter = (midBandL + midBandR) * 0.5f
                val midSide = (midBandL - midBandR) * 0.5f

                // 1. Center / Primary Lead Vocal Panning
                val leftMidPan = sqrt(((1f - smoothedPan) * 0.5f).coerceIn(0f, 1f)) * 1.4142f
                val rightMidPan = sqrt(((1f + smoothedPan) * 0.5f).coerceIn(0f, 1f)) * 1.4142f
                val vMidL = midCenter * leftMidPan
                val vMidR = midCenter * rightMidPan

                // 2. Side Harmonies / Backing / Second Voice Panning
                val isVocalSep = config.eightDMode == dev.auralis.studio.core.model.EightDMode.VOCAL_SEPARATION
                val leftSidePan = sqrt(((1f - smoothedSidePan) * 0.5f).coerceIn(0f, 1f)) * 1.4142f
                val rightSidePan = sqrt(((1f + smoothedSidePan) * 0.5f).coerceIn(0f, 1f)) * 1.4142f
                val sepMult = if (isVocalSep) 1f + 0.22f * config.eightDVocalSeparation else 1f
                val vSideL = midSide * leftSidePan * sepMult
                val vSideR = -midSide * rightSidePan * sepMult

                // 3. Bass Panning with Asymmetric Right-Ear Weighting
                val isBassSep = config.eightDMode == dev.auralis.studio.core.model.EightDMode.BASS_SEPARATION
                val leftBassPan = sqrt(((1f - smoothedBassPan) * 0.5f).coerceIn(0f, 1f)) * 1.4142f
                val rightBassWeight = if (isBassSep) 1f + 0.16f * config.eightDBassSeparation else 1f
                val rightBassPan = sqrt(((1f + smoothedBassPan) * 0.5f).coerceIn(0f, 1f)) * 1.4142f * rightBassWeight
                val bL = lowL * leftBassPan
                val bR = lowR * rightBassPan

                // 4. Treble Panning
                val leftHighPan = sqrt(((1f - smoothedPan) * 0.5f).coerceIn(0f, 1f)) * 1.4142f
                val rightHighPan = sqrt(((1f + smoothedPan) * 0.5f).coerceIn(0f, 1f)) * 1.4142f
                val hL = highL * leftHighPan
                val hR = highR * rightHighPan

                // Sum all spatial bands
                left = bL + vMidL + vSideL + hL
                right = bR + vMidR + vSideR + hR

                // Dynamic 3D Crossfeed
                val crossfeed = if (config.orbitDirection == OrbitDirection.PING_PONG) {
                    0f
                } else {
                    (0.016f + 0.016f * ((cos(phase) + 1.0) * 0.5).toFloat()) * strength
                }
                val originalLeft = left
                left = (left) + (right * crossfeed)
                right = (right) + (originalLeft * crossfeed)

                // 5. Binaural HRTF Simulation (Head Shadowing + ITD micro-delays)
                val binauralDepth = config.eightDBinauralDepth.coerceIn(0f, 1f) * strength
                itdBufferL[itdIndex] = left
                itdBufferR[itdIndex] = right

                if (binauralDepth > 0.05f) {
                    val maxDelaySamples = 24
                    if (smoothedPan > 0.05f) {
                        val delay = (smoothedPan * maxDelaySamples * binauralDepth).toInt().coerceIn(0, 31)
                        val delayedIdx = (itdIndex - delay + 64) % 64
                        val delayedL = itdBufferL[delayedIdx]
                        val shadowAlpha = (1.0f - (smoothedPan * 0.48f * binauralDepth)).coerceIn(0.25f, 1.0f)
                        headShadowL += (delayedL - headShadowL) * shadowAlpha
                        left = headShadowL
                    } else if (smoothedPan < -0.05f) {
                        val delay = (-smoothedPan * maxDelaySamples * binauralDepth).toInt().coerceIn(0, 31)
                        val delayedIdx = (itdIndex - delay + 64) % 64
                        val delayedR = itdBufferR[delayedIdx]
                        val shadowAlpha = (1.0f - (-smoothedPan * 0.48f * binauralDepth)).coerceIn(0.25f, 1.0f)
                        headShadowR += (delayedR - headShadowR) * shadowAlpha
                        right = headShadowR
                    }
                }
                itdIndex = (itdIndex + 1) % 64
            } else {
                // Strength 0 is an actual bypass, including removal of residual pan.
                smoothedPan = 0f
                smoothedBassPan = 0f
                smoothedSidePan = 0f
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

            // Limiter
            if (config.limiterEnabled) {
                left = softLimit(left)
                right = softLimit(right)
            }

            output.putShort((left.coerceIn(-1f, 1f) * 32767f).toInt().toShort())
            if (channelCount > 1) output.putShort((right.coerceIn(-1f, 1f) * 32767f).toInt().toShort())
            repeat((channelCount - 2).coerceAtLeast(0)) { output.putShort(inputBuffer.short) }

            pcmTap.write((left + right) * 0.5f)
        }
        output.flip()
    }

    private fun tapBypass(buf: ByteBuffer) {
        val frameBytes = channelCount * 2
        val readBuf = buf.duplicate().order(ByteOrder.nativeOrder())
        while (readBuf.remaining() >= frameBytes) {
            val left = readBuf.short / 32768f
            val right = if (channelCount > 1) readBuf.short / 32768f else left
            repeat((channelCount - 2).coerceAtLeast(0)) { readBuf.short }
            pcmTap.write((left + right) * 0.5f)
        }
    }

    override fun onFlush() {
        phase = 0.0
        smoothedPan = 0f
        smoothedBassPan = 0f
        smoothedSidePan = 0f
        headShadowL = 0f
        headShadowR = 0f
        itdBufferL.fill(0f)
        itdBufferR.fill(0f)
        itdIndex = 0
        smoothedWet = 0f
        crossoverLowL.reset()
        crossoverLowR.reset()
        crossoverHighL.reset()
        crossoverHighR.reset()
        leftFilters.forEach(Biquad::reset)
        rightFilters.forEach(Biquad::reset)
        leftDelay.fill(0f)
        rightDelay.fill(0f)
        delayIndex = 0
    }

    override fun onReset() {
        leftFilters = emptyList()
        rightFilters = emptyList()
        leftDelay = FloatArray(1)
        rightDelay = FloatArray(1)
        itdBufferL.fill(0f)
        itdBufferR.fill(0f)
        itdIndex = 0
        crossoverLowL.reset()
        crossoverLowR.reset()
        crossoverHighL.reset()
        crossoverHighR.reset()
        pcmTap.clear()
    }

    private fun rebuildFilters(config: AudioConfig, force: Boolean = false) {
        val signature = 31 * config.eqBandsDb.hashCode() + 17 * config.bassDb.hashCode() + config.trebleDb.hashCode() + sampleRate
        if (!force && signature == filterSignature) return
        filterSignature = signature
        val frequencies = listOf(31f, 62f, 125f, 250f, 500f, 1000f, 2000f, 4000f, 8000f, 16000f)
        val gains = config.eqBandsDb.toMutableList()
        gains[1] = (gains[1] + config.bassDb).coerceIn(-18f, 18f)
        gains[8] = (gains[8] + config.trebleDb).coerceIn(-18f, 18f)
        leftFilters  = frequencies.mapIndexed { index, frequency -> Biquad().apply { setPeaking(sampleRate, frequency, gains[index], 1.15f) } }
        rightFilters = frequencies.mapIndexed { index, frequency -> Biquad().apply { setPeaking(sampleRate, frequency, gains[index], 1.15f) } }
    }

    private fun rebuildDelays() {
        val delayFrames = (sampleRate * 0.043f).toInt().coerceAtLeast(1)
        leftDelay  = FloatArray(delayFrames)
        rightDelay = FloatArray(delayFrames)
        delayIndex = 0
    }

    private fun applyChannelMode(left: Float, right: Float, mode: ChannelMode): Pair<Float, Float> = when (mode) {
        ChannelMode.STEREO -> left to right
        ChannelMode.MONO   -> ((left + right) * 0.5f).let { it to it }
        ChannelMode.LEFT   -> left to left
        ChannelMode.RIGHT  -> right to right
        ChannelMode.SWAP   -> right to left
    }

    private fun softLimit(value: Float): Float {
        val absolute = abs(value)
        if (absolute <= 0.88f) return value
        val compressed = 0.88f + (absolute - 0.88f) * 0.14f
        return if (value < 0f) -compressed.coerceAtMost(0.98f) else compressed.coerceAtMost(0.98f)
    }
}
