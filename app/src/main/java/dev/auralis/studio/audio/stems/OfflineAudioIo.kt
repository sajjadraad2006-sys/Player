package dev.auralis.studio.audio.stems

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.coroutines.coroutineContext
import kotlin.math.floor

data class DecodedPcmFile(val file: File, val frames: Long, val sampleRateHz: Int = VocFtContract.SAMPLE_RATE_HZ)

/** MediaCodec decode followed by deterministic stereo conversion and linear 44.1 kHz resampling. */
class OfflineAudioDecoder {
    suspend fun decode(source: File, workDirectory: File, onProgress: (Int) -> Unit): DecodedPcmFile =
        withContext(Dispatchers.IO) {
            workDirectory.mkdirs()
            val nativeRaw = File(workDirectory, "decoded-native.f32le")
            val extractor = MediaExtractor()
            var codec: MediaCodec? = null
            try {
                extractor.setDataSource(source.absolutePath)
                val trackIndex = (0 until extractor.trackCount).firstOrNull { index ->
                    extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
                } ?: error("No decodable audio track")
                extractor.selectTrack(trackIndex)
                val sourceFormat = extractor.getTrackFormat(trackIndex)
                val mime = sourceFormat.getString(MediaFormat.KEY_MIME) ?: error("Audio MIME is missing")
                val durationUs = sourceFormat.getLongOrDefault(MediaFormat.KEY_DURATION, 0L)
                codec = MediaCodec.createDecoderByType(mime).apply {
                    configure(sourceFormat, null, null, 0)
                    start()
                }

                var outputSampleRate = sourceFormat.getIntegerOrDefault(MediaFormat.KEY_SAMPLE_RATE, 44_100)
                var outputChannels = sourceFormat.getIntegerOrDefault(MediaFormat.KEY_CHANNEL_COUNT, 2)
                var pcmEncoding = AudioFormat.ENCODING_PCM_16BIT
                var inputEnded = false
                var outputEnded = false
                var frames = 0L
                val info = MediaCodec.BufferInfo()
                FloatStereoWriter(nativeRaw).use { writer ->
                    while (!outputEnded) {
                        coroutineContext.ensureActive()
                        if (!inputEnded) {
                            val inputIndex = codec.dequeueInputBuffer(10_000)
                            if (inputIndex >= 0) {
                                val input = codec.getInputBuffer(inputIndex) ?: error("Decoder input buffer unavailable")
                                val count = extractor.readSampleData(input, 0)
                                if (count < 0) {
                                    codec.queueInputBuffer(inputIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                    inputEnded = true
                                } else {
                                    codec.queueInputBuffer(inputIndex, 0, count, extractor.sampleTime.coerceAtLeast(0L), extractor.sampleFlags)
                                    extractor.advance()
                                }
                            }
                        }

                        when (val outputIndex = codec.dequeueOutputBuffer(info, 10_000)) {
                            MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                                val format = codec.outputFormat
                                outputSampleRate = format.getIntegerOrDefault(MediaFormat.KEY_SAMPLE_RATE, outputSampleRate)
                                outputChannels = format.getIntegerOrDefault(MediaFormat.KEY_CHANNEL_COUNT, outputChannels)
                                pcmEncoding = format.getIntegerOrDefault(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
                            }
                            MediaCodec.INFO_TRY_AGAIN_LATER, MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED -> Unit
                            else -> if (outputIndex >= 0) {
                                if (info.size > 0) {
                                    val output = codec.getOutputBuffer(outputIndex) ?: error("Decoder output buffer unavailable")
                                    output.position(info.offset)
                                    output.limit(info.offset + info.size)
                                    frames += writeDecodedFrames(output.slice().order(ByteOrder.LITTLE_ENDIAN), outputChannels, pcmEncoding, writer)
                                }
                                outputEnded = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                                codec.releaseOutputBuffer(outputIndex, false)
                                if (durationUs > 0L) onProgress(((info.presentationTimeUs * 20L) / durationUs).toInt().coerceIn(0, 20))
                            }
                        }
                    }
                }

                if (frames == 0L) error("Decoder produced no PCM frames")
                if (outputSampleRate == VocFtContract.SAMPLE_RATE_HZ) {
                    onProgress(20)
                    DecodedPcmFile(nativeRaw, frames)
                } else {
                    val resampled = File(workDirectory, "decoded-44100.f32le")
                    val resampledFrames = resample(nativeRaw, frames, outputSampleRate, resampled)
                    nativeRaw.delete()
                    onProgress(20)
                    DecodedPcmFile(resampled, resampledFrames)
                }
            } finally {
                runCatching { codec?.stop() }
                runCatching { codec?.release() }
                extractor.release()
            }
        }

    private fun writeDecodedFrames(
        buffer: ByteBuffer,
        channels: Int,
        encoding: Int,
        writer: FloatStereoWriter,
    ): Int {
        val bytesPerSample = when (encoding) {
            AudioFormat.ENCODING_PCM_FLOAT, AudioFormat.ENCODING_PCM_32BIT -> 4
            AudioFormat.ENCODING_PCM_24BIT_PACKED -> 3
            AudioFormat.ENCODING_PCM_8BIT -> 1
            else -> 2
        }
        val frameBytes = channels.coerceAtLeast(1) * bytesPerSample
        val values = FloatArray(channels.coerceAtLeast(1))
        var frames = 0
        while (buffer.remaining() >= frameBytes) {
            for (channel in values.indices) values[channel] = readSample(buffer, encoding)
            val left: Float
            val right: Float
            if (values.size == 1) {
                left = values[0]; right = values[0]
            } else {
                var l = values[0]
                var r = values[1]
                if (values.size > 2) {
                    val extraScale = 0.5f / (values.size - 2)
                    for (index in 2 until values.size) {
                        l += values[index] * extraScale
                        r += values[index] * extraScale
                    }
                    l /= 1.5f; r /= 1.5f
                }
                left = l; right = r
            }
            writer.write(left, right)
            frames++
        }
        return frames
    }

    private fun readSample(buffer: ByteBuffer, encoding: Int): Float = when (encoding) {
        AudioFormat.ENCODING_PCM_FLOAT -> buffer.float
        AudioFormat.ENCODING_PCM_32BIT -> buffer.int / 2_147_483_648f
        AudioFormat.ENCODING_PCM_24BIT_PACKED -> {
            val b0 = buffer.get().toInt() and 0xff
            val b1 = buffer.get().toInt() and 0xff
            val b2 = buffer.get().toInt()
            ((b0 or (b1 shl 8) or (b2 shl 16)) / 8_388_608f)
        }
        AudioFormat.ENCODING_PCM_8BIT -> ((buffer.get().toInt() and 0xff) - 128) / 128f
        else -> buffer.short / 32_768f
    }

    private suspend fun resample(source: File, sourceFrames: Long, sourceRate: Int, destination: File): Long {
        require(sourceFrames > 0 && sourceRate > 0)
        val ratio = sourceRate.toDouble() / VocFtContract.SAMPLE_RATE_HZ
        val outputFrames = if (sourceFrames == 1L) 1L else
            (floor((sourceFrames - 1) / ratio).toLong() + 1L).coerceAtLeast(1L)
        FloatStereoReader(source).use { reader ->
            FloatStereoWriter(destination).use { writer ->
                var current = reader.read() ?: error("Decoded PCM is empty")
                var next = reader.read() ?: current
                var currentIndex = 0L
                repeatLong(outputFrames) { outputIndex ->
                    coroutineContext.ensureActive()
                    val sourcePosition = outputIndex * ratio
                    val wantedIndex = floor(sourcePosition).toLong()
                    while (currentIndex < wantedIndex) {
                        current = next
                        next = reader.read() ?: current
                        currentIndex++
                    }
                    val fraction = (sourcePosition - currentIndex).toFloat().coerceIn(0f, 1f)
                    writer.write(
                        current.first + (next.first - current.first) * fraction,
                        current.second + (next.second - current.second) * fraction,
                    )
                }
            }
        }
        return outputFrames
    }
}

class FloatStereoWriter(file: File) : AutoCloseable {
    private val output = BufferedOutputStream(file.outputStream(), 256 * 1024)
    private val bytes = ByteArray(64 * 1024)
    private val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

