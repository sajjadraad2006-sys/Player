package dev.auralis.desktop.library

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Small bounded tag reads for common local formats; expensive container probing is a fallback. */
object LocalMetadataReader {
    data class Metadata(val title: String = "", val artist: String = "", val album: String = "", val duration: Long = 0)
    fun read(file: File): Metadata? = runCatching {
        when(file.extension.lowercase()) { "mp3" -> mp3(file); "wav" -> wav(file); else -> null }
    }.getOrNull()
    private fun mp3(file: File): Metadata {
        val tags = mutableMapOf<String, String>()
        RandomAccessFile(file, "r").use { input ->
            val header = ByteArray(10); input.readFully(header)
            if(String(header, 0, 3, Charsets.US_ASCII) == "ID3") {
                val version = header[3].toInt()
                val size = syncSafe(header, 6)
                if(version in 3..4 && size in 1..4_194_304 && size + 10L <= input.length()) {
                    val data = ByteArray(size); input.readFully(data)
                    var offset = if(header[5].toInt() and 0x40 != 0) {
                        if(version == 4) syncSafe(data, 0) else bigEndian(data, 0) + 4
                    } else 0
                    while(offset >= 0 && offset + 10 <= data.size) {
                        val name = String(data, offset, 4, Charsets.US_ASCII)
                        val length = if(version == 4) syncSafe(data, offset + 4) else bigEndian(data, offset + 4)
                        if(length <= 0 || length > data.size - offset - 10) break
                        if(name in setOf("TIT2", "TPE1", "TALB")) {
                            val encoding = when(data[offset + 10].toInt()) { 1 -> Charsets.UTF_16; 2 -> Charsets.UTF_16BE; 3 -> Charsets.UTF_8; else -> Charsets.ISO_8859_1 }
                            tags[name] = String(data, offset + 11, length - 1, encoding).trim('\u0000', ' ')
                        }
                        offset += 10 + length
                    }
                }
            }
        }
        val duration = runCatching { dev.auralis.desktop.playback.decoder.Mp3AudioDecoder(file).use { it.durationMs } }.getOrDefault(0)
        return Metadata(tags["TIT2"].orEmpty(), tags["TPE1"].orEmpty(), tags["TALB"].orEmpty(), duration)
    }
    private fun wav(file: File): Metadata? = RandomAccessFile(file, "r").use { input ->
        val header = ByteArray(12); input.readFully(header)
        if(String(header, 0, 4) != "RIFF" || String(header, 8, 4) != "WAVE") return null
        var byteRate = 0L; var audioBytes = 0L
        val tags = mutableMapOf<String, String>()
        var chunks = 0
        while(input.filePointer + 8 <= input.length() && chunks++ < 4096) {
            val name = ByteArray(4); input.readFully(name)
            val length = Integer.reverseBytes(input.readInt()).toLong() and 0xFFFFFFFFL
            val start = input.filePointer
            if(length > input.length() - start) break
            when(String(name, Charsets.US_ASCII)) {
                "fmt " -> if(length >= 16) { input.skipBytes(8); byteRate = Integer.reverseBytes(input.readInt()).toLong() and 0xFFFFFFFFL }
                "data" -> audioBytes = length
                "LIST" -> if(length in 4..1_048_576) {
                    val data = ByteArray(length.toInt()); input.readFully(data)
                    if(String(data, 0, 4) == "INFO") {
                        var pos = 4
                        while(pos + 8 <= data.size) {
                            val key = String(data, pos, 4)
                            val size = ByteBuffer.wrap(data, pos + 4, 4).order(ByteOrder.LITTLE_ENDIAN).int
                            if(size < 0 || size > data.size - pos - 8) break
                            tags[key] = String(data, pos + 8, size, Charsets.UTF_8).trim('\u0000', ' ')
                            pos += 8 + size + size % 2
                        }
                    }
                }
            }
            input.seek((start + length + length % 2).coerceAtMost(input.length()))
        }
        Metadata(tags["INAM"].orEmpty(), tags["IART"].orEmpty(), tags["IPRD"].orEmpty(), if(byteRate > 0) audioBytes * 1000 / byteRate else 0)
    }
    private fun syncSafe(data: ByteArray, start: Int) = (0..3).fold(0) { total, i -> (total shl 7) or (data[start + i].toInt() and 127) }
    private fun bigEndian(data: ByteArray, start: Int) = ByteBuffer.wrap(data, start, 4).int
}
