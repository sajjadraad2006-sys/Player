package dev.auralis.studio.library

import dev.auralis.studio.core.database.DownloadEntity
import dev.auralis.studio.core.database.DownloadStatus
import dev.auralis.studio.online.UnifiedTrack

object RecordingDownloadMatcher {
    fun status(track: UnifiedTrack, downloads: List<DownloadEntity>): String? = downloads.firstOrNull { row ->
        val derivedId = DownloadIngestionRequest.deriveRecordingIdentity(track.artist, track.title)
        (row.recordingIdentity.isNotBlank() && (row.recordingIdentity == track.recordingIdentity || row.recordingIdentity == derivedId)) ||
            track.sources.any { source ->
                (row.provider.equals(source.provider, true) || row.provider.equals("lrr_${source.provider}", true)) &&
                    row.providerTrackId == source.providerTrackId
            }
    }?.status

    fun isDownloaded(track: UnifiedTrack, downloads: List<DownloadEntity>): Boolean =
        status(track, downloads) == DownloadStatus.COMPLETED.name
}
