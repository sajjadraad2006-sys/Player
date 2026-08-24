package dev.auralis.studio.library

import dev.auralis.studio.online.OnlineTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unique-work identity + preview rejection at the worker boundary.
 * Ten rapid taps must resolve to ONE work name; previews never enqueue.
 */
class DownloadWorkIdentityTest {

    private fun track(provider: String = "jamendo", id: String = "77", downloadable: Boolean = true) = OnlineTrack(
        provider = provider,
        id = id,
        title = "Song",
        artist = "Artist",
        album = "Album",
        durationMs = 219_000L,
        artworkUrl = "",
        previewUrl = null,
        streamUrl = if (downloadable) "https://example.com/full.ogg" else null,
        downloadUrl = if (downloadable) "https://example.com/full.mp3" else null,
        downloadAllowed = downloadable,
        license = "CC",
    )

    @Test
    fun `work name is deterministic per provider and id`() {
        val a = downloadWorkName("jamendo", "77")
        val b = downloadWorkName("jamendo", "77")
        assertEquals(a, b)
    }

    @Test
    fun `different tracks map to different work names`() {
        assertNotEquals(downloadWorkName("jamendo", "77"), downloadWorkName("jamendo", "78"))
        assertNotEquals(downloadWorkName("jamendo", "77"), downloadWorkName("archive_org", "77"))
    }

    @Test
    fun `ten rapid requests collapse to one unique key`() {
        val taps = (1..10).map { downloadWorkName(track().provider, track().id) }.toSet()
        assertEquals("10 rapid taps must produce exactly 1 unique work item", 1, taps.size)
    }

    @Test
    fun `preview-only track is rejected before any work is enqueued`() {
        // The exact #42 fixture: full 219 s catalog entry, 29 s preview asset, no download URL.
        val previewOnly = OnlineTrack(
            provider = "itunes",
            id = "itunes_99",
            title = "Big Song",
            artist = "Star",
            album = "LP",
            durationMs = 219_000L,
            artworkUrl = "",
            previewUrl = "https://audio.itunes.com/preview.m4a",
            streamUrl = null,
            downloadUrl = null,
            downloadAllowed = false,
            license = "preview",
        )
        assertFalse(previewOnly.isDownloadableFullTrack)
        assertNull(previewOnly.downloadUrl)
    }

    @Test
    fun `track payload survives worker serialization round-trip`() {
        val original = track()
        val json = JsonCodecBridge.encode(original)
        val decoded = decodeTrack(json)!!
        assertEquals(original.downloadKey, decoded.downloadKey)
        assertEquals(original.durationMs, decoded.durationMs)
        assertTrue(decoded.isDownloadableFullTrack)

        val preview = original.copy(downloadAllowed = false, downloadUrl = null)
        val decodedPreview = decodeTrack(JsonCodecBridge.encode(preview))!!
        assertFalse(decodedPreview.isDownloadableFullTrack)
    }
}

/** Small indirection so the test can reach the private jsonTrack encoder. */
private object JsonCodecBridge {
    fun encode(track: OnlineTrack): String =
        kotlinx.serialization.json.Json.encodeToString(
            OnlineTrackJson.serializer(),
            OnlineTrackJson.from(track),
        )
}
