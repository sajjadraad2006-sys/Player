package dev.auralis.desktop.playback.decoder

import java.io.File
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem

/**
 * Audio decoder for WAV, AIFF, and AU formats backed by Java Sound SPI.
 */
class WavAudioDecoder(private val file: File) : DesktopAudioDecoder {

    private var audioIn: AudioInputStream? = null
    override val format: AudioFormat
    override val durationMs: Long

    private val bytesPerMs: Double

    init {
        val stream = AudioSystem.getAudioInputStream(file)
        audioIn = stream
        val fmt = stream.format
        format = fmt

        val frameLength = stream.frameLength
        durationMs = if (frameLength > 0 && fmt.frameRate > 0) {
            (frameLength * 1000.0 / fmt.frameRate).toLong()
        } else {
            0L
        }

        val frameSize = if (fmt.frameSize > 0) fmt.frameSize else (fmt.channels * (fmt.sampleSizeInBits / 8))
        bytesPerMs = (fmt.sampleRate * frameSize) / 1000.0
    }

    override fun readPcm(target: ByteArray, offset: Int, length: Int): Int {
        val stream = audioIn ?: return -1
        return stream.read(target, offset, length)
    }

    override fun seekTo(positionMs: Long): Boolean {
        if (bytesPerMs <= 0) return false
        val targetByteOffset = (positionMs.coerceAtLeast(0L) * bytesPerMs).toLong()
        try {
            audioIn?.close()
        } catch (_: Throwable) {}

        return try {
            val freshIn = AudioSystem.getAudioInputStream(file)
            var skippedTotal = 0L
            while (skippedTotal < targetByteOffset) {
                val skipped = freshIn.skip(targetByteOffset - skippedTotal)
                if (skipped <= 0) break
                skippedTotal += skipped
            }
            audioIn = freshIn
            true
        } catch (_: Throwable) {
            false
        }
    }

    override fun close() {
        try {
            audioIn?.close()
        } catch (_: Throwable) {}
        audioIn = null
    }
}
