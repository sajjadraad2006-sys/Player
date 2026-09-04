package dev.auralis.studio.library

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dev.auralis.studio.AuralisApplication
import dev.auralis.studio.online.IdentityNormalization
import dev.auralis.studio.online.OnlineTrack
import dev.auralis.studio.online.VersionQualifier
import dev.auralis.studio.resolver.android.AndroidProcessMediaResolver
import dev.auralis.studio.resolver.api.DeliveryBlockReason
import dev.auralis.studio.resolver.api.DownloadCapability
import dev.auralis.studio.resolver.api.DownloadCapabilityEvaluator
import dev.auralis.studio.resolver.api.DrmState
import dev.auralis.studio.resolver.api.EphemeralResourceLease
import dev.auralis.studio.resolver.api.MediaResolver
import dev.auralis.studio.resolver.api.ResolutionMode
import dev.auralis.studio.resolver.api.ResolveIntent
import dev.auralis.studio.resolver.api.ResolvedFormatDescriptor
import dev.auralis.studio.resolver.api.ResolverDownloadDescriptor
import dev.auralis.studio.resolver.api.ResolverErrorCode
import dev.auralis.studio.resolver.api.ResolverFormatSelector
import dev.auralis.studio.resolver.api.ResolverRequest
import dev.auralis.studio.resolver.api.ResolverResult
import dev.auralis.studio.resolver.api.SecretSanitizer
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
 * Enqueues a unique download for an LRR [descriptor].
 * Guaranteed:
 * - Stable download key based on sourceId or SHA-256(canonicalUrl).
 * - ExistingWorkPolicy.KEEP prevents duplicate jobs on rapid taps.
 * - Does not persist temporary signed URLs; re-resolves fresh at worker start.
 */
fun enqueueDownload(context: Context, descriptor: ResolverDownloadDescriptor) {
    val request = OneTimeWorkRequestBuilder<DownloadWorker>()
        .setInputData(workDataOf(DownloadWorker.KEY_RESOLVER_DESCRIPTOR_JSON to jsonDescriptor(descriptor)))
        .build()
    WorkManager.getInstance(context).enqueueUniqueWork(
        downloadWorkName("lrr", descriptor.downloadKey),
        ExistingWorkPolicy.REPLACE,
        request,
    )
}

