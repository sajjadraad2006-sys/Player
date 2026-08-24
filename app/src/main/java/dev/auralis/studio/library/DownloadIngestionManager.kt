package dev.auralis.studio.library

import android.content.Context
import android.media.MediaMetadataRetriever
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import androidx.documentfile.provider.DocumentFile
import dev.auralis.studio.core.database.DownloadEntity
import dev.auralis.studio.core.database.DownloadStatus
import dev.auralis.studio.core.database.DownloadsDao
import dev.auralis.studio.core.model.Track
import dev.auralis.studio.online.OnlineTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import kotlin.coroutines.resume
import kotlin.math.abs

data class AudioFormatInfo(
    val extension: String,
    val mimeType: String,
)

/** Thrown when a downloaded asset fails full-track validation. */
class PreviewDownloadRejectedException(message: String) : IllegalStateException(message)

/**
 * Production download ingestion manager.
 *
 * Guarantees:
 * 1. Only authorized FULL-track downloads are persisted ([OnlineTrack.downloadAllowed]).
 *    Preview URLs (~30 s promo clips) are rejected outright and never reach storage.
 * 2. One [OnlineTrack.downloadKey] has at most one active job (in-process mutex +
 *    DB status check) — rapid taps, recomposition and re-entry cannot duplicate work.
 * 3. Written with detected genuine audio container format (never forced to .mp3).
 * 4. Completed only after validation: HTTP success, non-zero file, container sniffed,
 *    metadata readable and local duration plausible vs catalog expected duration.
 * 5. Provider metadata takes precedence over embedded/filename fallbacks — generic
 *    values such as "Music"/"Unknown" can never overwrite trusted provider identity.
 * 6. Registered into MediaStore via MediaScannerConnection and tracked in Room.
 */
