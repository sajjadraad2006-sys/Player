package dev.auralis.studio.audio.stems

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs
import kotlin.math.tanh

data class StemMixGains(
    val vocals: Float = 1.0f,
    val instrumental: Float = 1.0f,
    val bass: Float = 1.0f,
)

object StemMixerController {
    val gains = AtomicReference(StemMixGains())

    fun setVocalsGain(gain: Float) {
        val clamped = gain.coerceIn(0f, 1.5f)
        gains.updateAndGet { it.copy(vocals = clamped) }
    }

    fun setInstrumentalGain(gain: Float) {
        val clamped = gain.coerceIn(0f, 1.5f)
        gains.updateAndGet { it.copy(instrumental = clamped) }
    }

    fun setBassGain(gain: Float) {
        val clamped = gain.coerceIn(0f, 1.5f)
        gains.updateAndGet { it.copy(bass = clamped) }
    }

    fun resetGains() {
        gains.set(StemMixGains(1.0f, 1.0f, 1.0f))
    }
}

/**
 * Pure, sample-accurate, click-free multi-stem mixer.
 * Guarantees numerically exact linear reconstruction for Vocals (100%) + Instrumental (100%),
 * applies sample-rate-invariant smooth gain interpolation, and protects against digital clipping.
 *
 * Gain smoothing uses a continuous one-pole exponential smoothing filter:
 *   y[n] = y[n-1] + alpha * (target - y[n-1])
 * where alpha(fs) = 1 - exp(-1 / (fs * tau)) with nominal time constant tau = 10 ms.
 */
class StemMixerProcessor(
    private val sampleRateHz: Int = VocFtContract.SAMPLE_RATE_HZ,
    timeConstantSeconds: Float = 0.010f, // 10 ms nominal time constant
) {
    private var currentVocalsGain = 1.0f
    private var currentInstGain = 1.0f
    private var currentBassGain = 1.0f

    // Sample-rate invariant smoothing coefficient alpha
    val alpha: Float = (1.0 - kotlin.math.exp(-1.0 / (sampleRateHz * timeConstantSeconds))).toFloat()

    /** Nominal time constant in milliseconds: tau = -1 / (fs * ln(1 - alpha)) */
    val timeConstantMs: Float = (-1000f / (sampleRateHz * kotlin.math.ln(1.0 - alpha.toDouble()))).toFloat()

    /**
     * Mixes interleaved 16-bit stereo PCM frames into [outputBuffer].
     */
    fun mixFrames(
        vocals: ShortArray,
        instrumental: ShortArray,
        bass: ShortArray?,
        targetGains: StemMixGains,
        outputBuffer: ByteArray,
        outputOffset: Int,
        frameCount: Int,
    ) {

        var bytePos = outputOffset
        val hasBass = bass != null

        for (frame in 0 until frameCount) {
            // Smoothly ramp gains towards targets to prevent clicks
            currentVocalsGain += (targetGains.vocals - currentVocalsGain) * alpha
            currentInstGain += (targetGains.instrumental - currentInstGain) * alpha
            currentBassGain += (targetGains.bass - currentBassGain) * alpha

            val sampleIdx = frame * 2
            val vL = vocals[sampleIdx].toFloat()
            val vR = vocals[sampleIdx + 1].toFloat()
            val iL = instrumental[sampleIdx].toFloat()
            val iR = instrumental[sampleIdx + 1].toFloat()
            val bL = if (hasBass) bass!![sampleIdx].toFloat() else 0f
            val bR = if (hasBass) bass!![sampleIdx + 1].toFloat() else 0f

            val mixedL = vL * currentVocalsGain + iL * currentInstGain + bL * currentBassGain
            val mixedR = vR * currentVocalsGain + iR * currentInstGain + bR * currentBassGain

            val clampedL = sanitizeAndClampSample(mixedL)
            val clampedR = sanitizeAndClampSample(mixedR)

            outputBuffer[bytePos++] = (clampedL and 0xFF).toByte()
            outputBuffer[bytePos++] = ((clampedL ushr 8) and 0xFF).toByte()
            outputBuffer[bytePos++] = (clampedR and 0xFF).toByte()
            outputBuffer[bytePos++] = ((clampedR ushr 8) and 0xFF).toByte()
        }
    }

    /**
     * Preserves exact linear output when within [-32768, 32767],
     * and applies smooth soft saturation only if the mixed sample would exceed dynamic range.
     */
    fun sanitizeAndClampSample(sample: Float): Int {
        if (!sample.isFinite()) return 0
        if (sample >= -32768f && sample <= 32767f) {
            return sample.toInt().coerceIn(-32768, 32767)
        }
        // Soft compression for out-of-range peaks
        val normalized = sample / 32768f
        val saturated = tanh(normalized.toDouble()).toFloat() * 32767f
        return saturated.toInt().coerceIn(-32768, 32767)
    }
}

