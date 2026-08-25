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

    private fun source(provider: String, id: String) = OnlineTrack(
        provider = provider,
        id = id,
        title = "Song",
        artist = "Artist",
        album = "Album",
        durationMs = 180_000,
        isrc = "US-ABC-26-00001",
        downloadUrl = "https://download.example/$id",
        downloadAllowed = true,
    )
}