    fun write(left: Float, right: Float) {
        if (buffer.remaining() < 8) flushBuffer()
        buffer.putFloat(left)
        buffer.putFloat(right)
    }

    private fun flushBuffer() {
        output.write(bytes, 0, buffer.position())
        buffer.clear()
    }

    override fun close() {
        flushBuffer()
        output.close()
    }
}

class FloatStereoReader(file: File) : AutoCloseable {
    private val input = BufferedInputStream(file.inputStream(), 256 * 1024)
    private val bytes = ByteArray(8)

    fun read(): Pair<Float, Float>? {
        var offset = 0
        while (offset < bytes.size) {
            val count = input.read(bytes, offset, bytes.size - offset)
            if (count < 0) return if (offset == 0) null else error("Truncated stereo PCM frame")
            offset += count
        }
        return Float.fromBits(littleEndianInt(bytes, 0)) to Float.fromBits(littleEndianInt(bytes, 4))
    }

    override fun close() = input.close()
}

class StereoFloatRandomAccess(file: File, mode: String) : AutoCloseable {
    private val access = RandomAccessFile(file, mode)
    private val bytes = ByteArray(8)

    fun readFrame(frame: Long): Pair<Float, Float> {
        access.seek(frame * 8L)
        access.readFully(bytes)
        return Float.fromBits(littleEndianInt(bytes, 0)) to Float.fromBits(littleEndianInt(bytes, 4))
    }

