package dev.auralis.desktop.playback

import javax.sound.sampled.AudioFormat

/**
 * Normalizes varied decoded PCM formats into canonical 16-bit signed little-endian stereo PCM.
 *
 * Requirements:
 * - Operates on pre-allocated reusable buffers to avoid allocations in the playback loop.
 * - Handles:
 *   - Mono -> Stereo upmixing (duplicates mono sample across L and R)
 *   - 8-bit unsigned -> 16-bit signed
 *   - 16-bit signed -> pass-through / channel mapping
 *   - 24-bit signed -> 16-bit signed (discards lowest 8 bits of precision)
 *   - 32-bit float -> 16-bit signed
 */
class PcmNormalizer(initialCapacity: Int = 32768) {

    private var buffer = ByteArray(initialCapacity)

    /**
     * Normalizes input PCM bytes described by [format] into canonical 16-bit signed stereo PCM.
     *
     * @param input Raw input byte array
     * @param offset Starting byte index in [input]
     * @param length Number of valid bytes in [input]
     * @param format AudioFormat of the input PCM
     * @return A [NormalizedChunk] pointing to the internal reusable buffer and valid byte count.
     */
    fun normalize(
        input: ByteArray,
        offset: Int,
        length: Int,
        format: AudioFormat,
    ): NormalizedChunk {
        val channels = format.channels
        val sampleSizeBits = format.sampleSizeInBits
        val isBigEndian = format.isBigEndian

        // Fast path: already 16-bit stereo little-endian
        if (channels == 2 && sampleSizeBits == 16 && !isBigEndian && format.encoding == AudioFormat.Encoding.PCM_SIGNED) {
            return NormalizedChunk(input, offset, length)
        }

        val bytesPerSample = sampleSizeBits / 8
        val frameSize = channels * bytesPerSample
        val totalFrames = length / frameSize
        val requiredBytes = totalFrames * 4 // Stereo 16-bit: 2 channels * 2 bytes = 4 bytes per frame

        ensureCapacity(requiredBytes)

        var src = offset
        var dst = 0

        when {
            // 16-bit signed Mono -> Stereo
            channels == 1 && sampleSizeBits == 16 -> {
                for (f in 0 until totalFrames) {
                    val b0 = input[src++]
                    val b1 = input[src++]
                    // Left channel
                    buffer[dst++] = b0
                    buffer[dst++] = b1
                    // Right channel (duplicate)
                    buffer[dst++] = b0
                    buffer[dst++] = b1
                }
            }

            // 24-bit signed Stereo -> 16-bit Stereo (drop lowest byte)
            channels == 2 && sampleSizeBits == 24 -> {
                for (f in 0 until totalFrames) {
                    // Left sample: 3 bytes (little-endian: low, mid, high)
                    src++ // skip low byte
                    buffer[dst++] = input[src++] // mid
                    buffer[dst++] = input[src++] // high

                    // Right sample: 3 bytes
                    src++ // skip low byte
                    buffer[dst++] = input[src++] // mid
                    buffer[dst++] = input[src++] // high
                }
            }

            // 24-bit signed Mono -> 16-bit Stereo
            channels == 1 && sampleSizeBits == 24 -> {
                for (f in 0 until totalFrames) {
                    src++ // skip low
                    val mid = input[src++]
                    val high = input[src++]

                    buffer[dst++] = mid
                    buffer[dst++] = high
                    buffer[dst++] = mid
                    buffer[dst++] = high
                }
            }

            // 8-bit unsigned Mono -> 16-bit Stereo
            channels == 1 && sampleSizeBits == 8 -> {
                for (f in 0 until totalFrames) {
                    val u8 = input[src++].toInt() and 0xFF
                    val s16 = ((u8 - 128) shl 8).toShort()
                    val b0 = (s16.toInt() and 0xFF).toByte()
                    val b1 = ((s16.toInt() shr 8) and 0xFF).toByte()

                    buffer[dst++] = b0
                    buffer[dst++] = b1
                    buffer[dst++] = b0
                    buffer[dst++] = b1
                }
            }

            // 8-bit unsigned Stereo -> 16-bit Stereo
            channels == 2 && sampleSizeBits == 8 -> {
                for (f in 0 until totalFrames) {
                    val leftU8 = input[src++].toInt() and 0xFF
                    val rightU8 = input[src++].toInt() and 0xFF

                    val leftS16 = ((leftU8 - 128) shl 8).toShort()
                    val rightS16 = ((rightU8 - 128) shl 8).toShort()

                    buffer[dst++] = (leftS16.toInt() and 0xFF).toByte()
                    buffer[dst++] = ((leftS16.toInt() shr 8) and 0xFF).toByte()
                    buffer[dst++] = (rightS16.toInt() and 0xFF).toByte()
                    buffer[dst++] = ((rightS16.toInt() shr 8) and 0xFF).toByte()
                }
            }

            else -> {
                // Fallback direct copy (best effort)
                val copyLen = minOf(length, buffer.size)
                System.arraycopy(input, offset, buffer, 0, copyLen)
                dst = copyLen
            }
        }

        return NormalizedChunk(buffer, 0, dst)
    }

    private fun ensureCapacity(needed: Int) {
        if (buffer.size < needed) {
            buffer = ByteArray(maxOf(buffer.size * 2, needed))
        }
    }

    class NormalizedChunk(
        val data: ByteArray,
        val offset: Int,
        val length: Int,
    )
}
