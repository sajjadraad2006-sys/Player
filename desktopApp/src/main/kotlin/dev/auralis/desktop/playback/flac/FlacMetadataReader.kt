package dev.auralis.desktop.playback.flac

import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets

/**
 * High-performance, memory-bounded metadata parser for FLAC files.
 * Directly parses STREAMINFO, VORBIS_COMMENT, and PICTURE blocks
 * without spawning external processes or buffering full files into RAM.
 */
object FlacMetadataReader {

    private const val MAX_BLOCK_SIZE = 16 * 1024 * 1024 // 16 MB max block size
    private const val MAX_VORBIS_COMMENTS = 500
    private const val MAX_COMMENT_LENGTH = 32 * 1024 // 32 KB per comment
    private const val MAX_PICTURE_BYTES = 8 * 1024 * 1024 // 8 MB max picture size

    data class FlacMetadata(
        val sampleRate: Int,
        val channels: Int,
        val bitsPerSample: Int,
        val totalSamples: Long,
        val durationMs: Long,
        val title: String = "",
        val artist: String = "",
        val album: String = "",
        val albumArtist: String = "",
        val trackNumber: Int = 0,
        val discNumber: Int = 0,
        val genre: String = "",
        val date: String = "",
    )

    /**
     * Reads audio format, duration, and Vorbis tags from [file].
     */
    fun readMetadata(file: File): FlacMetadata? {
        if (!file.exists() || file.length() < 42) return null

        try {
            BufferedInputStream(FileInputStream(file), 16384).use { input ->
                val magic = ByteArray(4)
                if (readFully(input, magic) != 4) return null
                if (magic[0] != 0x66.toByte() || magic[1] != 0x4C.toByte() ||
                    magic[2] != 0x61.toByte() || magic[3] != 0x43.toByte()
                ) {
                    return null // Not "fLaC"
                }

                var isLast = false
                var streamInfoParsed = false
                var sampleRate = 0
                var channels = 0
                var bitsPerSample = 0
                var totalSamples = 0L

                val tags = mutableMapOf<String, String>()

                while (!isLast) {
                    val header = ByteArray(4)
                    if (readFully(input, header) != 4) break

                    val rawHeader0 = header[0].toInt() and 0xFF
                    isLast = (rawHeader0 and 0x80) != 0
                    val blockType = rawHeader0 and 0x7F
                    val blockLength = ((header[1].toInt() and 0xFF) shl 16) or
                        ((header[2].toInt() and 0xFF) shl 8) or
                        (header[3].toInt() and 0xFF)

                    if (blockLength < 0 || blockLength > MAX_BLOCK_SIZE) {
                        break // Corrupted block length
                    }

                    when (blockType) {
                        0 -> { // STREAMINFO (34 bytes)
                            if (blockLength >= 34) {
                                val infoBytes = ByteArray(34)
                                if (readFully(input, infoBytes) != 34) break
                                if (blockLength > 34) skipFully(input, (blockLength - 34).toLong())

                                val b10 = infoBytes[10].toLong() and 0xFF
                                val b11 = infoBytes[11].toLong() and 0xFF
                                val b12 = infoBytes[12].toLong() and 0xFF
                                val b13 = infoBytes[13].toLong() and 0xFF
                                val b14 = infoBytes[14].toLong() and 0xFF
                                val b15 = infoBytes[15].toLong() and 0xFF
                                val b16 = infoBytes[16].toLong() and 0xFF
                                val b17 = infoBytes[17].toLong() and 0xFF

                                val val64 = (b10 shl 56) or (b11 shl 48) or (b12 shl 40) or (b13 shl 32) or
                                    (b14 shl 24) or (b15 shl 16) or (b16 shl 8) or b17

                                sampleRate = ((val64 ushr 44) and 0xFFFFFL).toInt()
                                channels = (((val64 ushr 41) and 0x07L) + 1L).toInt()
                                bitsPerSample = (((val64 ushr 36) and 0x1FL) + 1L).toInt()
                                totalSamples = val64 and 0x0000000FFFFFFFFFL
                                streamInfoParsed = true
                            } else {
                                skipFully(input, blockLength.toLong())
                            }
                        }
                        4 -> { // VORBIS_COMMENT
                            parseVorbisCommentBlock(input, blockLength, tags)
                        }
                        else -> {
                            skipFully(input, blockLength.toLong())
                        }
                    }
                }

                if (!streamInfoParsed || sampleRate <= 0) return null

                val durationMs = if (totalSamples > 0) (totalSamples * 1000L) / sampleRate else 0L

                return FlacMetadata(
                    sampleRate = sampleRate,
                    channels = channels,
                    bitsPerSample = bitsPerSample,
                    totalSamples = totalSamples,
                    durationMs = durationMs,
                    title = tags["TITLE"] ?: "",
                    artist = tags["ARTIST"] ?: "",
                    album = tags["ALBUM"] ?: "",
                    albumArtist = tags["ALBUMARTIST"] ?: tags["ALBUM ARTIST"] ?: "",
                    trackNumber = tags["TRACKNUMBER"]?.toIntOrNull() ?: 0,
                    discNumber = tags["DISCNUMBER"]?.toIntOrNull() ?: 0,
                    genre = tags["GENRE"] ?: "",
                    date = tags["DATE"] ?: tags["YEAR"] ?: "",
                )
            }
        } catch (_: Exception) {
            return null
        }
    }

