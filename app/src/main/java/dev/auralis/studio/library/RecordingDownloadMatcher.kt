package dev.auralis.studio.library

import dev.auralis.studio.core.database.DownloadEntity
import dev.auralis.studio.core.database.DownloadStatus
import dev.auralis.studio.online.UnifiedTrack

object RecordingDownloadMatcher {
    fun status(track: UnifiedTrack, downloads: List<DownloadEntity>): String? = downloads.firstOrNull { row ->
        (row.recordingIdentity.isNotBlank() && row.recordingIdentity == track.recordingIdentity) ||
            track.sources.any { source -> row.provider == source.provider && row.providerTrackId == source.id }
    }?.status

    fun isDownloaded(track: UnifiedTrack, downloads: List<DownloadEntity>): Boolean =
        status(track, downloads) == DownloadStatus.COMPLETED.name
}