/**
 * Media3 seekable virtual WAV DataSource for synchronized stem playback.
 * Synthesizes a valid WAV stream on the fly from separated stems on a single timeline.
 */
@OptIn(UnstableApi::class)
class StemMixingDataSource : DataSource {

    private var vocalsFile: RandomAccessFile? = null
    private var instrumentalFile: RandomAccessFile? = null
    private var bassFile: RandomAccessFile? = null

    private var totalFrames: Long = 0L
    private var streamLength: Long = 0L
    private var currentPosition: Long = 0L
    private var bytesRemaining: Long = 0L

    private val headerBuffer = ByteArray(44)
    private val mixer = StemMixerProcessor()

    // Reusable buffers to avoid heap churn
    private val pcmChunkFrames = 4096
    private val rawVocals = ByteArray(pcmChunkFrames * 4)
    private val rawInstrumental = ByteArray(pcmChunkFrames * 4)
    private val rawBass = ByteArray(pcmChunkFrames * 4)
    private val shortVocals = ShortArray(pcmChunkFrames * 2)
    private val shortInstrumental = ShortArray(pcmChunkFrames * 2)
    private val shortBass = ShortArray(pcmChunkFrames * 2)
    private val mixedBlock = ByteArray(pcmChunkFrames * 4)

    override fun addTransferListener(transferListener: androidx.media3.datasource.TransferListener) = Unit

    override fun open(dataSpec: DataSpec): Long {
        val uri = dataSpec.uri
        val dirPath = uri.path ?: uri.schemeSpecificPart.removePrefix("//")
        val dir = File(dirPath)
        val vocals = File(dir, "vocals.wav")
        val instrumental = File(dir, "instrumental.wav")
        val bass = File(dir, "bass.wav")

        require(vocals.isFile && instrumental.isFile) {
            "Stem files missing in directory: $dirPath"
        }

        val vRaf = RandomAccessFile(vocals, "r")
        val iRaf = RandomAccessFile(instrumental, "r")
        val bRaf = if (bass.isFile) RandomAccessFile(bass, "r") else null

        vocalsFile = vRaf
        instrumentalFile = iRaf
        bassFile = bRaf

        totalFrames = (vocals.length() - 44L) / 4L
        streamLength = 44L + totalFrames * 4L

        synthesizeWavHeader(headerBuffer, totalFrames, VocFtContract.SAMPLE_RATE_HZ)

        currentPosition = dataSpec.position
        bytesRemaining = if (dataSpec.length != C.LENGTH_UNSET.toLong()) {
            dataSpec.length
        } else {
            streamLength - dataSpec.position
        }

        seekFiles(currentPosition)
        return bytesRemaining
    }