    /**
     * Extracts raw embedded cover art bytes from the FLAC PICTURE block.
     * Prioritizes front cover (type 3).
     */
    fun extractPicture(file: File): ByteArray? {
        if (!file.exists() || file.length() < 42) return null

        try {
            BufferedInputStream(FileInputStream(file), 16384).use { input ->
                val magic = ByteArray(4)
                if (readFully(input, magic) != 4) return null
                if (magic[0] != 0x66.toByte() || magic[1] != 0x4C.toByte() ||
                    magic[2] != 0x61.toByte() || magic[3] != 0x43.toByte()
                ) {
                    return null
                }

                var isLast = false
                var fallbackPicture: ByteArray? = null

                while (!isLast) {
                    val header = ByteArray(4)
                    if (readFully(input, header) != 4) break

                    val rawHeader0 = header[0].toInt() and 0xFF
                    isLast = (rawHeader0 and 0x80) != 0
                    val blockType = rawHeader0 and 0x7F
                    val blockLength = ((header[1].toInt() and 0xFF) shl 16) or
                        ((header[2].toInt() and 0xFF) shl 8) or
                        (header[3].toInt() and 0xFF)

                    if (blockLength < 0 || blockLength > MAX_BLOCK_SIZE) break

                    if (blockType == 6) { // PICTURE
                        val picData = parsePictureBlock(input, blockLength)
                        if (picData != null) {
                            if (picData.type == 3) {
                                return picData.bytes // Front cover found
                            } else if (fallbackPicture == null) {
                                fallbackPicture = picData.bytes
                            }
                        }
                    } else {
                        skipFully(input, blockLength.toLong())
                    }
                }

                return fallbackPicture
            }
        } catch (_: Exception) {
            return null
        }
    }

    private class PictureResult(val type: Int, val bytes: ByteArray)

