package dev.auralis.studio.library

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dev.auralis.studio.online.OnlineTrack
import dev.auralis.studio.resolver.android.AndroidProcessMediaResolver
import dev.auralis.studio.resolver.api.DownloadCapability
import dev.auralis.studio.resolver.api.MediaResolver
import dev.auralis.studio.resolver.api.ResolutionMode
import dev.auralis.studio.resolver.api.ResolverDownloadDescriptor
import dev.auralis.studio.resolver.api.ResolverFormatSelector
import dev.auralis.studio.resolver.api.ResolverRequest
import dev.auralis.studio.resolver.api.ResolverResult
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

/**
 * Enqueues a unique download for a permitted LRR [descriptor].
 * Guaranteed:
 * - Stable download key based on sourceId or SHA-256(canonicalUrl).
 * - ExistingWorkPolicy.KEEP prevents duplicate jobs on rapid taps.
 * - Does not persist temporary signed URLs; re-resolves fresh at worker start.
 */
fun enqueueDownload(context: Context, descriptor: ResolverDownloadDescriptor) {
    if (descriptor.downloadCapability != DownloadCapability.PERMITTED) {
        return
    }
    val request = OneTimeWorkRequestBuilder<DownloadWorker>()
        .setInputData(workDataOf(DownloadWorker.KEY_RESOLVER_DESCRIPTOR_JSON to jsonDescriptor(descriptor)))
        .build()
    WorkManager.getInstance(context).enqueueUniqueWork(
        downloadWorkName("lrr", descriptor.downloadKey),
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

private fun jsonDescriptor(descriptor: ResolverDownloadDescriptor): String =
    Json.encodeToString(ResolverDownloadDescriptor.serializer(), descriptor)

internal fun decodeDescriptor(json: String): ResolverDownloadDescriptor? = runCatching {
    Json.decodeFromString(ResolverDownloadDescriptor.serializer(), json)
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
 * Supports both standard [OnlineTrack] and LRR [ResolverDownloadDescriptor].
 * Fresh resolution is strictly enforced at worker execution time for LRR descriptors.
 */
class DownloadWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val manager = DownloadWorkerBridge.manager
            ?: return Result.failure(workDataOf(KEY_ERROR to "DownloadIngestionManager not registered"))

        // ── Branch 1: Standard OnlineTrack ─────────────────────────────────
        val trackJson = inputData.getString(KEY_TRACK_JSON)
        if (!trackJson.isNullOrBlank()) {
            val track = decodeTrack(trackJson)
                ?: return Result.failure(workDataOf(KEY_ERROR to "Corrupt track payload"))

            if (!track.isDownloadableFullTrack) {
                return Result.failure(workDataOf(KEY_ERROR to PREVIEW_REJECTION))
            }

            val result = manager.downloadTrack(track)
            val failure = result.exceptionOrNull() ?: return Result.success()
            return when (failure) {
                is PreviewDownloadRejectedException ->
                    Result.failure(workDataOf(KEY_ERROR to PREVIEW_REJECTION))
                else ->
                    if (runAttemptCount < MAX_AUTO_RETRIES) Result.retry()
                    else Result.failure(workDataOf(KEY_ERROR to failure.message))
            }
        }

        // ── Branch 2: LRR ResolverDownloadDescriptor (Fresh Resolution) ────
        val descriptorJson = inputData.getString(KEY_RESOLVER_DESCRIPTOR_JSON)
        if (!descriptorJson.isNullOrBlank()) {
            val descriptor = decodeDescriptor(descriptorJson)
                ?: return Result.failure(workDataOf(KEY_ERROR to "Corrupt resolver descriptor payload"))

            if (descriptor.downloadCapability != DownloadCapability.PERMITTED) {
                return Result.failure(workDataOf(KEY_ERROR to "Download permission not granted for this resource"))
            }

            val resolver = DownloadWorkerBridge.resolver ?: AndroidProcessMediaResolver(applicationContext)

            return processResolverDownload(descriptor, resolver, manager)
        }

        return Result.failure(workDataOf(KEY_ERROR to "Missing download payload"))
    }

    private suspend fun processResolverDownload(
        descriptor: ResolverDownloadDescriptor,
        resolver: MediaResolver,
        manager: DownloadIngestionManager,
    ): Result {
        // Fresh resolution attempt #1
        val res1 = resolveFreshFormat(descriptor, resolver)
            ?: return if (runAttemptCount < MAX_AUTO_RETRIES) Result.retry()
            else Result.failure(workDataOf(KEY_ERROR to "Fresh resolution failed to find permitted direct format"))

        val (format1, safeHeaders1) = res1

        val req1 = DownloadIngestionRequest.fromResolverResource(
            descriptor = descriptor,
            freshDirectUrl = format1.url,
            durationMs = descriptor.expectedDurationMs,
            safeHeaders = safeHeaders1,
            formatExtension = format1.extension,
        )

        val result1 = manager.download(req1)
        if (result1.isSuccess) {
            return Result.success()
        }

        val failure1 = result1.exceptionOrNull()
        val failureMsg1 = failure1?.message.orEmpty()

        // ── One-shot stale resource recovery on HTTP 403 / expired token ─────
        if (failureMsg1.contains("403") || failureMsg1.contains("expired", ignoreCase = true)) {
            val res2 = resolveFreshFormat(descriptor, resolver)
            if (res2 != null) {
                val (format2, safeHeaders2) = res2
                val req2 = DownloadIngestionRequest.fromResolverResource(
                    descriptor = descriptor,
                    freshDirectUrl = format2.url,
                    durationMs = descriptor.expectedDurationMs,
                    safeHeaders = safeHeaders2,
                    formatExtension = format2.extension,
                )
                val result2 = manager.download(req2)
                if (result2.isSuccess) {
                    return Result.success()
                }
                val failure2 = result2.exceptionOrNull()
                return Result.failure(workDataOf(KEY_ERROR to (failure2?.message ?: "Re-resolution download failed")))
            }
        }

        return when (failure1) {
            is PreviewDownloadRejectedException ->
                Result.failure(workDataOf(KEY_ERROR to PREVIEW_REJECTION))
            else ->
                if (runAttemptCount < MAX_AUTO_RETRIES) Result.retry()
                else Result.failure(workDataOf(KEY_ERROR to failure1?.message))
        }
    }

    private suspend fun resolveFreshFormat(
        descriptor: ResolverDownloadDescriptor,
        resolver: MediaResolver,
    ): Pair<dev.auralis.studio.resolver.api.ResolvedFormat, Map<String, String>>? {
        val resolverReq = ResolverRequest(
            sourceUrl = descriptor.canonicalUrl,
            sourceId = descriptor.sourceId,
            preferredAudioQuality = descriptor.qualityPreference,
        )

        val resolveResult = resolver.resolve(resolverReq)
        if (resolveResult !is ResolverResult.Success) {
            return null
        }

        val media = resolveResult.media
        val selectedFormat = ResolverFormatSelector.selectBestAudioFormat(
            formats = media.formats,
            preference = descriptor.qualityPreference,
            formatHint = descriptor.formatHint,
        ) ?: return null

        if (selectedFormat.resolutionMode != ResolutionMode.DIRECT_RESOURCE ||
            selectedFormat.downloadCapability != DownloadCapability.PERMITTED
        ) {
            return null
        }

        // Strict allowlist for safe ephemeral HTTP headers (never Cookie / Authorization / Range)
        val safeHeaders = selectedFormat.httpHeaders.filterKeys { key ->
            key.equals("User-Agent", ignoreCase = true) ||
                key.equals("Referer", ignoreCase = true) ||
                key.equals("Origin", ignoreCase = true) ||
                key.equals("Accept", ignoreCase = true)
        }

        return Pair(selectedFormat, safeHeaders)
    }

    companion object {
        const val KEY_TRACK_JSON = "track_json"
        const val KEY_RESOLVER_DESCRIPTOR_JSON = "resolver_descriptor_json"
        const val KEY_ERROR = "error"
        const val PREVIEW_REJECTION = "preview_rejected"
        const val MAX_AUTO_RETRIES = 2
    }
}

/** Service locator set by AppContainer to avoid tight coupling. */
object DownloadWorkerBridge {
    @Volatile
    var manager: DownloadIngestionManager? = null

    @Volatile
    var resolver: MediaResolver? = null

    fun attach(manager: DownloadIngestionManager) {
        this.manager = manager
    }

    fun attachResolver(resolver: MediaResolver) {
        this.resolver = resolver
    }
}
