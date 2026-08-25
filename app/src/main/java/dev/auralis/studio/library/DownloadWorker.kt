package dev.auralis.studio.library

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dev.auralis.studio.online.OnlineTrack
import kotlinx.serialization.json.Json

/**
 * Unique-work name for one remote track: stable across process death.
 * Format: "lyrune_download:<provider>:<providerTrackId>"
 */
fun downloadWorkName(provider: String, providerTrackId: String): String =
    "lyrune_download:$provider:$providerTrackId"

/**
 * Enqueues a unique download for [track]. Repeated taps for the same track
 * reuse/ignore the existing work ([ExistingWorkPolicy.KEEP]) instead of
 * creating a second job.
 *
 * Execution is delegated to [DownloadIngestionManager]; Room stays the
 * canonical state source that the UI observes.
 */
fun enqueueDownload(context: Context, track: OnlineTrack) {
    val request = OneTimeWorkRequestBuilder<DownloadWorker>()
        .setInputData(workDataOf(DownloadWorker.KEY_TRACK_JSON to jsonTrack(track)))
        .build()
    WorkManager.getInstance(context).enqueueUniqueWork(
        downloadWorkName(track.provider, track.id),
        ExistingWorkPolicy.KEEP,
        request,
    )
}

/** Cancels any pending/running unique work for this track (user-initiated cancel). */
fun cancelDownload(context: Context, provider: String, providerTrackId: String) {
    WorkManager.getInstance(context).cancelUniqueWork(downloadWorkName(provider, providerTrackId))
}

private fun jsonTrack(track: OnlineTrack): String =
    Json.encodeToString(OnlineTrackJson.serializer(), OnlineTrackJson.from(track))

internal fun decodeTrack(json: String): OnlineTrack? = runCatching {
    Json.decodeFromString(OnlineTrackJson.serializer(), json).toOnlineTrack()
}.getOrNull()

/** DTO indirection keeps OnlineTrack decoupled from serialization concerns. */
@kotlinx.serialization.Serializable
internal data class OnlineTrackJson(
    val provider: String,
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val albumArtist: String? = null,
    val durationMs: Long,
    val artworkUrl: String,
    val isrc: String? = null,
    val previewUrl: String?,
    val streamUrl: String?,
    val downloadUrl: String?,
    val streamAllowed: Boolean = false,
    val downloadAllowed: Boolean,
    val audioFormat: String? = null,
    val availableDownloadFormats: List<String> = emptyList(),
    val qualityLabel: String? = null,
    val originalFileName: String? = null,
    val license: String,
    val licenseUrl: String = "",
    val sourceUrl: String = "",
) {
    fun toOnlineTrack() = OnlineTrack(
        provider = provider,
        id = id,
        title = title,
        artist = artist,
        album = album,
        albumArtist = albumArtist,
        durationMs = durationMs,
        artworkUrl = artworkUrl,
        isrc = isrc,
        previewUrl = previewUrl,
        streamUrl = streamUrl,
        downloadUrl = downloadUrl,
        streamAllowed = streamAllowed,
        downloadAllowed = downloadAllowed,
        audioFormat = audioFormat,
        availableDownloadFormats = availableDownloadFormats,
        qualityLabel = qualityLabel,
        originalFileName = originalFileName,
        license = license,
        licenseUrl = licenseUrl,
        sourceUrl = sourceUrl,
    )

    companion object {
        fun from(t: OnlineTrack) = OnlineTrackJson(
            provider = t.provider,
            id = t.id,
            title = t.title,
            artist = t.artist,
            album = t.album,
            albumArtist = t.albumArtist,
            durationMs = t.durationMs,
            artworkUrl = t.artworkUrl,
            isrc = t.isrc,
            previewUrl = t.previewUrl,
            streamUrl = t.streamUrl,
            downloadUrl = t.downloadUrl,
            streamAllowed = t.streamAllowed,
            downloadAllowed = t.downloadAllowed,
            audioFormat = t.audioFormat,
            availableDownloadFormats = t.availableDownloadFormats,
            qualityLabel = t.qualityLabel,
            originalFileName = t.originalFileName,
            license = t.license,
            licenseUrl = t.licenseUrl,
            sourceUrl = t.sourceUrl,
        )
    }
}

/**
 * CoroutineWorker executing one unique download.
 *
 * Process-death behavior:
 * - The Room row (status DOWNLOADING) persists across restarts; a restart that
 *   re-enqueues with KEEP reuses the same row via findByKey, so no duplicate
 *   files or rows can appear.
 * - FAILED / COMPLETED statuses live in Room and survive process restarts;
 *   the UI observes them through DownloadsDao flows.
 */
class DownloadWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val json = inputData.getString(KEY_TRACK_JSON)
            ?: return Result.failure(workDataOf(KEY_ERROR to "Missing track payload"))
        val track = decodeTrack(json)
            ?: return Result.failure(workDataOf(KEY_ERROR to "Corrupt track payload"))

        // Preview-only tracks must never enter the pipeline at all.
        if (!track.isDownloadableFullTrack) {
            return Result.failure(workDataOf(KEY_ERROR to PREVIEW_REJECTION))
        }

        // Outcome (success/failure + status) is recorded in Room by the manager;
        // the worker result only controls WorkManager retry semantics.
        val result = DownloadWorkerBridge.manager?.downloadTrack(track)
            ?: return Result.failure(workDataOf(KEY_ERROR to "DownloadIngestionManager not registered"))
        val failure = result.exceptionOrNull() ?: return Result.success()
        return when (failure) {
            is PreviewDownloadRejectedException ->
                // Deterministic rejection — retrying can never succeed.
                Result.failure(workDataOf(KEY_ERROR to PREVIEW_REJECTION))
            else ->
                // Transient failures (network etc.): allow a bounded automatic retry.
                if (runAttemptCount < MAX_AUTO_RETRIES) Result.retry()
                else Result.failure(workDataOf(KEY_ERROR to failure.message))
        }
    }

    companion object {
        const val KEY_TRACK_JSON = "track_json"
        const val KEY_ERROR = "error"
        const val PREVIEW_REJECTION = "preview_rejected"
        const val MAX_AUTO_RETRIES = 2
    }
}

/** Simple service locator set by AppContainer to avoid a hard Koin requirement. */
object DownloadWorkerBridge {
    @Volatile
    var manager: DownloadIngestionManager? = null

    /** Convenience used by the ViewModel path so both entry points share ownership rules. */
    fun attach(manager: DownloadIngestionManager) { this.manager = manager }
}