    private fun parsePictureBlock(input: InputStream, blockLength: Int): PictureResult? {
        if (blockLength < 32) {
            skipFully(input, blockLength.toLong())
            return null
        }

        var remaining = blockLength

        // 1. Picture type (4 bytes, big endian)
        val picType = readBeInt32(input)
        remaining -= 4

        // 2. MIME type length (4 bytes, big endian)
        val mimeLen = readBeInt32(input)
        remaining -= 4
        if (mimeLen < 0 || mimeLen > remaining) {
            skipFully(input, remaining.toLong())
            return null
        }
        skipFully(input, mimeLen.toLong()) // Skip MIME string
        remaining -= mimeLen

        // 3. Description length (4 bytes, big endian)
        val descLen = readBeInt32(input)
        remaining -= 4
        if (descLen < 0 || descLen > remaining) {
            skipFully(input, remaining.toLong())
            return null
        }
        skipFully(input, descLen.toLong()) // Skip description
        remaining -= descLen

        // 4. Width, height, depth, colors (16 bytes)
        if (remaining < 16) {
            skipFully(input, remaining.toLong())
            return null
        }
        skipFully(input, 16L)
        remaining -= 16

        // 5. Picture data length (4 bytes, big endian)
        val dataLen = readBeInt32(input)
        remaining -= 4

        if (dataLen <= 0 || dataLen > remaining || dataLen > MAX_PICTURE_BYTES) {
            skipFully(input, remaining.toLong())
            return null
        }

        val picBytes = ByteArray(dataLen)
        val read = readFully(input, picBytes)
        remaining -= read
        if (remaining > 0) {
            skipFully(input, remaining.toLong())
        }

        if (read == dataLen) {
            return PictureResult(picType, picBytes)
        }
        return null
    }

    private fun parseVorbisCommentBlock(input: InputStream, blockLength: Int, outTags: MutableMap<String, String>) {
        if (blockLength < 8) {
            skipFully(input, blockLength.toLong())
            return
        }

        var remaining = blockLength

        // 1. Vendor string length (32-bit little-endian)
        val vendorLen = readLeInt32(input)
        remaining -= 4
        if (vendorLen < 0 || vendorLen > remaining) {
            skipFully(input, remaining.toLong())
            return
        }
        skipFully(input, vendorLen.toLong())
        remaining -= vendorLen

        // 2. User comment list length (32-bit little-endian)
        if (remaining < 4) {
            skipFully(input, remaining.toLong())
            return
        }
        val commentCount = readLeInt32(input)
        remaining -= 4

        val boundedCount = minOf(commentCount.coerceAtLeast(0), MAX_VORBIS_COMMENTS)

        for (i in 0 until boundedCount) {
            if (remaining < 4) break
            val commentLen = readLeInt32(input)
            remaining -= 4

            if (commentLen < 0 || commentLen > remaining) break
            if (commentLen > MAX_COMMENT_LENGTH) {
                skipFully(input, commentLen.toLong())
                remaining -= commentLen
                continue
            }

            val commentBytes = ByteArray(commentLen)
            readFully(input, commentBytes)
            remaining -= commentLen

            val commentStr = String(commentBytes, StandardCharsets.UTF_8)
            val eqIdx = commentStr.indexOf('=')
            if (eqIdx > 0) {
                val key = commentStr.substring(0, eqIdx).trim().uppercase()
                val value = commentStr.substring(eqIdx + 1).trim()
                if (!outTags.containsKey(key)) {
                    outTags[key] = value
                }
            }
        }

        if (remaining > 0) {
            skipFully(input, remaining.toLong())
        }
    }

    private fun readBeInt32(input: InputStream): Int {
        val b0 = input.read()
        val b1 = input.read()
        val b2 = input.read()
        val b3 = input.read()
        if (b0 or b1 or b2 or b3 < 0) return -1
        return (b0 shl 24) or (b1 shl 16) or (b2 shl 8) or b3
    }

    private fun readLeInt32(input: InputStream): Int {
        val b0 = input.read()
        val b1 = input.read()
        val b2 = input.read()
        val b3 = input.read()
        if (b0 or b1 or b2 or b3 < 0) return -1
        return b0 or (b1 shl 8) or (b2 shl 16) or (b3 shl 24)
    }

    private fun readFully(input: InputStream, buffer: ByteArray): Int {
        var total = 0
        while (total < buffer.size) {
            val count = input.read(buffer, total, buffer.size - total)
            if (count < 0) break
            total += count
        }
        return total
    }

    private fun skipFully(input: InputStream, bytes: Long) {
        var remaining = bytes
        while (remaining > 0) {
            val skipped = input.skip(remaining)
            if (skipped <= 0) {
                if (input.read() < 0) break
                remaining--
            } else {
                remaining -= skipped
            }
        }
    }
}