    fun readFrames(startFrame: Long, left: FloatArray, right: FloatArray) {
        require(left.size == right.size)
        access.seek(startFrame * 8L)
        for (index in left.indices) {
            access.readFully(bytes)
            left[index] = Float.fromBits(littleEndianInt(bytes, 0))
            right[index] = Float.fromBits(littleEndianInt(bytes, 4))
        }
    }

    override fun close() = access.close()
}

class Pcm16WavWriter(file: File, private val sampleRate: Int = VocFtContract.SAMPLE_RATE_HZ) : AutoCloseable {
    private val access = RandomAccessFile(file, "rw")
    private var frames = 0L
    private val bytes = ByteArray(4)

    init {
        access.setLength(0)
        access.write(ByteArray(44))
    }

    fun write(left: Float, right: Float) {
        val l = (left.coerceIn(-1f, 1f) * 32_767f).toInt().toShort().toInt()
        val r = (right.coerceIn(-1f, 1f) * 32_767f).toInt().toShort().toInt()
        bytes[0] = l.toByte(); bytes[1] = (l ushr 8).toByte()
        bytes[2] = r.toByte(); bytes[3] = (r ushr 8).toByte()
        access.write(bytes)
        frames++
    }

    override fun close() {
        val dataBytes = frames * 4L
        access.seek(0)
        access.writeBytes("RIFF")
        writeIntLe((36L + dataBytes).coerceAtMost(0xffff_ffffL).toInt())
        access.writeBytes("WAVEfmt ")
        writeIntLe(16)
        writeShortLe(1)
        writeShortLe(2)
        writeIntLe(sampleRate)
        writeIntLe(sampleRate * 4)
        writeShortLe(4)
        writeShortLe(16)
        access.writeBytes("data")
        writeIntLe(dataBytes.coerceAtMost(0xffff_ffffL).toInt())
        access.close()
    }

    private fun writeIntLe(value: Int) {
        access.write(value and 0xff); access.write((value ushr 8) and 0xff)
        access.write((value ushr 16) and 0xff); access.write((value ushr 24) and 0xff)
    }

    private fun writeShortLe(value: Int) {
        access.write(value and 0xff); access.write((value ushr 8) and 0xff)
    }
}

private fun MediaFormat.getIntegerOrDefault(key: String, default: Int): Int =
    if (containsKey(key)) getInteger(key) else default

private fun MediaFormat.getLongOrDefault(key: String, default: Long): Long =
    if (containsKey(key)) getLong(key) else default

private fun littleEndianInt(bytes: ByteArray, offset: Int): Int =
    (bytes[offset].toInt() and 0xff) or
        ((bytes[offset + 1].toInt() and 0xff) shl 8) or
        ((bytes[offset + 2].toInt() and 0xff) shl 16) or
        (bytes[offset + 3].toInt() shl 24)

private inline fun repeatLong(count: Long, action: (Long) -> Unit) {
    var index = 0L
    while (index < count) action(index++)
}
