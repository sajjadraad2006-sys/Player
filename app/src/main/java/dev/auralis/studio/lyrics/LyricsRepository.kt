package dev.auralis.studio.lyrics

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import dev.auralis.studio.core.database.LyricsCacheEntity
import dev.auralis.studio.core.database.LyricsDao
import dev.auralis.studio.core.model.LyricsSource
import dev.auralis.studio.core.model.SyncedLyrics
import dev.auralis.studio.core.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private const val TAG = "LyricsRepository"

/**
 * Unified lyrics resolution pipeline.
 *
 * Resolution order (Embedded first, network last):
 * 1. Embedded metadata lyrics (LYRICS or UNSYNCEDLYRICS tags)
 * 2. User-selected / imported local .lrc (manualSelection = true in cache)
 * 3. Auto-matched local .lrc file alongside audio
 * 4. Valid cached online lyrics (from previous network fetch)
 * 5. LRCLIB online lookup
 *
 * All network access is optional. Steps 1–4 work fully offline.
 */
class LyricsRepository(
    private val context: Context,
    private val dao: LyricsDao,
    private val lrclib: LrclibClient,
) {

    /**
     * Resolves lyrics for [track], following the offline-first pipeline.
     * Returns [SyncedLyrics.EMPTY] if nothing is available.
     */
    suspend fun resolve(track: Track): SyncedLyrics = withContext(Dispatchers.IO) {
        // ── Step 1: Embedded metadata ──────────────────────────────────────
        val embedded = readEmbeddedLyrics(track)
        if (embedded != null) {
            Log.d(TAG, "Using embedded lyrics for ${track.title}")
            return@withContext embedded
        }

        // ── Step 2 & 3: Local .lrc — manual first, then auto ──────────────
        val cachedManual = dao.getLyrics(track.uri)?.takeIf { it.manualSelection }
        if (cachedManual != null) {
            Log.d(TAG, "Using manually selected local/cache lyrics for ${track.title}")
            return@withContext parseCachedEntity(cachedManual)
        }

        val localLrc = findLocalLrc(track)
        if (localLrc != null) {
            Log.d(TAG, "Using local .lrc file for ${track.title}")
            return@withContext localLrc
        }

        // ── Step 4: Valid online cache ─────────────────────────────────────
        val cached = dao.getLyrics(track.uri)
        if (cached != null && isCacheValid(cached)) {
            Log.d(TAG, "Using cached LRCLIB lyrics for ${track.title}")
            return@withContext parseCachedEntity(cached)
        }

        // ── Step 5: Network lookup via LRCLIB ─────────────────────────────
        try {
            val result = lrclib.resolve(
                title     = track.title,
                artist    = track.artist,
                album     = track.album,
                durationMs = track.durationMs,
            )
            if (result != null) {
                val entity = LyricsCacheEntity(
                    trackUri       = track.uri,
                    source         = LyricsSource.LRCLIB.name,
                    providerItemId = result.id.toString(),
                    plainLyrics    = result.plainLyrics.orEmpty(),
                    syncedLyrics   = result.syncedLyrics.orEmpty(),
                    trackDurationMs = track.durationMs,
                    fetchedAt      = System.currentTimeMillis(),
                    lastValidatedAt = System.currentTimeMillis(),
                )
                dao.upsertLyrics(entity)
                return@withContext parseCachedEntity(entity)
            }
        } catch (e: Exception) {
            Log.e(TAG, "LRCLIB lookup failed: ${e.message}")
        }

        SyncedLyrics.EMPTY
    }

    /** Parses a [LyricsCacheEntity] into [SyncedLyrics]. */
    private fun parseCachedEntity(entity: LyricsCacheEntity): SyncedLyrics {
        val source = runCatching { LyricsSource.valueOf(entity.source) }.getOrDefault(LyricsSource.CACHE)
        return when {
            entity.syncedLyrics.isNotBlank() ->
                LrcParser.parse(entity.syncedLyrics, source).copy(offsetMs = entity.offsetMs)
            entity.plainLyrics.isNotBlank()  ->
                LrcParser.parse(entity.plainLyrics, source).copy(offsetMs = entity.offsetMs)
            else -> SyncedLyrics.EMPTY
        }
    }

    /** Reads embedded LYRICS/UNSYNCEDLYRICS tag from audio file metadata. */
    private fun readEmbeddedLyrics(track: Track): SyncedLyrics? {
        return try {
            val mmr = MediaMetadataRetriever()
            mmr.setDataSource(context, Uri.parse(track.uri))
            // METADATA_KEY_LYRICS (constant 28) is available on API 28+; use numeric fallback for older
            @Suppress("DEPRECATION")
            val lyrics = mmr.extractMetadata(28 /* METADATA_KEY_LYRICS */)
            mmr.release()
            lyrics?.takeIf { it.isNotBlank() }?.let { text ->
                // Try to parse as LRC; if no timestamps found, return as plain
                LrcParser.parse(text, LyricsSource.EMBEDDED)
            }
        } catch (e: Exception) {
            Log.d(TAG, "No embedded lyrics in ${track.title}: ${e.message}")
            null
        }
    }

    /**
     * Searches for a sidecar .lrc file in the same directory as the audio.
     * Checks: same name with .lrc extension, case-insensitive.
     */
    private fun findLocalLrc(track: Track): SyncedLyrics? {
        return try {
            val uri = Uri.parse(track.uri)
            val path = uri.path ?: return null
            val audioFile = File(path)
            val lrcFile = File(audioFile.parent, "${audioFile.nameWithoutExtension}.lrc")
                .takeIf { it.exists() && it.canRead() }
                ?: File(audioFile.parent, "${audioFile.nameWithoutExtension}.LRC")
                    .takeIf { it.exists() && it.canRead() }
                ?: return null
            val content = lrcFile.readText(Charsets.UTF_8)
            LrcParser.parse(content, LyricsSource.LOCAL_LRC)
        } catch (e: Exception) {
            null
        }
    }

    /** Cache is considered valid if it was fetched within 30 days. */
    private fun isCacheValid(entity: LyricsCacheEntity): Boolean {
        val maxAgeMs = 30L * 24 * 60 * 60 * 1000
        return (System.currentTimeMillis() - entity.fetchedAt) < maxAgeMs &&
               (entity.syncedLyrics.isNotBlank() || entity.plainLyrics.isNotBlank())
    }

    /** User-initiated: import a local .lrc file as a manual selection. */
    suspend fun importLrc(track: Track, lrcContent: String) {
        val parsed = LrcParser.parse(lrcContent, LyricsSource.LOCAL_LRC)
        val entity = LyricsCacheEntity(
            trackUri        = track.uri,
            source          = LyricsSource.LOCAL_LRC.name,
            syncedLyrics    = if (parsed.synced) lrcContent else "",
            plainLyrics     = if (!parsed.synced) lrcContent else "",
            trackDurationMs = track.durationMs,
            manualSelection = true,   // never overwritten automatically
        )
        dao.clearAutoCache(track.uri)
        dao.upsertLyrics(entity)
    }

    /** Updates the timing offset for the currently cached lyrics. */
    suspend fun updateOffset(track: Track, offsetMs: Long) {
        val entity = dao.getLyrics(track.uri) ?: return
        dao.updateOffset(entity.id, offsetMs)
    }

    /** Clears all cache (including manual) — user-initiated reset. */
    suspend fun clearAll(track: Track) {
        dao.clearAllLyrics(track.uri)
    }
}