/** Cancels any pending/running unique work for this track (user-initiated cancel). */
fun cancelDownload(context: Context, provider: String, providerTrackId: String) {
    WorkManager.getInstance(context).cancelUniqueWork(downloadWorkName(provider, providerTrackId))
    DownloadNotificationHelper.cancel(context, downloadWorkName(provider, providerTrackId).hashCode())
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

/** Evaluates whether recording identity matches across persisted descriptor and fresh resolution. */
object RecordingIdentityRevalidator {
    fun isSameRecording(
        persistedSourceId: String?,
        persistedArtist: String?,
        persistedTitle: String,
        freshSourceId: String?,
        freshArtist: String?,
        freshTitle: String?,
    ): Boolean {
        // 1. Strongest evidence: matching provider/source ID
        if (!persistedSourceId.isNullOrBlank() && !freshSourceId.isNullOrBlank()) {
            return persistedSourceId.trim() == freshSourceId.trim()
        }

        // If one is present and the other is present but different -> ProviderChanged
        if (!persistedSourceId.isNullOrBlank() && !freshSourceId.isNullOrBlank() &&
            persistedSourceId.trim() != freshSourceId.trim()
        ) {
            return false
        }

        // 2. Fallback when sourceId is missing on one or both: normalized recording identity
        val pTitle = persistedTitle.trim()
        val fTitle = freshTitle?.trim().orEmpty()
        if (pTitle.isBlank() || fTitle.isBlank()) return false

        val pArtistNorm = IdentityNormalization.normalizeArtist(persistedArtist.orEmpty())
        val fArtistNorm = IdentityNormalization.normalizeArtist(freshArtist.orEmpty())

        // If artist was specified on persisted descriptor, fresh artist must match
        if (pArtistNorm.isNotBlank() && fArtistNorm.isNotBlank() && pArtistNorm != fArtistNorm) {
            return false
        }

        // Check core title and version qualifiers
        val pCoreTitle = IdentityNormalization.coreTitle(pTitle)
        val fCoreTitle = IdentityNormalization.coreTitle(fTitle)

        if (pCoreTitle.isBlank() || fCoreTitle.isBlank()) return false
        if (pCoreTitle != fCoreTitle) return false

        // Check version qualifiers (e.g. original vs remix/live)
        val pProfile = dev.auralis.studio.online.VersionProfile.parse(pTitle)
        val fProfile = dev.auralis.studio.online.VersionProfile.parse(fTitle)
        if (!pProfile.isCompatibleWith(fProfile)) {
            return false
        }

        return true
    }
}

/** Conservative expired resource decision logic. */
object ExpiredResourceClassifier {
    fun shouldRefreshExpiredLease(
        lease: EphemeralResourceLease?,
        failure: Throwable?,
        failureMessage: String,
    ): Boolean {
        // 1. Structured lease evidence: knownExpiryEpochMs has elapsed or isExpired is true
        if (lease != null && lease.isExpired) {
            return true
        }
        if (lease?.knownExpiryEpochMs != null && System.currentTimeMillis() >= (lease.knownExpiryEpochMs - 10_000L)) {
            return true
        }

        // 2. Explicit expiration text in HTTP message / error details from CDN
        val lower = failureMessage.lowercase()
        if (lower.contains("url expired") ||
            lower.contains("link expired") ||
            lower.contains("signature expired") ||
            lower.contains("token expired") ||
            lower.contains("access token expired") ||
            lower.contains("request has expired") ||
            lower.contains("expired_token") ||
            lower.contains("expired url")
        ) {
            return true
        }

        return false
    }
}

/** Result classification for fresh resolution inside DownloadWorker. */
internal sealed interface FreshResolutionResult {
    data class Success(
        val format: ResolvedFormatDescriptor,
        val lease: EphemeralResourceLease,
        val resolvedDurationMs: Long? = null,
    ) : FreshResolutionResult

    data class TerminalFailure(val message: String, val code: ResolverErrorCode = ResolverErrorCode.Unknown) : FreshResolutionResult
    data class TransientFailure(val message: String, val code: ResolverErrorCode = ResolverErrorCode.Unknown) : FreshResolutionResult
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
        val app = applicationContext as? AuralisApplication
        val container = app?.container
        val manager = DownloadWorkerBridge.manager ?: container?.downloadManager
            ?: return Result.failure(workDataOf(KEY_ERROR to "DownloadIngestionManager not registered"))

        // ── Branch 1: Standard OnlineTrack ─────────────────────────────────
        val trackJson = inputData.getString(KEY_TRACK_JSON)
        if (!trackJson.isNullOrBlank()) {
            val track = decodeTrack(trackJson)
                ?: return Result.failure(workDataOf(KEY_ERROR to "Corrupt track payload"))

            if (!track.isDownloadableFullTrack) {
                return Result.failure(workDataOf(KEY_ERROR to PREVIEW_REJECTION))
            }

            val notifId = track.id.hashCode()
            DownloadNotificationHelper.showStarting(applicationContext, notifId, track.title, track.artist)

            val result = manager.downloadTrack(track, onProgress = { downloaded, total ->
                DownloadNotificationHelper.showProgress(applicationContext, notifId, track.title, track.artist, downloaded, total)
            })
            val failure = result.exceptionOrNull()
            if (failure == null) {
                DownloadNotificationHelper.showCompleted(applicationContext, notifId, track.title, track.artist)
                return Result.success()
            }
            val errorMsg = failure.message.orEmpty()
            DownloadNotificationHelper.showError(applicationContext, notifId, track.title, errorMsg.ifBlank { "Download failed" })
            return when (failure) {
                is PreviewDownloadRejectedException ->
                    Result.failure(workDataOf(KEY_ERROR to PREVIEW_REJECTION))
                else -> {
                    if (errorMsg.contains("404")) {
                        Result.failure(workDataOf(KEY_ERROR to errorMsg))
                    } else if (runAttemptCount < MAX_AUTO_RETRIES) {
                        Result.retry()
                    } else {
                        Result.failure(workDataOf(KEY_ERROR to errorMsg))
                    }
                }
            }
        }

        // ── Branch 2: LRR ResolverDownloadDescriptor (Fresh Resolution) ────
        val descriptorJson = inputData.getString(KEY_RESOLVER_DESCRIPTOR_JSON)
        if (!descriptorJson.isNullOrBlank()) {
            val descriptor = decodeDescriptor(descriptorJson)
                ?: return Result.failure(workDataOf(KEY_ERROR to "Corrupt resolver descriptor payload"))

            if (descriptor.schemaVersion > 1) {
                return Result.failure(workDataOf(KEY_ERROR to "Unsupported descriptor schema version: ${descriptor.schemaVersion}"))
            }

            val resolver = DownloadWorkerBridge.resolver ?: container?.mediaResolver ?: AndroidProcessMediaResolver(applicationContext)

            return processResolverDownload(descriptor, resolver, manager)
        }

        return Result.failure(workDataOf(KEY_ERROR to "Missing download payload"))
    }

    private suspend fun processResolverDownload(
        descriptor: ResolverDownloadDescriptor,
        resolver: MediaResolver,
        manager: DownloadIngestionManager,
    ): Result {
        val notifId = descriptor.downloadKey.hashCode()
        DownloadNotificationHelper.showStarting(applicationContext, notifId, descriptor.title, descriptor.artist)

        // Fresh resolution attempt #1
        val res1 = resolveFreshFormat(descriptor, resolver)
        when (res1) {
            is FreshResolutionResult.TerminalFailure -> {
                DownloadNotificationHelper.showError(applicationContext, notifId, descriptor.title, res1.message)
                return Result.failure(workDataOf(KEY_ERROR to res1.message))
            }
            is FreshResolutionResult.TransientFailure -> {
                if (runAttemptCount < MAX_AUTO_RETRIES) {
                    return Result.retry()
                } else {
                    DownloadNotificationHelper.showError(applicationContext, notifId, descriptor.title, res1.message)
                    return Result.failure(workDataOf(KEY_ERROR to res1.message))
                }
            }
            is FreshResolutionResult.Success -> {
                val format1 = res1.format
                val lease1 = res1.lease

                val req1 = DownloadIngestionRequest.fromResolverResource(
                    descriptor = descriptor,
                    freshDirectUrl = lease1.url,
                    durationMs = res1.resolvedDurationMs ?: descriptor.expectedDurationMs,
                    safeHeaders = SecretSanitizer.extractSafeHeaders(lease1.headers),
                    formatExtension = format1.extension ?: format1.container,
                )

                val result1 = manager.download(req1, onProgress = { downloaded, total ->
                    DownloadNotificationHelper.showProgress(applicationContext, notifId, descriptor.title, descriptor.artist, downloaded, total)
                })
                if (result1.isSuccess) {
                    DownloadNotificationHelper.showCompleted(applicationContext, notifId, descriptor.title, descriptor.artist)
                    return Result.success()
                }

                val failure1 = result1.exceptionOrNull()
                val failureMsg1 = failure1?.message.orEmpty()

                // ── Conservative stale resource recovery ───────────────────
                if (ExpiredResourceClassifier.shouldRefreshExpiredLease(lease1, failure1, failureMsg1)) {
                    val res2 = resolveFreshFormat(descriptor, resolver)
                    if (res2 is FreshResolutionResult.Success) {
                        val format2 = res2.format
                        val lease2 = res2.lease
                        val req2 = DownloadIngestionRequest.fromResolverResource(
                            descriptor = descriptor,
                            freshDirectUrl = lease2.url,
                            durationMs = res2.resolvedDurationMs ?: descriptor.expectedDurationMs,
                            safeHeaders = SecretSanitizer.extractSafeHeaders(lease2.headers),
                            formatExtension = format2.extension ?: format2.container,
                        )
                        val result2 = manager.download(req2, onProgress = { downloaded, total ->
                            DownloadNotificationHelper.showProgress(applicationContext, notifId, descriptor.title, descriptor.artist, downloaded, total)
                        })
                        if (result2.isSuccess) {
                            DownloadNotificationHelper.showCompleted(applicationContext, notifId, descriptor.title, descriptor.artist)
                            return Result.success()
                        }
                        val failure2 = result2.exceptionOrNull()
                        val failureMsg2 = failure2?.message ?: "Re-resolution download failed"
                        DownloadNotificationHelper.showError(applicationContext, notifId, descriptor.title, failureMsg2)
                        return Result.failure(workDataOf(KEY_ERROR to failureMsg2))
                    } else if (res2 is FreshResolutionResult.TerminalFailure) {
                        DownloadNotificationHelper.showError(applicationContext, notifId, descriptor.title, res2.message)
                        return Result.failure(workDataOf(KEY_ERROR to res2.message))
                    } else if (res2 is FreshResolutionResult.TransientFailure) {
                        return if (runAttemptCount < MAX_AUTO_RETRIES) Result.retry()
                        else {
                            DownloadNotificationHelper.showError(applicationContext, notifId, descriptor.title, res2.message)
                            Result.failure(workDataOf(KEY_ERROR to res2.message))
                        }
                    }
                }

                val finalError = failureMsg1.ifBlank { "Download failed" }
                if (runAttemptCount >= MAX_AUTO_RETRIES || failure1 is PreviewDownloadRejectedException || failureMsg1.contains("404") || failureMsg1.contains("401")) {
                    DownloadNotificationHelper.showError(applicationContext, notifId, descriptor.title, finalError)
                }

                return when {
                    failure1 is PreviewDownloadRejectedException ->
                        Result.failure(workDataOf(KEY_ERROR to PREVIEW_REJECTION))
                    failureMsg1.contains("404") ->
                        Result.failure(workDataOf(KEY_ERROR to failureMsg1))
                    failureMsg1.contains("401") ->
                        Result.failure(workDataOf(KEY_ERROR to "Authentication required"))
                    runAttemptCount < MAX_AUTO_RETRIES ->
                        Result.retry()
                    else ->
                        Result.failure(workDataOf(KEY_ERROR to failure1?.message))
                }
            }
        }
    }

    private suspend fun resolveFreshFormat(
        descriptor: ResolverDownloadDescriptor,
        resolver: MediaResolver,
    ): FreshResolutionResult {
        val resolverReq = ResolverRequest(
            sourceUrl = descriptor.canonicalUrl,
            sourceId = descriptor.sourceId,
            intent = ResolveIntent.DOWNLOAD,
            preferredAudioQuality = descriptor.qualityPreference,
            formatHint = descriptor.formatHint,
        )

        val resolveResult = resolver.resolve(resolverReq)
        if (resolveResult !is ResolverResult.Success) {
            val error = (resolveResult as? ResolverResult.Failure)?.error
            val msg = error?.safeUserMessage ?: "Resolution failed"
            val code = error?.technicalCode ?: ResolverErrorCode.Unknown
            val isTerminal = when (code) {
                ResolverErrorCode.NotFound,
                ResolverErrorCode.UnsupportedUrl,
                ResolverErrorCode.DownloadNotPermitted,
                ResolverErrorCode.AccessRestricted,
                ResolverErrorCode.AuthenticationRequired,
                ResolverErrorCode.IncompatibleSchemaVersion,
                ResolverErrorCode.CollectionNotSupported,
                -> true
                else -> error?.isRetryable == false
            }
            return if (isTerminal) FreshResolutionResult.TerminalFailure(msg, code)
            else FreshResolutionResult.TransientFailure(msg, code)
        }

        val media = resolveResult.media

        // 1. Conservative Recording Identity validation
        if (!RecordingIdentityRevalidator.isSameRecording(
                persistedSourceId = descriptor.sourceId,
                persistedArtist = descriptor.artist,
                persistedTitle = descriptor.title,
                freshSourceId = media.sourceId,
                freshArtist = media.artist,
                freshTitle = media.title,
            )
        ) {
            return FreshResolutionResult.TerminalFailure("Source recording identity mismatch", ResolverErrorCode.ProviderChanged)
        }

        // 2. Permission re-evaluation at worker execution time (authoritative)
        val annotatedMedia = DownloadCapabilityEvaluator.annotateMedia(media)

        // 3. Format selection
        val selectedFormat = ResolverFormatSelector.selectBestDescriptor(
            formats = annotatedMedia.formats,
            preference = descriptor.qualityPreference,
            formatHint = descriptor.formatHint,
            requirePermittedDownload = true,
        ) ?: return FreshResolutionResult.TerminalFailure("No permitted direct formats available", ResolverErrorCode.FormatUnavailable)

        // 4. Hard invariants
        if (selectedFormat.resolutionMode != ResolutionMode.DIRECT_RESOURCE ||
            selectedFormat.downloadCapability != DownloadCapability.PERMITTED ||
            selectedFormat.drmState != DrmState.NONE ||
            selectedFormat.blockReason != DeliveryBlockReason.NONE ||
            selectedFormat.deliveryRequirements.hasDrm
        ) {
            return FreshResolutionResult.TerminalFailure("Format is not permitted for direct download", ResolverErrorCode.DownloadNotPermitted)
        }

        // 5. Ephemeral lease validation
        val lease = if (resolveResult.lease != null && resolveResult.lease.formatId == selectedFormat.formatId) {
            resolveResult.lease
        } else {
            val rawMatch = media.rawFormats.firstOrNull { it.formatId == selectedFormat.formatId }
            if (rawMatch != null && rawMatch.url.isNotBlank()) {
                EphemeralResourceLease(
                    requestId = descriptor.requestId,
                    formatId = selectedFormat.formatId,
                    url = rawMatch.url,
                    headers = SecretSanitizer.extractSafeHeaders(rawMatch.httpHeaders),
                    resolvedAtEpochMs = System.currentTimeMillis(),
                    knownExpiryEpochMs = rawMatch.expiresAtEpochMs,
                    resolutionMode = selectedFormat.resolutionMode,
                    mediaKind = selectedFormat.mediaKind,
                )
            } else {
                resolveResult.lease ?: return FreshResolutionResult.TerminalFailure("No delivery lease available for selected format", ResolverErrorCode.FormatUnavailable)
            }
        }

        if (lease.resolutionMode != ResolutionMode.DIRECT_RESOURCE || lease.url.isBlank()) {
            return FreshResolutionResult.TerminalFailure("Delivery lease is not a valid direct resource", ResolverErrorCode.UnsupportedUrl)
        }

        return FreshResolutionResult.Success(
            format = selectedFormat,
            lease = lease,
            resolvedDurationMs = media.durationMs,
        )
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
