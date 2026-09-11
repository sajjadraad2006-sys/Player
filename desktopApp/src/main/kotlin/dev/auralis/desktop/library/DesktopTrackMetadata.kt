package dev.auralis.desktop.library

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Shared by folder indexing, Open With, and drag-and-drop playback. */
object DesktopTrackMetadata {
    fun read(file: File, id: Long = file.absolutePath.hashCode().toLong(), folderPath: String = file.parent.orEmpty()): DesktopTrack {
        val ext = file.extension.lowercase()
        val baseName = file.nameWithoutExtension
        val parts = baseName.split(" - ", limit = 2)
        var title = parts.last()
        var artist = if(parts.size == 2) parts.first() else ""
        var album = ""
        var durationMs = 0L

        // Fast targeted metadata extraction without full file loading
        if (ext == "mp3" && file.length() > 128) {
            val id3 = readId3v1(file)
            if (id3 != null) {
                if (id3.title.isNotBlank()) title = id3.title
                if (id3.artist.isNotBlank()) artist = id3.artist
                if (id3.album.isNotBlank()) album = id3.album
            }
            durationMs = (file.length() * 8L * 1000L) / (192_000L)
        } else if (ext == "wav" && file.length() > 44) {
            val wavInfo = readWavHeader(file)
            if (wavInfo != null) durationMs = wavInfo.durationMs
        } else if (ext == "flac" && file.length() > 42) {
            val flacInfo = dev.auralis.desktop.playback.flac.FlacMetadataReader.readMetadata(file)
            if (flacInfo != null) {
                if (flacInfo.title.isNotBlank()) title = flacInfo.title
                if (flacInfo.artist.isNotBlank()) artist = flacInfo.artist
                if (flacInfo.album.isNotBlank()) album = flacInfo.album
                durationMs = flacInfo.durationMs
            }
        }

        val fast = LocalMetadataReader.read(file)
        fast?.title?.takeIf(String::isNotBlank)?.let { title = it }
        fast?.artist?.takeIf(String::isNotBlank)?.let { artist = it }
        fast?.album?.takeIf(String::isNotBlank)?.let { album = it }
        fast?.duration?.takeIf { it > 0 }?.let { durationMs = it }
        val probe = if(ext !in setOf("mp3", "wav", "flac")) DesktopMediaProbe.read(file) else null
        val tags = DesktopMediaProbe.tags(probe)
        tags["title"]?.takeIf(String::isNotBlank)?.let { title = it }
        tags["artist"]?.takeIf(String::isNotBlank)?.let { artist = it }
        tags["album"]?.takeIf(String::isNotBlank)?.let { album = it }
        probe?.get("duration")?.let { it.toString().trim('"').toDoubleOrNull() }?.let { durationMs = (it * 1000).toLong() }
        return DesktopTrack(
            metadataVersion = 1,
            id = id,
            path = file.absolutePath,
            title = title,
            artist = artist,
            album = album,
            durationMs = durationMs.coerceAtLeast(0L),
            sizeBytes = file.length(),
            lastModified = file.lastModified(),
            mimeType = when (ext) {
                "wav" -> "audio/wav"
                "flac" -> "audio/flac"
                "ogg", "opus" -> "audio/ogg"
                "m4a" -> "audio/mp4"
                "aac" -> "audio/aac"
                "webm" -> "audio/webm"
                else -> "audio/mpeg"
            },
            artworkKey = file.absolutePath,
            isFavorite = false,
            folderPath = folderPath,
        )
    }

    private data class Id3v1(val title: String, val artist: String, val album: String)

    private fun readId3v1(file: File): Id3v1? {
        try {
            RandomAccessFile(file, "r").use { raf ->
                val len = raf.length()
                if (len < 128) return null
                raf.seek(len - 128)
                val tag = ByteArray(3)
                raf.readFully(tag)
                if (String(tag) != "TAG") return null

                val titleBuf = ByteArray(30)
                raf.readFully(titleBuf)
                val artistBuf = ByteArray(30)
                raf.readFully(artistBuf)
                val albumBuf = ByteArray(30)
                raf.readFully(albumBuf)

                return Id3v1(
                    title = String(titleBuf).trim('\u0000', ' '),
                    artist = String(artistBuf).trim('\u0000', ' '),
                    album = String(albumBuf).trim('\u0000', ' '),
                )
            }
        } catch (_: Throwable) {
            return null
        }
    }

    private data class WavInfo(val durationMs: Long)

    private fun readWavHeader(file: File): WavInfo? {
        try {
            RandomAccessFile(file, "r").use { raf ->
                val header = ByteArray(44)
                raf.readFully(header)
                val bb = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
                val riff = ByteArray(4)
                bb.get(riff)
                if (String(riff) != "RIFF") return null
                bb.getInt() // size
                val wave = ByteArray(4)
                bb.get(wave)
                if (String(wave) != "WAVE") return null

                bb.position(28)
                val byteRate = bb.getInt()
                val dataSize = file.length() - 44
                val durationMs = if (byteRate > 0) (dataSize * 1000L) / byteRate else 0L
                return WavInfo(durationMs)
            }
        } catch (_: Throwable) {
            return null
        }
    }
}
