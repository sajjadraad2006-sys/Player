package dev.auralis.studio.audio.dsp

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.AudioProcessor.UnhandledAudioFormatException
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import dev.auralis.studio.core.model.AudioConfig
import dev.auralis.studio.core.model.ChannelMode
import dev.auralis.studio.core.model.OrbitDirection
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
 * Low-allocation PCM processing used only for local playback. Order:
 * preamp → graphic EQ/tone → stereo matrix → smooth 8D orbit → reverb → safe limiter.
 * Media3's Sonic processor performs pitch/time-stretch before this processor.
 *
 * Visualizer tap: After the output sample is ready, we copy a downsampled mono
 * snapshot into [visualizerRingBuffer] using lock-free atomic writes.
 * The ring buffer is pre-allocated and never causes GC in the audio thread.
 * The visualizer reads it on a background thread — no synchronization needed
 * because we use a simple power-of-two ring with atomic indices.
 */
@UnstableApi
class StudioAudioProcessor : BaseAudioProcessor() {
    private val requestedConfig = AtomicReference(AudioConfig())
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

    // ── Visualizer tap ──────────────────────────────────────────────────────
    // 4096-sample ring buffer for the visualizer (mono, -1..1 float).
    // Power-of-two size for efficient masking. Written in queueInput() at ~1:8
    // downsampled rate to keep write overhead tiny.
    val visualizerRingBuffer  = FloatArray(4096)
    @Volatile var visualizerWritePos = 0
    private var tapDecimator = 0

    fun update(config: AudioConfig) {
        requestedConfig.set(config)
    }

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            throw UnhandledAudioFormatException(inputAudioFormat)
        }
        sampleRate = inputAudioFormat.sampleRate
        channelCount = inputAudioFormat.channelCount
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
            // Even when DSP is bypassed, still feed the visualizer tap cheaply
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
            if (config.equalizerEnabled || config.bassDb != 0f || config.trebleDb != 0f) {
                leftFilters.forEach { left = it.process(left) }
                rightFilters.forEach { right = it.process(right) }
            }

            val channelPair = applyChannelMode(left, right, config.channelMode)
            left = channelPair.first
            right = channelPair.second

            val mid = (left + right) * 0.5f
            val side = (left - right) * 0.5f * config.stereoWidth
            left = mid + side
            right = mid - side

            val balanceLeft = if (config.balance > 0f) 1f - config.balance else 1f
            val balanceRight = if (config.balance < 0f) 1f + config.balance else 1f
            left *= balanceLeft
            right *= balanceRight

            if (config.eightDEnabled && channelCount > 1) {
                val direction = if (config.orbitDirection == OrbitDirection.COUNTER_CLOCKWISE) -1.0 else 1.0
                phase += direction * 2.0 * PI * config.eightDRateHz / sampleRate
                if (phase > 2.0 * PI || phase < -2.0 * PI) phase %= 2.0 * PI
                val targetPan = sin(phase).toFloat() * config.eightDStrength
                smoothedPan += (targetPan - smoothedPan) * 0.0015f
                val leftPan = sqrt(((1f - smoothedPan) * 0.5f).coerceIn(0f, 1f)) * 1.4142f
                val rightPan = sqrt(((1f + smoothedPan) * 0.5f).coerceIn(0f, 1f)) * 1.4142f
                val crossfeed = 0.035f * config.eightDStrength
                val originalLeft = left
                left = (left * leftPan) + (right * crossfeed)
                right = (right * rightPan) + (originalLeft * crossfeed)
            } else {
                smoothedPan *= 0.995f
            }

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

            if (config.limiterEnabled) {
                left = softLimit(left)
                right = softLimit(right)
            }

            output.putShort((left.coerceIn(-1f, 1f) * 32767f).toInt().toShort())
            if (channelCount > 1) output.putShort((right.coerceIn(-1f, 1f) * 32767f).toInt().toShort())
            repeat((channelCount - 2).coerceAtLeast(0)) { output.putShort(inputBuffer.short) }

            // Visualizer tap — write every 8th frame to keep callback overhead minimal
            tapDecimator++
            if (tapDecimator >= 8) {
                tapDecimator = 0
                val mono = (left + right) * 0.5f
                val pos = visualizerWritePos and (visualizerRingBuffer.size - 1)
                visualizerRingBuffer[pos] = mono
                visualizerWritePos = (pos + 1) and (visualizerRingBuffer.size - 1)
            }
        }
        output.flip()
    }

    /** Tap bypass path — cheap downsampled mono read without DSP. */
    private fun tapBypass(buf: ByteBuffer) {
        val frameBytes = channelCount * 2
        var decimator = tapDecimator
        val readBuf = buf.duplicate().order(ByteOrder.nativeOrder())
        while (readBuf.remaining() >= frameBytes) {
            val left = readBuf.short / 32768f
            val right = if (channelCount > 1) readBuf.short / 32768f else left
            repeat((channelCount - 2).coerceAtLeast(0)) { readBuf.short }
            decimator++
            if (decimator >= 8) {
                decimator = 0
                val mono = (left + right) * 0.5f
                val pos = visualizerWritePos and (visualizerRingBuffer.size - 1)
                visualizerRingBuffer[pos] = mono
                visualizerWritePos = (pos + 1) and (visualizerRingBuffer.size - 1)
            }
        }
        tapDecimator = decimator
    }

    override fun onFlush() {
        phase = 0.0
        smoothedPan = 0f
        smoothedWet = 0f
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
        visualizerRingBuffer.fill(0f)
        visualizerWritePos = 0
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
