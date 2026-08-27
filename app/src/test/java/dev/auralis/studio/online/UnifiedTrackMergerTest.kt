package dev.auralis.studio.online

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UnifiedTrackMergerTest {
    @Test
    fun `same ISRC becomes one recording with both sources`() {
        val merged = UnifiedTrackMerger.merge(listOf(track("audius", isrc = "US-ABC-26-12345"), track("itunes", isrc = "USABC2612345")))
        assertEquals(1, merged.size)
        assertEquals(2, merged.single().sources.size)
        assertEquals("isrc:USABC2612345", merged.single().recordingIdentity)
    }

    @Test
    fun `matching title artist and close duration merges conservatively without ISRC`() {
        val merged = UnifiedTrackMerger.merge(listOf(track("audius", duration = 188_000), track("jamendo", duration = 190_000)))
        assertEquals(1, merged.size)
    }

    @Test
    fun `live remix and studio versions stay separate`() {
        val merged = UnifiedTrackMerger.merge(
            listOf(track("audius"), track("jamendo", title = "Song (Live)"), track("itunes", title = "Song - Remix")),
        )
        assertEquals(3, merged.size)
    }

    @Test
    fun `diacritic and punctuation spelling variation merges only with matching evidence`() {
        val merged = UnifiedTrackMerger.merge(
            listOf(track("itunes", title = "Déjà Vu!", artist = "Artist & Friend"), track("deezer", title = "Deja Vu", artist = "Artist and Friend")),
        )
        assertEquals(1, merged.size)
    }

    @Test
    fun `canonical metadata rejects placeholder artist and prefers complete metadata`() {
        val weak = track("jamendo", artist = "Music", album = "", duration = 0, isrc = "GBABC2600001")
        val trusted = track("itunes", artist = "Real Artist", album = "Real Album", duration = 188_000, isrc = "GBABC2600001")
        val unified = UnifiedTrackMerger.merge(listOf(weak, trusted)).single()
        assertEquals("Real Artist", unified.artist)
        assertEquals("Real Album", unified.album)
        assertEquals(188_000L, unified.durationMs)
        assertTrue(unified.artworkUrl.isNotBlank())
    }

    @Test
    fun `download source selection honors provider formats and user quality`() {
        val audius = track("audius", isrc = "USABC2600002").copy(
            audioFormat = "wav",
            availableDownloadFormats = listOf("original"),
            qualityLabel = "Original",
        )
        val jamendo = track("jamendo", isrc = "USABC2600002").copy(
            audioFormat = "flac",
            availableDownloadFormats = listOf("flac", "mp32", "ogg", "mp31"),
        )
        val unified = UnifiedTrackMerger.merge(listOf(audius, jamendo)).single()
        assertEquals("audius", unified.preferredDownloadSource(DownloadQuality.BEST_AVAILABLE)?.provider)
        assertEquals("jamendo", unified.preferredDownloadSource(DownloadQuality.DATA_SAVER)?.provider)
    }

    @Test
    fun `different artists with same title are NOT merged`() {
        // Two songs titled "Song" by genuinely different artists must stay separate.
        val a = track("audius", artist = "Artist Alpha")
        val b = track("jamendo", artist = "Artist Beta")
        val merged = UnifiedTrackMerger.merge(listOf(a, b))
        assertEquals(2, merged.size)
    }

    @Test
    fun `large duration difference prevents fuzzy merge without ISRC`() {
        // 240 s vs 120 s — more than the tolerance window — must stay separate
        val a = track("audius", duration = 240_000, isrc = null)
        val b = track("jamendo", duration = 120_000, isrc = null)
        val merged = UnifiedTrackMerger.merge(listOf(a, b))
        assertEquals(2, merged.size)
    }

    @Test
    fun `identical ISRC merges when titles differ by display noise but keeps version qualifiers separate`() {
        // Same ISRC + display noise merges
        val a = track("audius", title = "Song (Official Video)", isrc = "USABC2612345")
        val b = track("itunes", title = "Song", isrc = "US-ABC-26-12345")
        val merged = UnifiedTrackMerger.merge(listOf(a, b))
        assertEquals(1, merged.size)
        assertEquals(2, merged.single().sources.size)

        // Same ISRC + conflicting qualifier (ORIGINAL vs RADIO_EDIT) stays separate
        val c = track("deezer", title = "Song (Radio Edit)", isrc = "USABC2612345")
        val mergedWithQualifierConflict = UnifiedTrackMerger.merge(listOf(b, c))
        assertEquals(2, mergedWithQualifierConflict.size)
    }

    @Test
    fun `title match alone without artist evidence does not merge`() {
        // If artist info is blank on one side, don't assume they match
        val a = track("audius", artist = "Real Artist")
        val b = track("jamendo", artist = "Unknown")
        // Artist "Unknown" is a weak value, but "Real Artist" != "Unknown" normalized — must not merge
        // The merger normalizes and compares; this should result in 2 groups
        val merged = UnifiedTrackMerger.merge(listOf(a, b))
        // Artists are different strings — merger must keep them separate
        assertEquals(2, merged.size)
    }

    @Test
    fun `all version qualifier variants stay separate from studio recording`() {
        // Each of these is a distinct recording and must not be collapsed into one.
        // Use unique provider+id per track to avoid downloadKey collisions in merger dedup.
        fun vtrack(provider: String, id: String, title: String, artist: String = "Artist") = OnlineTrack(
            provider = provider, id = id, title = title, artist = artist,
            album = "Album", durationMs = 188_000,
            artworkUrl = "https://images.example/$provider/1000.jpg",
        )
        val studio       = vtrack("audius", "v1", "Song")
        val live         = vtrack("itunes", "v2", "Song (Live)")
        val remix        = vtrack("deezer", "v3", "Song - Remix")
        val acoustic     = vtrack("jamendo", "v4", "Song (Acoustic)")
        val cover        = vtrack("audius", "v5", "Song (Cover)", artist = "Other Artist")
        val instrumental = vtrack("jamendo", "v6", "Song (Instrumental)")
        val merged = UnifiedTrackMerger.merge(listOf(studio, live, remix, acoustic, cover, instrumental))
        // Every version is a distinct recording — none should be merged
        assertEquals(6, merged.size)
    }

    @Test
    fun `source identity is preserved after merge`() {
        // After ISRC merge, each provider source retains its own provider, providerTrackId,
        // streamUrl, downloadUrl, and other identity fields
        val audiusTrack = track("audius", isrc = "USABC2600099").copy(
            streamUrl = "https://stream.example/audius",
            downloadUrl = "https://download.example/audius",
        )
        val itunesTrack = track("itunes", isrc = "US-ABC-26-00099").copy(
            previewUrl = "https://preview.example/itunes",
        )
        val unified = UnifiedTrackMerger.merge(listOf(audiusTrack, itunesTrack)).single()
        val audiusSource = unified.sources.first { it.provider == "audius" } as OnlineRecordingSource
        val itunesSource = unified.sources.first { it.provider == "itunes" } as OnlineRecordingSource
        assertEquals("audius-id", audiusSource.providerTrackId)
        assertEquals("itunes-id", itunesSource.providerTrackId)
        org.junit.Assert.assertNull("Ephemeral streamUrl must not be stored in canonical source", audiusSource.originalTrack?.streamUrl)
        org.junit.Assert.assertNull("Ephemeral downloadUrl must not be stored in canonical source", audiusSource.originalTrack?.downloadUrl)
        org.junit.Assert.assertNull("Ephemeral previewUrl must not be stored in canonical source", itunesSource.originalTrack?.previewUrl)
        assertFalse(itunesSource.isDownloadableFullTrack)
    }

    private fun track(
        provider: String,
        title: String = "Song",
        artist: String = "Artist",
        album: String = "Album",
        duration: Long = 188_000,
        isrc: String? = null,
    ) = OnlineTrack(
        provider = provider,
        id = "$provider-id",
        title = title,
        artist = artist,
        album = album,
        durationMs = duration,
        artworkUrl = "https://images.example/$provider/1000.jpg",
        isrc = isrc,
        previewUrl = if (provider in setOf("itunes", "deezer")) "https://preview.example/$provider" else null,
        streamUrl = if (provider == "audius") "https://stream.example/$provider" else null,
        downloadUrl = if (provider in setOf("audius", "jamendo")) "https://download.example/$provider" else null,
        streamAllowed = provider == "audius",
        downloadAllowed = provider in setOf("audius", "jamendo"),
    )
}