    private fun seekFiles(position: Long) {
        val fileByteOffset = if (position < 44L) 44L else 44L + ((position - 44L) / 4L) * 4L
        vocalsFile?.seek(fileByteOffset)
        instrumentalFile?.seek(fileByteOffset)
        bassFile?.seek(fileByteOffset)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (bytesRemaining <= 0L || length <= 0) return C.RESULT_END_OF_INPUT

        var bytesWritten = 0
        var destOffset = offset
        var toRead = minOf(length.toLong(), bytesRemaining).toInt()

        // 1. Deliver header bytes if within header range (0..43)
        if (currentPosition < 44L) {
            val headerOffset = currentPosition.toInt()
            val headerBytesAvailable = 44 - headerOffset
            val count = minOf(toRead, headerBytesAvailable)
            System.arraycopy(headerBuffer, headerOffset, buffer, destOffset, count)
            bytesWritten += count
            destOffset += count
            toRead -= count
            currentPosition += count
            bytesRemaining -= count
            if (toRead == 0) return bytesWritten
        }

        // 2. Deliver mixed PCM samples
        val activeGains = StemMixerController.gains.get()
        while (toRead > 0) {
            val framesToRead = minOf(pcmChunkFrames, toRead / 4)
            if (framesToRead <= 0) {
                // Handling partial frame edge
                val singleFrameBytes = minOf(toRead, 4)
                currentPosition += singleFrameBytes
                bytesRemaining -= singleFrameBytes
                bytesWritten += singleFrameBytes
                break
            }

            val bytesForFrames = framesToRead * 4
            val vRead = vocalsFile?.read(rawVocals, 0, bytesForFrames) ?: -1
            val iRead = instrumentalFile?.read(rawInstrumental, 0, bytesForFrames) ?: -1
            if (vRead < bytesForFrames || iRead < bytesForFrames) {
                break
            }

            val hasBass = bassFile != null
            if (hasBass) {
                bassFile?.read(rawBass, 0, bytesForFrames)
                bytesToShorts(rawBass, shortBass, framesToRead * 2)
            }

            bytesToShorts(rawVocals, shortVocals, framesToRead * 2)
            bytesToShorts(rawInstrumental, shortInstrumental, framesToRead * 2)

            mixer.mixFrames(
                vocals = shortVocals,
                instrumental = shortInstrumental,
                bass = if (hasBass) shortBass else null,
                targetGains = activeGains,
                outputBuffer = mixedBlock,
                outputOffset = 0,
                frameCount = framesToRead,
            )

            System.arraycopy(mixedBlock, 0, buffer, destOffset, bytesForFrames)
            destOffset += bytesForFrames
            bytesWritten += bytesForFrames
            toRead -= bytesForFrames
            currentPosition += bytesForFrames
            bytesRemaining -= bytesForFrames
        }

        return if (bytesWritten > 0) bytesWritten else C.RESULT_END_OF_INPUT
    }

    private fun bytesToShorts(bytes: ByteArray, shorts: ShortArray, count: Int) {
        var byteIdx = 0
        for (i in 0 until count) {
            val b0 = bytes[byteIdx++].toInt() and 0xFF
            val b1 = bytes[byteIdx++].toInt() and 0xFF
            shorts[i] = ((b1 shl 8) or b0).toShort()
        }
    }

    override fun getUri() = null

    override fun close() {
        runCatching { vocalsFile?.close() }
        runCatching { instrumentalFile?.close() }
        runCatching { bassFile?.close() }
        vocalsFile = null
        instrumentalFile = null
        bassFile = null
    }

    companion object {
        fun synthesizeWavHeader(header: ByteArray, frames: Long, sampleRate: Int) {
            val dataBytes = frames * 4L
            val buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
            buffer.put("RIFF".toByteArray(Charsets.US_ASCII))
            buffer.putInt((36L + dataBytes).coerceAtMost(0xffff_ffffL).toInt())
            buffer.put("WAVEfmt ".toByteArray(Charsets.US_ASCII))
            buffer.putInt(16) // Subchunk1Size (16 for PCM)
            buffer.putShort(1) // AudioFormat (1 for PCM)
            buffer.putShort(2) // NumChannels (2 for Stereo)
            buffer.putInt(sampleRate)
            buffer.putInt(sampleRate * 4) // ByteRate
            buffer.putShort(4) // BlockAlign
            buffer.putShort(16) // BitsPerSample
            buffer.put("data".toByteArray(Charsets.US_ASCII))
            buffer.putInt(dataBytes.coerceAtMost(0xffff_ffffL).toInt())
        }
    }
}
