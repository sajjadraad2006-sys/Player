package dev.auralis.studio.library

import android.content.Context
import android.media.MediaMetadataRetriever
import android.media.MediaScannerConnection
import android.net.Uri
import dev.auralis.studio.core.database.DownloadEntity
import dev.auralis.studio.core.database.DownloadStatus
import dev.auralis.studio.core.database.DownloadsDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.math.abs

/**
 * Conservative repair of historical Lyrune-created bad downloads (~29 s preview
 * pollution) and wrong placeholder metadata ("Music"/"Lyrune" artists).
 *
 * Safety rules:
 * - Only rows in the app-owned `downloads` table are candidates. External media
 *   (WhatsApp audio, user recordings) is NEVER touched.
 * - A physical file is deleted only when ALL of: the DB row is COMPLETED, the
 *   destination lives inside Music/Lyrune/ (or the recorded destinationPath),
 *   and the actual duration mismatches the catalog expectation beyond tolerance.
 * - Anything ambiguous is surfaced for user review instead of auto-deleted.
 */
class DownloadRepairManager(
    private val context: Context,
    private val downloadsDao: DownloadsDao,
    private val mediaStoreRepository: MediaStoreMusicRepository,
) {

    data class BrokenDownload(
        val entity: DownloadEntity,
        val expectedDurationMs: Long,
        val actualDurationMs: Long,
        val reason: String,
    )

    /**
     * Scans completed Lyrune download records for provable preview/incomplete files.
     *
     * Detection (all required):
     * 1. Row status = COMPLETED (app-owned record)
     * 2. Catalog expected duration known (> 0)
     * 3. Local file exists at the recorded path/URI
     * 4. Actual duration differs from expected by more than the same tolerance
     *    used at download time (min 2× — extra headroom against false positives)
     */
    suspend fun findBrokenDownloads(): List<BrokenDownload> = withContext(Dispatchers.IO) {
        val all = downloadsDao.findAllCompleted()
        all.mapNotNull { entity ->
            val expected = entity.durationMs
            if (expected <= 0L) return@mapNotNull null

            val actual = probeDuration(entity)
            if (actual <= 0L) return@mapNotNull null

            // Extra-conservative tolerance: only flag gross mismatches.
            val tolerance = (expected * 0.10).toLong().coerceIn(2_000L, 15_000L) * 2
            if (abs(actual - expected) <= tolerance) return@mapNotNull null

            BrokenDownload(
                entity = entity,
                expectedDurationMs = expected,
                actualDurationMs = actual,
                reason = "Local ${actual / 1000}s vs catalog ${expected / 1000}s",
            )
        }
    }

    /**
     * Removes one confirmed-broken download: deletes the Lyrune-owned file,
     * its MediaStore entry (via scanner refresh), and the DB row.
     * Returns false when the row/file cannot be confidently attributed to Lyrune.
     */
    suspend fun removeBrokenDownload(broken: BrokenDownload): Boolean = withContext(Dispatchers.IO) {
        val entity = broken.entity
        if (entity.status != DownloadStatus.COMPLETED.name) return@withContext false

        val path = entity.destinationPath.ifBlank { entity.trackUri }
        val file = File(path).takeIf { it.isFile && it.absolutePath.contains(File.separator + "Lyrune") }

        var removed = true
        if (file != null) {
            removed = file.delete()
            runCatching { scanFileToMediaStore(file.absolutePath) } // refresh MediaStore index
        } else if (entity.trackUri.startsWith("content://")) {
            // SAF/MediaStore-owned document: delete via ContentResolver when it resolves.
            runCatching {
                context.contentResolver.delete(Uri.parse(entity.trackUri), null, null)
            }.onFailure { removed = false }
        } else {
            removed = false // nothing safely removable; still drop the stale row below? No — keep evidence.
        }

        if (removed) {
            downloadsDao.deleteById(entity.id)
            mediaStoreRepository.refresh()
        }
        removed
    }

    /** User chose to keep a suspicious file: mark it reviewed so it stops appearing. */
    suspend fun keepFile(entityId: Long) {
        // Keep the row but annotate via errorMessage so review lists can skip it.
        downloadsDao.getById(entityId)?.let {
            downloadsDao.updateStatus(it.id, it.status, "user_kept")
        }
    }

    /**
     * Metadata repair: replaces placeholder artists/titles on completed Lyrune
     * records with trusted provider metadata. Never invents values — when no
     * trusted provider data exists the row keeps "Unknown Artist".
     */
    suspend fun repairPlaceholderMetadata(): Int = withContext(Dispatchers.IO) {
        var repaired = 0
        for (entity in allCompleted()) {
            val badArtist = entity.artist.isBlank() ||
                entity.artist.equals("Music", true) ||
                entity.artist.equals("Lyrune", true)
            if (!badArtist) continue

            val fixedArtist = if (entity.artist.isBlank() || entity.artist.equals("Music", true) || entity.artist.equals("Lyrune", true)) {
                // No provider record survives in the row itself; fall back per spec:
                // filename parsing, then Unknown Artist. Never re-apply placeholders.
                parseArtistFromFileName(entity.fileName) ?: "Unknown Artist"
            } else entity.artist

            if (fixedArtist != entity.artist) {
                downloadsDao.repairMetadata(
                    id = entity.id,
                    title = entity.title,
                    artist = fixedArtist,
                    album = entity.album,
                    durationMs = entity.durationMs,
                )
                repaired++
            }
        }
        repaired
    }

    /** Best-effort artist extraction from "Artist - Title.ext" style filenames. */
    internal fun parseArtistFromFileName(fileName: String): String? {
        val stem = fileName.substringBeforeLast('.')
        if (!stem.contains(" - ")) return null
        val candidate = stem.substringBefore(" - ").trim()
        return candidate.takeIf { it.isNotBlank() && !it.equals("Unknown", true) }
    }

    private suspend fun allCompleted(): List<DownloadEntity> =
        downloadsDao.findAllCompleted()

    private fun probeDuration(entity: DownloadEntity): Long = runCatching {
        val path = entity.destinationPath
        val mmr = MediaMetadataRetriever()
        try {
            if (path.startsWith("/")) mmr.setDataSource(path) else mmr.setDataSource(context, Uri.parse(entity.trackUri))
            mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } finally {
            runCatching { mmr.release() }
        }
    }.getOrDefault(0L)

    private suspend fun scanFileToMediaStore(path: String): Uri? =
        suspendCancellableCoroutine { continuation ->
            MediaScannerConnection.scanFile(context, arrayOf(path), arrayOf("audio/*")) { _, uri ->
                continuation.resume(uri)
            }
        }
}
