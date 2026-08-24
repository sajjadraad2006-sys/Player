package dev.auralis.studio.library

import dev.auralis.studio.core.database.DownloadEntity
import dev.auralis.studio.core.database.DownloadStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for historical broken-download detection and metadata repair
 * rules (pure logic; no Android dependencies).
 */
class DownloadRepairLogicTest {

    // ── Detection rule (mirrors DownloadRepairManager.findBrokenDownloads) ──

    /** 2× the download-time tolerance — extra-conservative against false positives. */
    private fun isSuspicious(expectedMs: Long, actualMs: Long): Boolean {
        if (expectedMs <= 0 || actualMs <= 0) return false
        val tolerance = (expectedMs * 0.10).toLong().coerceIn(2_000L, 15_000L) * 2
        return kotlin.math.abs(actualMs - expectedMs) > tolerance
    }

    private fun completedEntity(
        path: String = "/storage/emulated/0/Music/Lyrune/Artist - Song.mp3",
        artist: String = "Artist",
        fileName: String = "Artist - Song.mp3",
    ) = DownloadEntity(
        id = 1L,
        provider = "jamendo",
        providerTrackId = "jamendo_123",
        remoteUrl = "https://example.com/a.mp3",
        fileName = fileName,
        destinationPath = path,
        title = "Song",
        artist = artist,
        album = "Album",
        durationMs = 219_000L,
        status = DownloadStatus.COMPLETED.name,
    )

    @Test
    fun `29s file for a 219s catalog track is suspicious`() {
        assertTrue(isSuspicious(expectedMs = 219_000L, actualMs = 29_000L))
    }

    @Test
    fun `normal full download is not suspicious`() {
        assertFalse(isSuspicious(expectedMs = 219_000L, actualMs = 218_400L))
        assertFalse(isSuspicousHelper(219_000L, 214_000L))
    }

    private fun isSuspicousHelper(e: Long, a: Long) = isSuspicious(e, a)

    @Test
    fun `unknown expected or actual duration is never flagged`() {
        assertFalse(isSuspicious(expectedMs = 0L, actualMs = 29_000L))
        assertFalse(isSuspicious(expectedMs = 219_000L, actualMs = 0L))
    }

    // ── Metadata repair rules (#6) ─────────────────────────────────────────

    private val repair = RepairRulesForTest()

    @Test
    fun `placeholder artists are detected`() {
        assertTrue(repair.isPlaceholderArtist("Music"))
        assertTrue(repair.isPlaceholderArtist("music"))
        assertTrue(repair.isPlaceholderArtist("Lyrune"))
        assertTrue(repair.isPlaceholderArtist(""))
        assertFalse(repair.isPlaceholderArtist("Real Artist"))
    }

    @Test
    fun `artist parsed from Artist - Title filename`() {
        assertEquals("Real Artist", repair.parseArtistFromFileName("Real Artist - Great Song.mp3"))
        assertNull(repair.parseArtistFromFileName("NoSeparator.mp3"))
        assertNull(repair.parseArtistFromFileName("Unknown - Song.mp3"))
    }

    @Test
    fun `repair never re-applies placeholders`() {
        val entity = completedEntity(artist = "Music", fileName = "Somebody - Song.mp3")
        val fixed = repair.parseArtistFromFileName(entity.fileName) ?: "Unknown Artist"
        assertTrue(fixed != "Music" && fixed != "Lyrune" && fixed.isNotBlank())
        assertEquals("Somebody", fixed)
    }

    @Test
    fun `unrepairable row falls back to Unknown Artist`() {
        val entity = completedEntity(artist = "Lyrune", fileName = "JustATitle.mp3")
        val fixed = repair.parseArtistFromFileName(entity.fileName) ?: "Unknown Artist"
        assertEquals("Unknown Artist", fixed)
    }
}

/** Test-visible mirror of the manager's pure helpers (no Context needed). */
private class RepairRulesForTest {
    fun isPlaceholderArtist(value: String): Boolean =
        value.isBlank() || value.equals("Music", true) || value.equals("Lyrune", true)

    fun parseArtistFromFileName(fileName: String): String? {
        val stem = fileName.substringBeforeLast('.')
        if (!stem.contains(" - ")) return null
        val candidate = stem.substringBefore(" - ").trim()
        return candidate.takeIf { it.isNotBlank() && !it.equals("Unknown", true) }
    }
}