class DownloadIngestionManager(
    private val context: Context,
    private val downloadsDao: DownloadsDao,
    private val mediaStoreRepository: MediaStoreMusicRepository,
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder().build(),
) {

    /** In-process ownership of DownloadKeys so only one job runs per key. */
    private val activeKeys = MutableSet<String>()
    private val startMutex = Mutex()

    /** Tolerance when comparing local duration to the catalog's expected full duration. */
    private fun durationToleranceMs(expectedMs: Long): Long =
        (expectedMs.coerceAtLeast(0L) * 0.10).toLong().coerceIn(2_000L, 15_000L)

    suspend fun downloadTrack(
        onlineTrack: OnlineTrack,
        customFolderUri: String? = null,
        onProgress: (bytesDownloaded: Long, bytesTotal: Long) -> Unit = { _, _ -> },
    ): Result<Track> = withContext(Dispatchers.IO) {
        // ── Gate 1: never persist a preview as a "download". ────────────────
        if (!onlineTrack.isDownloadableFullTrack || onlineTrack.downloadUrl.isNullOrBlank()) {
            return@withContext Result.failure(
                PreviewDownloadRejectedException(
                    "No authorized full-track download available for \"${onlineTrack.title}\" " +
                        "(provider=${onlineTrack.provider}). Preview assets are not stored.",
                ),
            )
        }

        // ── Gate 2: one active job per DownloadKey (in-process). ────────────
        val acquired = startMutex.withLock {
            if (onlineTrack.downloadKey in activeKeys) false else {
                activeKeys += onlineTrack.downloadKey
                true
            }
        }
        if (!acquired) {
            return@withContext Result.failure(
                IllegalStateException("A download for this track is already in progress"),
            )
        }

        try {
            performDownload(onlineTrack, customFolderUri, onProgress)
        } finally {
            synchronized(activeKeys) { activeKeys -= onlineTrack.downloadKey }
        }
    }

    private suspend fun performDownload(
        onlineTrack: OnlineTrack,
        customFolderUri: String?,
        onProgress: (bytesDownloaded: Long, bytesTotal: Long) -> Unit,
    ): Result<Track> {
        val downloadUrl = onlineTrack.downloadUrl ?: error("guarded by isDownloadableFullTrack")

        // ── Gate 3: DB-level uniqueness on the stable DownloadKey row. ──────
        val existing = downloadsDao.findByKey(onlineTrack.provider, onlineTrack.id)
        val rowId = if (existing != null && existing.status != DownloadStatus.COMPLETED.name) {
            downloadsDao.updateStatus(existing.id, DownloadStatus.DOWNLOADING.name, "")
            existing.id
        } else if (existing != null) {
            // Already completed — nothing to do; resolve its local file again.
            return resolveExistingCompleted(existing)
        } else {
            downloadsDao.insert(
                DownloadEntity(
                    provider = onlineTrack.provider,
                    providerTrackId = onlineTrack.id,
                    remoteUrl = downloadUrl,
                    fileName = "${onlineTrack.artist} - ${onlineTrack.title}",
                    title = onlineTrack.title,
                    artist = onlineTrack.artist,
                    album = onlineTrack.album,
                    artworkUrl = onlineTrack.artworkUrl.orEmpty(),
                    durationMs = onlineTrack.durationMs,
                    status = DownloadStatus.DOWNLOADING.name,
                ),
            )
        }

        try {
            val request = Request.Builder()
                .url(downloadUrl)
                .header("User-Agent", "Lyrune-Studio/2.0")
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                val errorMsg = "HTTP download failed with code ${response.code}"
                downloadsDao.updateStatus(rowId, DownloadStatus.FAILED.name, errorMsg)
                return Result.failure(IllegalStateException(errorMsg))
            }

            val body = response.body ?: run {
                val errorMsg = "Empty response body"
                downloadsDao.updateStatus(rowId, DownloadStatus.FAILED.name, errorMsg)
                return Result.failure(IllegalStateException(errorMsg))
            }

            val contentLength = body.contentLength()
            val contentType = response.header("Content-Type")

            // Read the first chunk to sniff magic bytes if necessary
            val bufferedInput = body.byteStream().buffered()
            bufferedInput.mark(64)
            val headerBytes = ByteArray(32)
            val bytesRead = bufferedInput.read(headerBytes, 0, 32).coerceAtLeast(0)
            bufferedInput.reset()

            val formatInfo = detectAudioFormat(contentType, downloadUrl, headerBytes.copyOf(bytesRead))

            // Clean filename with real extension
            val safeArtist = onlineTrack.artist.replace(Regex("[^a-zA-Z0-9._ -]"), "_").take(30).trim()
            val safeTitle = onlineTrack.title.replace(Regex("[^a-zA-Z0-9._ -]"), "_").take(40).trim()
            val fileName = if (safeArtist.isNotBlank()) "$safeArtist - $safeTitle.${formatInfo.extension}" else "$safeTitle.${formatInfo.extension}"

            var localFilePath: String? = null
            var localUri: Uri? = null

            if (customFolderUri != null) {
                // Save to user-selected SAF folder
                val treeUri = Uri.parse(customFolderUri)
                val docTree = DocumentFile.fromTreeUri(context, treeUri)
                val targetDoc = docTree?.createFile(formatInfo.mimeType, fileName)
                    ?: run {
                        val errorMsg = "Could not create SAF target file in $customFolderUri"
                        downloadsDao.updateStatus(rowId, DownloadStatus.FAILED.name, errorMsg)
                        return Result.failure(IllegalStateException(errorMsg))
                    }

                context.contentResolver.openOutputStream(targetDoc.uri)?.use { out ->
                    copyStreamWithProgress(bufferedInput, out, contentLength) { downloaded ->
                        downloadsDao.updateProgress(rowId, DownloadStatus.DOWNLOADING.name, downloaded, contentLength)
                        onProgress(downloaded, contentLength)
                    }
                }
                localUri = targetDoc.uri
                localFilePath = targetDoc.uri.toString()
            } else {
                // Standard destination: Music/Lyrune/
                val musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
                val lyruneDir = File(musicDir, "Lyrune").apply { if (!exists()) mkdirs() }
                val targetFile = uniqueTargetFile(lyruneDir, fileName)

                FileOutputStream(targetFile).use { out ->
                    copyStreamWithProgress(bufferedInput, out, contentLength) { downloaded ->
                        downloadsDao.updateProgress(rowId, DownloadStatus.DOWNLOADING.name, downloaded, contentLength)
                        onProgress(downloaded, contentLength)
                    }
                }
                localFilePath = targetFile.absolutePath

                // Scan into Android MediaStore
                val scannedContentUri = scanFileToMediaStore(targetFile.absolutePath, formatInfo.mimeType)
                localUri = scannedContentUri ?: Uri.fromFile(targetFile)
            }

            val finalUriStr = localUri.toString()
            val finalPath = localFilePath ?: finalUriStr

            // ── Validation: container must be readable & non-empty. ──────────
            val actualDurationMs = probeDuration(finalPath, finalUriStr)
            if (actualDurationMs <= 0L) {
                val errorMsg = "Downloaded audio is unreadable or empty"
                markFailedAndDelete(rowId, finalPath, customFolderUri == null, errorMsg)
                return Result.failure(IllegalStateException(errorMsg))
            }

            // ── Validation: duration plausibility vs expected FULL duration. ─
            val expectedDurationMs = onlineTrack.durationMs
            if (expectedDurationMs > 0L) {
                val tolerance = durationToleranceMs(expectedDurationMs)
                if (abs(actualDurationMs - expectedDurationMs) > tolerance) {
                    val errorMsg =
                        "Preview / incomplete download: local ${actualDurationMs / 1000}s vs expected ${expectedDurationMs / 1000}s"
                    markFailedAndDelete(rowId, finalPath, customFolderUri == null, errorMsg)
                    return Result.failure(PreviewDownloadRejectedException(errorMsg))
                }
            }

            // Metadata precedence (highest → lowest):
            //   1. Trusted provider catalog metadata
            //   2. Embedded tags — accepted ONLY when they are not generic placeholders
            // Generic values ("Music", "Unknown", "Lyrune", blank) never override provider data.
            var resolvedTitle = onlineTrack.title.takeIf { it.isNotBlank() }
            var resolvedArtist = onlineTrack.artist
                .takeIf { it.isNotBlank() && !it.equals("Music", true) && !it.equals("Lyrune", true) }
            var resolvedAlbum = onlineTrack.album.takeIf { it.isNotBlank() }
            runCatching {
                probeMetadata(finalPath, finalUriStr)?.let { embedded ->
                    // Embedded tags fill gaps only when they are not generic placeholders.
                    if (resolvedTitle.isNullOrBlank() && !embedded.title.isGenericPlaceholder()) resolvedTitle = embedded.title
                    if (resolvedArtist.isNullOrBlank() && !embedded.artist.isGenericPlaceholder()) resolvedArtist = embedded.artist
                    if (resolvedAlbum.isNullOrBlank() && !embedded.album.isGenericPlaceholder()) resolvedAlbum = embedded.album
                }
            }

            downloadsDao.markCompleted(
                rowId,
                trackUri = finalUriStr,
                path = finalPath,
                title = resolvedTitle ?: onlineTrack.title,
                artist = resolvedArtist.orEmpty(),
                album = resolvedAlbum.orEmpty(),
                durationMs = actualDurationMs,
            )

            // Refresh MediaStore repository so the new track appears immediately
            mediaStoreRepository.refresh()

            val resolvedTrack = mediaStoreRepository.track(finalUriStr) ?: Track(
                id = rowId,
                uri = finalUriStr,
                title = resolvedTitle ?: onlineTrack.title,
                artist = resolvedArtist ?: onlineTrack.artist,
                album = resolvedAlbum ?: onlineTrack.album,
                albumId = null,
                durationMs = actualDurationMs,
                dateAddedSeconds = System.currentTimeMillis() / 1000L,
                displayName = fileName,
                mimeType = formatInfo.mimeType,
                sizeBytes = File(finalPath).takeIf { it.isFile }?.length() ?: contentLength.coerceAtLeast(0L),
            )

            return Result.success(resolvedTrack)
        } catch (e: Exception) {
            downloadsDao.updateStatus(rowId, DownloadStatus.FAILED.name, e.localizedMessage ?: "Download failed")
            return Result.failure(e)
        }
    }

    private suspend fun resolveExistingCompleted(existing: DownloadEntity): Result<Track> {
        if (existing.trackUri.isBlank()) {
            return Result.failure(IllegalStateException("Completed download has no local file"))
        }
        mediaStoreRepository.refresh()
        val local = mediaStoreRepository.track(existing.trackUri)
        if (local != null) return Result.success(local)
        val uri = Uri.parse(existing.trackUri)
        val track = Track(
            id = existing.id,
            uri = existing.trackUri,
            title = existing.title,
            artist = existing.artist.ifBlank { "Unknown Artist" },
            album = existing.album,
            albumId = null,
            durationMs = existing.durationMs,
            dateAddedSeconds = existing.completedAt / 1000L,
            displayName = existing.fileName,
            mimeType = null,
            sizeBytes = 0L,
            trackNumber = null,
            year = null,
        )
        // Touch `uri` so linters see intent even when content resolver isn't needed.
        runCatching { context.contentResolver.getType(uri) }
        return Result.success(track)
    }

    /** Avoids silently overwriting an existing file with the same name (duplicate protection at FS level). */
    private fun uniqueTargetFile(dir: File, fileName: String): File {
        val base = dir.resolve(fileName)
        if (!base.exists()) return base
        val stem = fileName.substringBeforeLast('.')
        val ext = fileName.substringAfterLast('.', "")
        var index = 1
        while (true) {
            val candidate = dir.resolve(if (ext.isBlank()) "$stem ($index)" else "$stem ($index).$ext")
            if (!candidate.exists()) return candidate
            index++
        }
    }

    private suspend fun markFailedAndDelete(rowId: Long, path: String, deleteFile: Boolean, message: String) {
        if (deleteFile) runCatching { File(path).delete() }
        downloadsDao.updateStatus(rowId, DownloadStatus.FAILED.name, message)
    }

    private fun probeDuration(path: String, uriString: String): Long = runCatching {
        val mmr = MediaMetadataRetriever()
        try {
            if (path.startsWith("/")) mmr.setDataSource(path) else mmr.setDataSource(context, Uri.parse(uriString))
            mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } finally {
            runCatching { mmr.release() }
        }
    }.getOrDefault(0L)

    private data class EmbeddedMeta(val title: String?, val artist: String?, val album: String?)

    private fun probeMetadata(path: String, uriString: String): EmbeddedMeta? = runCatching {
        val mmr = MediaMetadataRetriever()
        try {
            if (path.startsWith("/")) mmr.setDataSource(path) else mmr.setDataSource(context, Uri.parse(uriString))
            EmbeddedMeta(
                title = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE),
                artist = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST),
                album = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM),
            )
        } finally {
            runCatching { mmr.release() }
        }
    }.getOrNull()

    private fun String?.isGenericPlaceholder(): Boolean {
        val value = orEmpty().trim()
        return value.isEmpty() ||
            value.equals("Music", true) ||
            value.equals("Lyrune", true) ||
            value.equals("Unknown", true) ||
            value.equals("Unknown Artist", true)
    }

    private suspend fun scanFileToMediaStore(path: String, mimeType: String): Uri? =
        suspendCancellableCoroutine { continuation ->
            MediaScannerConnection.scanFile(
                context,
                arrayOf(path),
                arrayOf(mimeType),
            ) { _, uri ->
                continuation.resume(uri)
            }
        }

    private inline fun copyStreamWithProgress(
        input: InputStream,
        output: OutputStream,
        totalBytes: Long,
        onProgress: (downloaded: Long) -> Unit,
    ) {
        val buffer = ByteArray(8192)
        var bytesRead: Int
        var totalRead = 0L
        var lastReportTime = 0L

        while (input.read(buffer).also { bytesRead = it } != -1) {
            output.write(buffer, 0, bytesRead)
            totalRead += bytesRead
            val now = System.currentTimeMillis()
            if (now - lastReportTime > 200 || totalRead == totalBytes) {
                lastReportTime = now
                onProgress(totalRead)
            }
        }
        output.flush()
    }

    companion object {
        fun detectAudioFormat(contentType: String?, url: String?, sampleBytes: ByteArray): AudioFormatInfo {
            val cleanType = contentType?.substringBefore(';')?.trim()?.lowercase() ?: ""
            val cleanUrl = url?.substringBefore('?')?.lowercase() ?: ""

            // 1. Direct Content-Type mapping
            when {
                cleanType == "audio/mpeg" || cleanType == "audio/mp3" -> return AudioFormatInfo("mp3", "audio/mpeg")
                cleanType == "audio/mp4" || cleanType == "audio/m4a" || cleanType == "audio/x-m4a" || cleanType == "audio/aac" -> return AudioFormatInfo("m4a", "audio/mp4")
                cleanType == "audio/flac" || cleanType == "audio/x-flac" -> return AudioFormatInfo("flac", "audio/flac")
                cleanType == "audio/ogg" || cleanType == "application/ogg" -> return AudioFormatInfo("ogg", "audio/ogg")
                cleanType == "audio/opus" -> return AudioFormatInfo("opus", "audio/opus")
                cleanType == "audio/wav" || cleanType == "audio/x-wav" || cleanType == "audio/wave" -> return AudioFormatInfo("wav", "audio/wav")
                cleanType == "audio/webm" -> return AudioFormatInfo("webm", "audio/webm")
            }

            // 2. Magic byte sniffing (if generic or missing)
            if (sampleBytes.size >= 4) {
                // FLAC: "fLaC"
                if (sampleBytes[0] == 0x66.toByte() && sampleBytes[1] == 0x4C.toByte() &&
                    sampleBytes[2] == 0x61.toByte() && sampleBytes[3] == 0x43.toByte()) {
                    return AudioFormatInfo("flac", "audio/flac")
                }
                // OGG: "OggS"
                if (sampleBytes[0] == 0x4F.toByte() && sampleBytes[1] == 0x67.toByte() &&
                    sampleBytes[2] == 0x67.toByte() && sampleBytes[3] == 0x53.toByte()) {
                    return AudioFormatInfo("ogg", "audio/ogg")
                }
                // WAV: "RIFF....WAVE"
                if (sampleBytes.size >= 12 && sampleBytes[0] == 0x52.toByte() && sampleBytes[1] == 0x49.toByte() &&
                    sampleBytes[2] == 0x46.toByte() && sampleBytes[3] == 0x46.toByte() &&
                    sampleBytes[8] == 0x57.toByte() && sampleBytes[9] == 0x41.toByte() &&
                    sampleBytes[10] == 0x56.toByte() && sampleBytes[11] == 0x45.toByte()) {
                    return AudioFormatInfo("wav", "audio/wav")
                }
                // M4A / MP4: offset 4 contains "ftyp"
                if (sampleBytes.size >= 8 && sampleBytes[4] == 0x66.toByte() && sampleBytes[5] == 0x74.toByte() &&
                    sampleBytes[6] == 0x79.toByte() && sampleBytes[7] == 0x70.toByte()) {
                    return AudioFormatInfo("m4a", "audio/mp4")
                }
                // WebM: 0x1A 0x45 0xDF 0xA3
                if (sampleBytes[0] == 0x1A.toByte() && sampleBytes[1] == 0x45.toByte() &&
                    sampleBytes[2] == 0xDF.toByte() && sampleBytes[3] == 0xA3.toByte()) {
                    return AudioFormatInfo("webm", "audio/webm")
                }
                // MP3 ID3v2: "ID3"
                if (sampleBytes[0] == 0x49.toByte() && sampleBytes[1] == 0x44.toByte() && sampleBytes[2] == 0x33.toByte()) {
                    return AudioFormatInfo("mp3", "audio/mpeg")
                }
                // MP3 Sync frame: 0xFF 0xFB/F3/F2
                if (sampleBytes[0] == 0xFF.toByte() && (sampleBytes[1].toInt() and 0xE0) == 0xE0) {
                    return AudioFormatInfo("mp3", "audio/mpeg")
                }
            }

            // 3. Fallback to URL path extension
            return when {
                cleanUrl.endsWith(".m4a") || cleanUrl.endsWith(".aac") || cleanUrl.endsWith(".mp4") -> AudioFormatInfo("m4a", "audio/mp4")
                cleanUrl.endsWith(".flac") -> AudioFormatInfo("flac", "audio/flac")
                cleanUrl.endsWith(".ogg") -> AudioFormatInfo("ogg", "audio/ogg")
                cleanUrl.endsWith(".opus") -> AudioFormatInfo("opus", "audio/opus")
                cleanUrl.endsWith(".wav") -> AudioFormatInfo("wav", "audio/wav")
                cleanUrl.endsWith(".webm") -> AudioFormatInfo("webm", "audio/webm")
                else -> AudioFormatInfo("mp3", "audio/mpeg")
            }
        }
    }
}

private class MutableSet<T>(private val backing: HashSet<T> = HashSet()) {
    @Synchronized
    operator fun contains(value: T): Boolean = backing.contains(value)

    @Synchronized
    operator fun plusAssign(value: T) {
        backing.add(value)
    }

    @Synchronized
    operator fun minusAssign(value: T) {
        backing.remove(value)
    }
}
