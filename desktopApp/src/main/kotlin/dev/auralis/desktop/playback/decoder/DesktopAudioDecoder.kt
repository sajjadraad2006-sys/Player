package dev.auralis.desktop.playback.decoder

import javax.sound.sampled.AudioFormat

/**
 * Narrow audio decoder boundary abstraction for Lyrune desktop playback.
 * Decodes audio stream into raw PCM on-demand in a pull-style interface.
 */
interface DesktopAudioDecoder : AutoCloseable {

    /**
     * Native AudioFormat of the decoded audio data (sample rate, bit depth, channels, endianness).
     */
    val format: AudioFormat

    /**
     * Total track duration in milliseconds, or 0 if unknown/streaming.
     */
    val durationMs: Long

    /**
     * Reads up to [length] decoded PCM bytes into [target] starting at [offset].
     *
     * @return Number of bytes read, or -1 if the end of stream has been reached.
     */
    fun readPcm(target: ByteArray, offset: Int, length: Int): Int

    /**
     * Re-positions playback to [positionMs].
     *
     * @return True if seek succeeded, false otherwise.
     */
    fun seekTo(positionMs: Long): Boolean

    /**
     * Releases any open native library handles, file streams, or network connections.
     */
    override fun close()
}
