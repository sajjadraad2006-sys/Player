package dev.auralis.studio.online

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JamendoClientTest {
    private val client = JamendoClient("fixture-client")

    @Test
    fun `download denied means no download capability even if URL is present`() {
        val mapped = client.mapFixture(dto(allowed = false, url = "https://invalid.example/file"))
        assertFalse(mapped.downloadAllowed)
        assertNull(mapped.downloadUrl)
        assertFalse(mapped.isDownloadableFullTrack)
    }

    @Test
    fun `download allowed with URL maps selected authorized format`() {
        val mapped = client.mapFixture(dto(allowed = true, url = "https://cdn.example/download/1/flac"), "flac")
        assertTrue(mapped.isDownloadableFullTrack)
        assertEquals("flac", mapped.audioFormat)
        assertEquals("FLAC", mapped.qualityLabel)
        assertEquals("https://cdn.example/download/1/flac", mapped.downloadUrl)
    }

    @Test
    fun `quality setting maps only documented Jamendo formats`() {
        assertEquals("flac", client.requestedFormat(DownloadQuality.BEST_AVAILABLE))
        assertEquals("mp32", client.requestedFormat(DownloadQuality.HIGH))
        assertEquals("mp31", client.requestedFormat(DownloadQuality.DATA_SAVER))
    }

    // ── Mock HTTP path: fixture-based search ─────────────────────────────────

    @Test
    fun `fixture search returns downloadable and non-downloadable tracks correctly`() {
        val payload = requireNotNull(javaClass.classLoader?.getResource("jamendo/search_tracks.json")).readText()
        val tracks = client.parseSearchFixture(payload)

        assertEquals(2, tracks.size)

        val downloadable = tracks[0]
        assertEquals("jamendo", downloadable.provider)
        assertEquals("1234567", downloadable.id)
        assertEquals("Fixture Song CC", downloadable.title)
        assertEquals("Fixture Artist", downloadable.artist)
        assertEquals("Fixture Album", downloadable.album)
        assertEquals(214_000L, downloadable.durationMs)
        assertTrue(downloadable.streamAllowed)
        assertFalse(downloadable.streamUrl.isNullOrBlank())
        assertTrue(downloadable.isDownloadableFullTrack)
        assertTrue(downloadable.downloadUrl!!.contains("download/track/1234567/flac"))
        assertEquals("flac", downloadable.audioFormat)
        assertNull(downloadable.previewUrl) // Jamendo streams are full, not previews

        val noDownload = tracks[1]
        assertEquals("7654321", noDownload.id)
        assertTrue(noDownload.streamAllowed)
        assertFalse(noDownload.downloadAllowed)
        assertNull(noDownload.downloadUrl)
        assertFalse(noDownload.isDownloadableFullTrack)
    }

    @Test
    fun `streamUrl and downloadUrl are always distinct for Jamendo`() {
        val mapped = client.mapFixture(dto(allowed = true, url = "https://cdn.example/download/flac"))
        // Stream is separate from download — they must never be the same URL for Jamendo
        assertTrue(mapped.streamUrl != mapped.downloadUrl)
        assertFalse(mapped.streamUrl.isNullOrBlank())
        assertFalse(mapped.downloadUrl.isNullOrBlank())
    }

    @Test
    fun `download allowed but blank URL is not downloadable`() {
        val mapped = client.mapFixture(dto(allowed = true, url = ""))
        assertFalse(mapped.isDownloadableFullTrack)
        assertNull(mapped.downloadUrl)
    }

    @Test
    fun `Jamendo without client id is not configured`() {
        assertFalse(JamendoClient("").isConfigured)
    }

    private fun dto(allowed: Boolean, url: String) = JamendoTrackDto(
        id = "1",
        name = "Song",
        artistName = "Artist",
        albumName = "Album",
        duration = 180,
        image = "https://images.example/1.jpg",
        audio = "https://cdn.example/stream/1",
        audioDownload = url,
        audioDownloadAllowed = allowed,
        license_ccurl = "https://creativecommons.org/licenses/by/4.0/",
        shareurl = "https://www.jamendo.com/track/1",
    )
}
