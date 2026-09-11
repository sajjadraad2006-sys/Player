package dev.auralis.desktop.playback.decoder

import javazoom.jl.decoder.Bitstream
import javazoom.jl.decoder.Decoder
import javazoom.jl.decoder.Header
import javazoom.jl.decoder.SampleBuffer
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import javax.sound.sampled.AudioFormat

/**
 * Audio decoder for MP3 streams and files backed by JLayer decoder.
 */
class Mp3AudioDecoder(
    private val file: File?,
    initialStream: InputStream? = null,
    private val totalStreamBytes: Long = 0L,
) : DesktopAudioDecoder {

    private var activeStream: InputStream? = initialStream ?: file?.let { BufferedInputStream(FileInputStream(it), 32768) }
    private var bitstream: Bitstream? = activeStream?.let { Bitstream(it) }
    private val decoder = Decoder()

    override val format: AudioFormat
    override val durationMs: Long

    private val internalPcmBuffer = ByteArray(18432)
    private var bufferPos = 0
    private var bufferLimit = 0

    private val sampleRate: Int
    private val channels: Int
    private val msPerFrame: Float
    private val totalBytes: Long = file?.length() ?: totalStreamBytes

    init {
        val bs = bitstream ?: throw IllegalArgumentException("No stream available for MP3 decoding")
        val firstHeader = bs.readFrame() ?: throw IllegalArgumentException("Empty or corrupted MP3 stream")

        sampleRate = firstHeader.frequency()
        channels = if (firstHeader.mode() == Header.SINGLE_CHANNEL) 1 else 2
        msPerFrame = firstHeader.ms_per_frame()
        val bitrate = firstHeader.bitrate()

        durationMs = if (firstHeader.vbr() && totalBytes > 0) {
            firstHeader.total_ms(totalBytes.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()).toLong()
        } else if (bitrate > 0 && totalBytes > 0) {
            (totalBytes * 8L * 1000L) / bitrate.toLong()
        } else {
            0L
        }

        format = AudioFormat(sampleRate.toFloat(), 16, channels, true, false)

        // Decode first frame into internal buffer
        val firstBuffer = decoder.decodeFrame(firstHeader, bs) as? SampleBuffer
        if (firstBuffer != null) {
            bufferLimit = copySamplesToBytes(firstBuffer, internalPcmBuffer)
            bufferPos = 0
        }
        bs.closeFrame()
    }

    override fun readPcm(target: ByteArray, offset: Int, length: Int): Int {
        if (length <= 0) return 0

        while (bufferPos >= bufferLimit) {
            // Need to decode next MP3 frame
            val bs = bitstream ?: return -1
            val header = bs.readFrame() ?: return -1
            val sampleBuffer = decoder.decodeFrame(header, bs) as? SampleBuffer
            if (sampleBuffer != null) {
                bufferLimit = copySamplesToBytes(sampleBuffer, internalPcmBuffer)
                bufferPos = 0
            } else {
                bufferLimit = 0
                bufferPos = 0
            }
            bs.closeFrame()
        }

        val available = bufferLimit - bufferPos
        val toCopy = minOf(available, length)
        System.arraycopy(internalPcmBuffer, bufferPos, target, offset, toCopy)
        bufferPos += toCopy
        return toCopy
    }

    override fun seekTo(positionMs: Long): Boolean {
        val f = file ?: return false
        if (durationMs <= 0) return false

        try {
            bitstream?.close()
            activeStream?.close()
        } catch (_: Throwable) {}

        val targetFraction = positionMs.toDouble() / durationMs
        val skipBytes = (totalBytes * targetFraction).toLong().coerceIn(0L, maxOf(0L, totalBytes - 128))

        return try {
            val newStream = BufferedInputStream(FileInputStream(f), 32768)
            var skippedTotal = 0L
            while (skippedTotal < skipBytes) {
                val s = newStream.skip(skipBytes - skippedTotal)
                if (s <= 0) break
                skippedTotal += s
            }
            activeStream = newStream
            bitstream = Bitstream(newStream)
            bufferPos = 0
            bufferLimit = 0
            true
        } catch (_: Throwable) {
            false
        }
    }

    override fun close() {
        try { bitstream?.close() } catch (_: Throwable) {}
        try { activeStream?.close() } catch (_: Throwable) {}
        bitstream = null
        activeStream = null
        bufferPos = 0
        bufferLimit = 0
    }

    private fun copySamplesToBytes(sampleBuffer: SampleBuffer, out: ByteArray): Int {
        val samples = sampleBuffer.buffer
        val count = sampleBuffer.bufferLength
        var outIdx = 0
        for (i in 0 until count) {
            val sample = samples[i]
            out[outIdx++] = (sample.toInt() and 0xFF).toByte()
            out[outIdx++] = ((sample.toInt() shr 8) and 0xFF).toByte()
        }
        return outIdx
    }
}
