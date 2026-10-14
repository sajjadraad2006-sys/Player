package dev.auralis.studio.library

import dev.auralis.studio.online.OnlineTrack
import dev.auralis.studio.resolver.api.ResolverDownloadDescriptor

/**
 * Internal neutral ingestion request model that feeds into the single canonical
 * download, validation, MediaStore, and Room persistence pipeline.
 *
 * Adapts both [OnlineTrack] and [ResolverDownloadDescriptor] seamlessly.
 */
data class DownloadIngestionRequest(
    val provider: String,
    val providerTrackId: String,
    val downloadUrl: String,
    val title: String,
    val artist: String? = null,
    val album: String? = null,
    val artworkUrl: String? = null,
    val durationMs: Long = 0L,
    val recordingIdentity: String = deriveRecordingIdentity(artist, title),
    val isrc: String? = null,
    val httpHeaders: Map<String, String> = emptyMap(),
    val expectedFormat: String? = null,
    val qualityLabel: String? = null,
) {
    val downloadKey: String get() = "$provider:$providerTrackId"

    companion object {
        fun deriveRecordingIdentity(artist: String?, title: String): String {
            val cleanArtist = (artist ?: "").trim().lowercase()
            val cleanTitle = title.trim().lowercase()
            return if (cleanArtist.isNotBlank()) "$cleanArtist - $cleanTitle" else cleanTitle
        }

        fun fromOnlineTrack(
            track: OnlineTrack,
            headers: Map<String, String> = emptyMap(),
        ): DownloadIngestionRequest {
            return DownloadIngestionRequest(
                provider = track.provider,
                providerTrackId = track.id,
                downloadUrl = track.downloadUrl ?: error("OnlineTrack downloadUrl must not be null"),
                title = track.title,
                artist = track.artist.takeIf { it.isNotBlank() },
                album = track.album.takeIf { it.isNotBlank() },
                artworkUrl = track.artworkUrl.takeIf { it.isNotBlank() },
                durationMs = track.durationMs,
                recordingIdentity = track.recordingIdentity,
                isrc = track.isrc,
                httpHeaders = headers,
                expectedFormat = track.audioFormat,
                qualityLabel = track.qualityLabel,
            )
        }

        fun fromResolverResource(
            descriptor: ResolverDownloadDescriptor,
            freshDirectUrl: String,
            durationMs: Long? = null,
            safeHeaders: Map<String, String> = emptyMap(),
            formatExtension: String? = null,
            artworkUrl: String? = null,
        ): DownloadIngestionRequest {
            val effectiveArt = artworkUrl?.takeIf { it.isNotBlank() } ?: descriptor.artworkUrl
            return DownloadIngestionRequest(
                provider = "lrr_${descriptor.source.lowercase().trim().replace(Regex("[^a-z0-9_]"), "_")}",
                providerTrackId = descriptor.sourceId ?: ResolverDownloadDescriptor.sha256Hex(descriptor.canonicalUrl.trim()),
                downloadUrl = freshDirectUrl,
                title = descriptor.title,
                artist = descriptor.artist,
                album = descriptor.album,
                artworkUrl = effectiveArt,
                durationMs = durationMs ?: descriptor.expectedDurationMs ?: 0L,
                recordingIdentity = descriptor.recordingIdentity,
                isrc = null,
                httpHeaders = safeHeaders,
                expectedFormat = formatExtension,
                qualityLabel = descriptor.qualityPreference.name,
            )
        }
    }
}
