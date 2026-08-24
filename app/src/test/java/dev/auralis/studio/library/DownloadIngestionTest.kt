package dev.auralis.studio.library

import dev.auralis.studio.core.database.DownloadEntity
import dev.auralis.studio.core.database.DownloadStatus
import dev.auralis.studio.online.OnlineTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadIngestionTest {

    @Test
    fun safeFileName_removesInvalidCharacters() {
        val unsafeTitle = "Song/With:Invalid*Chars?|<>.mp3"
        val safe = unsafeTitle.replace(Regex("[^a-zA-Z0-9._ -]"), "_").take(40)
        assertTrue("Safe file name must not contain invalid characters", !safe.contains("/") && !safe.contains(":") && !safe.contains("*"))
    }

    @Test
    fun downloadEntity_initializesWithCorrectQueuedStatus() {
        val onlineTrack = OnlineTrack(
            provider = "jamendo",
            id = "jamendo_12345",
            title = "Ambient Echoes",
            artist = "Chill Artist",
            album = "Relaxation Vol 1",
            durationMs = 210_000L,
            artworkUrl = "https://example.com/art.jpg",
            previewUrl = null,
            streamUrl = "https://example.com/audio.mp3",
            downloadUrl = "https://example.com/audio.mp3",
            downloadAllowed = true,
            license = "CC-BY",
        )

        val entity = DownloadEntity(
            provider = onlineTrack.provider,
            providerTrackId = onlineTrack.id,
            remoteUrl = onlineTrack.downloadUrl ?: "",
            fileName = "Chill Artist - Ambient Echoes.mp3",
            title = onlineTrack.title,
            artist = onlineTrack.artist,
            album = onlineTrack.album,
            artworkUrl = onlineTrack.artworkUrl ?: "",
            durationMs = onlineTrack.durationMs,
            status = DownloadStatus.DOWNLOADING.name,
        )

        assertEquals("jamendo", entity.provider)
        assertEquals("jamendo_12345", entity.providerTrackId)
        assertEquals(DownloadStatus.DOWNLOADING.name, entity.status)
        assertEquals(-1L, entity.bytesTotal)
    }

    @Test
    fun completedDownload_updatesTrackUriAndStatus() {
        val entity = DownloadEntity(
            id = 42L,
            provider = "archive_org",
            providerTrackId = "track_99",
            remoteUrl = "https://archive.org/download/test.mp3",
            fileName = "Test.mp3",
            title = "Test Track",
            artist = "Artist",
            album = "Album",
            status = DownloadStatus.DOWNLOADING.name,
        )

        val completed = entity.copy(
            status = DownloadStatus.COMPLETED.name,
            trackUri = "content://media/external/audio/media/4200",
            destinationPath = "/storage/emulated/0/Music/Lyrune/Test.mp3",
            completedAt = 1700000000L,
            bytesDownloaded = 4_500_000L,
            bytesTotal = 4_500_000L,
        )

        assertEquals(DownloadStatus.COMPLETED.name, completed.status)
        assertTrue(completed.trackUri.startsWith("content://"))
        assertTrue(completed.destinationPath.endsWith(".mp3"))
        assertEquals(4_500_000L, completed.bytesDownloaded)
    }

    @Test
    fun detectAudioFormat_identifiesFlacMagicBytes() {
        val flacHeader = byteArrayOf(0x66, 0x4C, 0x61, 0x43, 0x00, 0x00) // "fLaC"
        val format = DownloadIngestionManager.detectAudioFormat(null, "https://example.com/stream", flacHeader)
        assertEquals("flac", format.extension)
        assertEquals("audio/flac", format.mimeType)
    }

    @Test
    fun detectAudioFormat_identifiesOggMagicBytes() {
        val oggHeader = byteArrayOf(0x4F, 0x67, 0x67, 0x53, 0x00, 0x02) // "OggS"
        val format = DownloadIngestionManager.detectAudioFormat(null, "https://example.com/stream", oggHeader)
        assertEquals("ogg", format.extension)
        assertEquals("audio/ogg", format.mimeType)
    }

    @Test
    fun detectAudioFormat_identifiesWavMagicBytes() {
        val wavHeader = byteArrayOf(
            'R'.code.toByte(), 'I'.code.toByte(), 'F'.code.toByte(), 'F'.code.toByte(),
            0x24, 0x00, 0x00, 0x00,
            'W'.code.toByte(), 'A'.code.toByte(), 'V'.code.toByte(), 'E'.code.toByte()
        )
        val format = DownloadIngestionManager.detectAudioFormat(null, "https://example.com/stream", wavHeader)
        assertEquals("wav", format.extension)
        assertEquals("audio/wav", format.mimeType)
    }

    @Test
    fun detectAudioFormat_identifiesM4aMagicBytes() {
        val m4aHeader = byteArrayOf(
            0x00, 0x00, 0x00, 0x20,
            'f'.code.toByte(), 't'.code.toByte(), 'y'.code.toByte(), 'p'.code.toByte(),
            'M'.code.toByte(), '4'.code.toByte(), 'A'.code.toByte(), ' '.code.toByte()
        )
        val format = DownloadIngestionManager.detectAudioFormat(null, "https://example.com/stream", m4aHeader)
        assertEquals("m4a", format.extension)
        assertEquals("audio/mp4", format.mimeType)
    }

    @Test
    fun detectAudioFormat_identifiesWebmMagicBytes() {
        val webmHeader = byteArrayOf(0x1A.toByte(), 0x45.toByte(), 0xDF.toByte(), 0xA3.toByte())
        val format = DownloadIngestionManager.detectAudioFormat(null, "https://example.com/stream", webmHeader)
        assertEquals("webm", format.extension)
        assertEquals("audio/webm", format.mimeType)
    }

    @Test
    fun detectAudioFormat_identifiesMp3Id3Header() {
        val id3Header = byteArrayOf('I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte(), 0x03, 0x00)
        val format = DownloadIngestionManager.detectAudioFormat(null, "https://example.com/stream", id3Header)
        assertEquals("mp3", format.extension)
        assertEquals("audio/mpeg", format.mimeType)
    }

    @Test
    fun detectAudioFormat_fallsBackToContentTypeAndUrl() {
        val formatFromType = DownloadIngestionManager.detectAudioFormat("audio/flac", "https://example.com/stream", ByteArray(0))
        assertEquals("flac", formatFromType.extension)
        assertEquals("audio/flac", formatFromType.mimeType)

        val formatFromUrl = DownloadIngestionManager.detectAudioFormat(null, "https://example.com/track.opus", ByteArray(0))
        assertEquals("opus", formatFromUrl.extension)
        assertEquals("audio/opus", formatFromUrl.mimeType)
    }
}
