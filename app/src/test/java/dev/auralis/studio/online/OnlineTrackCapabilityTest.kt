package dev.auralis.studio.online

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression tests for the 29-second preview-download bug.
 *
 * Root cause: iTunes previewUrl / Deezer preview (~30 s promo clips) were mapped
 * as downloadUrl with downloadAllowed=true, so previews were persisted into
 * Music/Lyrune/ as "complete songs".
 */
class OnlineTrackCapabilityTest {

    private fun track(
        provider: String = "itunes",
        previewUrl: String? = null,
        streamUrl: String? = null,
        downloadUrl: String? = null,
        downloadAllowed: Boolean = false,
        durationMs: Long = 219_000L,
    ) = OnlineTrack(
        provider = provider,
        id = "${provider}_123",
        title = "Test Song",
        artist = "Test Artist",
        album = "Test Album",
        durationMs = durationMs,
        artworkUrl = "",
        previewUrl = previewUrl,
        streamUrl = streamUrl,
        downloadUrl = downloadUrl,
        downloadAllowed = downloadAllowed,
        license = "test",
    )

    // ── #42: provider preview protection ────────────────────────────────────

    @Test
    fun `preview-only result is not downloadable`() {
        // Exactly the shape the iTunes Search API returns.
        val t = track(previewUrl = "https://audio.itunes.com/preview.m4a", durationMs = 219_000L)
        assertFalse("Preview must never be offered as a full download", t.isDownloadableFullTrack)
        assertNull(t.downloadUrl)
    }

    @Test
    fun `preview-only result still allows streaming playback`() {
        val t = track(previewUrl = "https://example.com/preview.m4a")
        assertEquals("https://example.com/preview.m4a", t.bestStreamUrl)
    }

    @Test
    fun `full stream is preferred over preview for playback`() {
        val t = track(
            previewUrl = "https://example.com/preview.m4a",
            streamUrl = "https://example.com/full.m4a",
        )
        assertEquals("https://example.com/full.m4a", t.bestStreamUrl)
    }

    // ── #43: full-download provider capability ─────────────────────────────

    @Test
    fun `jamendo authorized download is flagged downloadable`() {
        val t = track(
            provider = "jamendo",
            streamUrl = "https://prod-1.storage.jamendo.com/stream",
            downloadUrl = "https://prod-1.storage.jamendo.com/download.mp3",
            downloadAllowed = true,
        )
        assertTrue(t.isDownloadableFullTrack)
    }

    @Test
    fun `jamendo with audiodownload_allowed false is not downloadable`() {
        val t = track(
            provider = "jamendo",
            streamUrl = "https://stream",
            downloadUrl = null,
            downloadAllowed = false,
        )
        assertFalse(t.isDownloadableFullTrack)
    }

    @Test
    fun `downloadAllowed true with blank url is not downloadable`() {
        val t = track(downloadUrl = "", downloadAllowed = true)
        assertFalse(t.isDownloadableFullTrack)
    }

    // ── #11: stable DownloadKey identity ───────────────────────────────────

    @Test
    fun `downloadKey is provider plus provider id`() {
        val t = track(provider = "jamendo")
        assertEquals("jamendo:jamendo_123", t.downloadKey)
        val other = t.copy(id = "jamendo_456")
        assertTrue(t.downloadKey != other.downloadKey)
    }
}
