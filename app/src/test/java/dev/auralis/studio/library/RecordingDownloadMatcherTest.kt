package dev.auralis.studio.library

import dev.auralis.studio.core.database.DownloadEntity
import dev.auralis.studio.core.database.DownloadStatus
import dev.auralis.studio.online.OnlineTrack
import dev.auralis.studio.online.UnifiedTrackMerger
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordingDownloadMatcherTest {
    @Test
    fun `completed Audius recording marks matching Jamendo source downloaded`() {
        val audius = source("audius", "a1")
        val jamendo = source("jamendo", "j9")
        val unified = UnifiedTrackMerger.merge(listOf(audius, jamendo)).single()
        val row = DownloadEntity(
            provider = "audius",
            providerTrackId = "a1",
            remoteUrl = "https://download.example/a1",
            fileName = "song.flac",
            title = "Song",
            artist = "Artist",
            album = "Album",
            durationMs = 180_000,
            recordingIdentity = unified.recordingIdentity,
            isrc = "USABC2600001",
            status = DownloadStatus.COMPLETED.name,
        )
        assertTrue(RecordingDownloadMatcher.isDownloaded(unified, listOf(row)))
    }

    @Test
    fun `live and remix alternate recordings are NOT marked downloaded when only studio track is downloaded`() {
        val studio = source("audius", "a1", title = "Song")
        val live = source("jamendo", "j2", title = "Song (Live)")
        val remix = source("deezer", "d3", title = "Song - Remix")

        val studioUnified = UnifiedTrackMerger.merge(listOf(studio)).single()
        val liveUnified = UnifiedTrackMerger.merge(listOf(live)).single()
        val remixUnified = UnifiedTrackMerger.merge(listOf(remix)).single()

        val studioRow = DownloadEntity(
            provider = "audius",
            providerTrackId = "a1",
            remoteUrl = "https://download.example/a1",
            fileName = "song.flac",
            title = "Song",
            artist = "Artist",
            album = "Album",
            durationMs = 180_000,
            recordingIdentity = studioUnified.recordingIdentity,
            isrc = "",
            status = DownloadStatus.COMPLETED.name,
        )

        val downloads = listOf(studioRow)
        assertTrue(RecordingDownloadMatcher.isDownloaded(studioUnified, downloads))
        org.junit.Assert.assertFalse(RecordingDownloadMatcher.isDownloaded(liveUnified, downloads))
        org.junit.Assert.assertFalse(RecordingDownloadMatcher.isDownloaded(remixUnified, downloads))
    }

    private fun source(provider: String, id: String, title: String = "Song") = OnlineTrack(
        provider = provider,
        id = id,
        title = title,
        artist = "Artist",
        album = "Album",
        durationMs = 180_000,
        isrc = if (title == "Song") "US-ABC-26-00001" else null,
        downloadUrl = "https://download.example/$id",
        downloadAllowed = true,
    )
}
