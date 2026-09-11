package dev.auralis.desktop.playback.flac

import com.sun.jna.Native
import com.sun.jna.Pointer
import dev.auralis.desktop.playback.decoder.DesktopAudioDecoder
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.util.concurrent.locks.ReentrantLock
import javax.sound.sampled.AudioFormat
import kotlin.concurrent.withLock

/**
 * Native audio decoder for FLAC files using the official Xiph libFLAC 1.5.0 reference library.
 * Normalizes 16-bit, 24-bit, and 32-bit FLAC streams into canonical 16-bit signed stereo PCM.
 */
class FlacAudioDecoder(
    private val file: File,
    private val lib: FlacNativeLibrary = FlacNativeLibrary.loadOrNull()
        ?: throw IllegalStateException("libFLAC native library could not be loaded on this system.")
) : DesktopAudioDecoder {

    private val lock = ReentrantLock()

    private var decoder: Pointer? = null
    private var isClosed = false

    override val format: AudioFormat
    override val durationMs: Long

    private val sampleRate: Int
    private val nativeChannels: Int
    private val nativeBitsPerSample: Int
    private val totalSamples: Long

    private var pcmBuffer = ByteArray(65536)
    private var readOffset = 0
    private var writeOffset = 0

    // Retain strong references to callbacks to prevent garbage collection by JVM
    private val writeCallback = FlacNativeLibrary.WriteCallback { _, frame, bufferPtr, _ ->
        if (bufferPtr == null || frame == null) {
            return@WriteCallback FlacNativeLibrary.FLAC__STREAM_DECODER_WRITE_STATUS_CONTINUE
        }

        val blocksize = frame.getInt(0L)
        if (blocksize <= 0 || blocksize > MAX_FLAC_BLOCK_SIZE) {
            return@WriteCallback FlacNativeLibrary.FLAC__STREAM_DECODER_WRITE_STATUS_ABORT
        }

        // Compact existing unread bytes to head of buffer if needed
        if (readOffset > 0) {
            val unread = writeOffset - readOffset
            if (unread > 0) {
                System.arraycopy(pcmBuffer, readOffset, pcmBuffer, 0, unread)
            }
            writeOffset = unread
            readOffset = 0
        }

        val neededBytes = blocksize * 4 // Stereo 16-bit PCM = 2 channels * 2 bytes = 4 bytes per sample
        ensureBufferCapacity(writeOffset + neededBytes)

        val leftPtr = bufferPtr.getPointer(0L)
            ?: return@WriteCallback FlacNativeLibrary.FLAC__STREAM_DECODER_WRITE_STATUS_CONTINUE
        val rightPtr = if (nativeChannels > 1) {
            bufferPtr.getPointer(Native.POINTER_SIZE.toLong()) ?: leftPtr
        } else {
            leftPtr
        }

        when (nativeBitsPerSample) {
            16 -> {
                for (i in 0 until blocksize) {
                    val sampleL = leftPtr.getInt(i * 4L)
                    val sampleR = rightPtr.getInt(i * 4L)

                    pcmBuffer[writeOffset++] = (sampleL and 0xFF).toByte()
                    pcmBuffer[writeOffset++] = ((sampleL shr 8) and 0xFF).toByte()
                    pcmBuffer[writeOffset++] = (sampleR and 0xFF).toByte()
                    pcmBuffer[writeOffset++] = ((sampleR shr 8) and 0xFF).toByte()
                }
            }
            24 -> {
                // Exact signed arithmetic downshift (sample >> 8) mapping 24-bit to 16-bit without DC bias
                for (i in 0 until blocksize) {
                    val sampleL = leftPtr.getInt(i * 4L) shr 8
                    val sampleR = rightPtr.getInt(i * 4L) shr 8

                    pcmBuffer[writeOffset++] = (sampleL and 0xFF).toByte()
                    pcmBuffer[writeOffset++] = ((sampleL shr 8) and 0xFF).toByte()
                    pcmBuffer[writeOffset++] = (sampleR and 0xFF).toByte()
                    pcmBuffer[writeOffset++] = ((sampleR shr 8) and 0xFF).toByte()
                }
            }
            32 -> {
                for (i in 0 until blocksize) {
                    val sampleL = leftPtr.getInt(i * 4L) shr 16
                    val sampleR = rightPtr.getInt(i * 4L) shr 16

                    pcmBuffer[writeOffset++] = (sampleL and 0xFF).toByte()
                    pcmBuffer[writeOffset++] = ((sampleL shr 8) and 0xFF).toByte()
                    pcmBuffer[writeOffset++] = (sampleR and 0xFF).toByte()
                    pcmBuffer[writeOffset++] = ((sampleR shr 8) and 0xFF).toByte()
                }
            }
            8 -> {
                for (i in 0 until blocksize) {
                    val sampleL = (leftPtr.getInt(i * 4L) - 128) shl 8
                    val sampleR = (rightPtr.getInt(i * 4L) - 128) shl 8

                    pcmBuffer[writeOffset++] = (sampleL and 0xFF).toByte()
                    pcmBuffer[writeOffset++] = ((sampleL shr 8) and 0xFF).toByte()
                    pcmBuffer[writeOffset++] = (sampleR and 0xFF).toByte()
                    pcmBuffer[writeOffset++] = ((sampleR shr 8) and 0xFF).toByte()
                }
            }
            else -> {
                val shift = (nativeBitsPerSample - 16).coerceAtLeast(0)
                for (i in 0 until blocksize) {
                    val sampleL = leftPtr.getInt(i * 4L) shr shift
                    val sampleR = rightPtr.getInt(i * 4L) shr shift

                    pcmBuffer[writeOffset++] = (sampleL and 0xFF).toByte()
                    pcmBuffer[writeOffset++] = ((sampleL shr 8) and 0xFF).toByte()
                    pcmBuffer[writeOffset++] = (sampleR and 0xFF).toByte()
                    pcmBuffer[writeOffset++] = ((sampleR shr 8) and 0xFF).toByte()
                }
            }
        }

        FlacNativeLibrary.FLAC__STREAM_DECODER_WRITE_STATUS_CONTINUE
    }

    private val metadataCallback = FlacNativeLibrary.MetadataCallback { _, _, _ ->
        // STREAMINFO and other metadata blocks received
    }

    private val errorCallback = FlacNativeLibrary.ErrorCallback { _, _, _ ->
        // Decoder error reported by libFLAC; frame resynchronization handled internally
    }

    init {
        if (!file.exists() || !file.canRead()) {
            throw FileNotFoundException("FLAC file not found or inaccessible: ${file.absolutePath}")
        }

        val dec = lib.FLAC__stream_decoder_new()
            ?: throw IOException("Failed to allocate libFLAC stream decoder instance")
        decoder = dec

        val initStatus = lib.FLAC__stream_decoder_init_file(
            dec,
            file.absolutePath,
            writeCallback,
            metadataCallback,
            errorCallback,
            null
        )

        if (initStatus != FlacNativeLibrary.FLAC__STREAM_DECODER_INIT_STATUS_OK) {
            close()
            throw IOException("Failed to initialize libFLAC file decoder (status $initStatus)")
        }

        // Process metadata blocks
        lib.FLAC__stream_decoder_process_until_end_of_metadata(dec)

        // Process the first frame to populate stream parameters and buffer initial samples
        lib.FLAC__stream_decoder_process_single(dec)

        val state = lib.FLAC__stream_decoder_get_state(dec)
        if (state == FlacNativeLibrary.FLAC__STREAM_DECODER_ABORTED ||
            state == FlacNativeLibrary.FLAC__STREAM_DECODER_MEMORY_ALLOCATION_ERROR
        ) {
            close()
            throw IOException("Corrupt or invalid FLAC stream: decoder aborted with state $state")
        }

        val sr = lib.FLAC__stream_decoder_get_sample_rate(dec)
        val ch = lib.FLAC__stream_decoder_get_channels(dec)
        val bps = lib.FLAC__stream_decoder_get_bits_per_sample(dec)

        if (sr < 8000 || sr > 384000 || ch <= 0 || bps !in 4..32) {
            close()
            throw IOException("Invalid or unsupported FLAC stream parameters (sr=$sr, ch=$ch, bps=$bps)")
        }

        if (ch > 2) {
            close()
            throw IllegalArgumentException("Unsupported multichannel layout: $ch channels. Lyrune currently supports mono or stereo FLAC.")
        }
        nativeChannels = ch
        sampleRate = sr
        nativeBitsPerSample = bps

        val tot = lib.FLAC__stream_decoder_get_total_samples(dec)
        totalSamples = tot.coerceAtLeast(0L)

        durationMs = if (sampleRate > 0 && totalSamples > 0) {
            (totalSamples * 1000L) / sampleRate
        } else {
            0L
        }

        format = AudioFormat(sampleRate.toFloat(), 16, 2, true, false)
    }

    override fun readPcm(target: ByteArray, offset: Int, length: Int): Int = lock.withLock {
        if (isClosed || length <= 0) return -1
        val dec = decoder ?: return -1

        while (writeOffset - readOffset < length) {
            val state = lib.FLAC__stream_decoder_get_state(dec)
            if (state == FlacNativeLibrary.FLAC__STREAM_DECODER_END_OF_STREAM) {
                break
            }

            val ok = lib.FLAC__stream_decoder_process_single(dec)
            if (ok == 0) {
                // An error occurred or stream ended
                val curState = lib.FLAC__stream_decoder_get_state(dec)
                if (curState == FlacNativeLibrary.FLAC__STREAM_DECODER_END_OF_STREAM ||
                    curState == FlacNativeLibrary.FLAC__STREAM_DECODER_ABORTED
                ) {
                    break
                }
            }
        }

        val available = writeOffset - readOffset
        if (available <= 0) {
            val state = lib.FLAC__stream_decoder_get_state(dec)
            return if (state == FlacNativeLibrary.FLAC__STREAM_DECODER_END_OF_STREAM) -1 else 0
        }

        val toCopy = minOf(available, length)
        System.arraycopy(pcmBuffer, readOffset, target, offset, toCopy)
        readOffset += toCopy
        return toCopy
    }

    override fun seekTo(positionMs: Long): Boolean = lock.withLock {
        if (isClosed) return false
        val dec = decoder ?: return false
        if (sampleRate <= 0) return false

        val targetSample = ((positionMs.coerceAtLeast(0L) * sampleRate.toLong()) / 1000L)
            .coerceIn(0L, maxOf(0L, totalSamples - 1L))

        readOffset = 0
        writeOffset = 0

        val seekResult = lib.FLAC__stream_decoder_seek_absolute(dec, targetSample)
        return if (seekResult != 0) {
            true
        } else {
            lib.FLAC__stream_decoder_flush(dec)
            false
        }
    }

    override fun close() = lock.withLock {
        if (isClosed) return
        isClosed = true

        val dec = decoder
        if (dec != null) {
            try {
                lib.FLAC__stream_decoder_finish(dec)
            } catch (_: Throwable) {}
            try {
                lib.FLAC__stream_decoder_delete(dec)
            } catch (_: Throwable) {}
            decoder = null
        }

        readOffset = 0
        writeOffset = 0
    }

    private fun ensureBufferCapacity(needed: Int) {
        if (needed > MAX_BUFFER_CAPACITY) {
            throw IOException("FLAC buffer overflow: required $needed bytes exceeds hard cap of $MAX_BUFFER_CAPACITY bytes.")
        }
        if (pcmBuffer.size < needed) {
            val newCap = minOf(maxOf(pcmBuffer.size * 2, needed), MAX_BUFFER_CAPACITY)
            val newBuf = ByteArray(newCap)
            val unread = writeOffset - readOffset
            if (unread > 0) {
                System.arraycopy(pcmBuffer, readOffset, newBuf, 0, unread)
            }
            pcmBuffer = newBuf
            readOffset = 0
            writeOffset = unread
        }
    }

    companion object {
        /** Official FLAC specification maximum blocksize in samples */
        const val MAX_FLAC_BLOCK_SIZE = 65535

        /** 1 MiB hard upper ceiling for decoded PCM streaming buffer */
        const val MAX_BUFFER_CAPACITY = 1024 * 1024
    }
}
