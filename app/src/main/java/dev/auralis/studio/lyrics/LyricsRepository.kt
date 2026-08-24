package dev.auralis.studio.lyrics

import android.content.Context
import android.net.Uri
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.Log
import dev.auralis.studio.core.database.LyricsCacheEntity
import dev.auralis.studio.core.database.LyricsDao
import dev.auralis.studio.core.model.LyricsSource
import dev.auralis.studio.core.model.SyncedLyrics
import dev.auralis.studio.core.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
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
    private val negativeCache = context.getSharedPreferences("lyrics_negative_cache", Context.MODE_PRIVATE)
    private val offsetStore = context.getSharedPreferences("lyrics_timing_offsets", Context.MODE_PRIVATE)

    /**
     * Resolves lyrics for [track], following the offline-first pipeline.
     * Returns [SyncedLyrics.EMPTY] if nothing is available.
     */
    suspend fun resolve(track: Track, forceRefresh: Boolean = false): SyncedLyrics = withContext(Dispatchers.IO) {
        // ── Step 1: Embedded metadata ──────────────────────────────────────
        val embedded = readEmbeddedLyrics(track)
        if (embedded != null) {
            Log.d(TAG, "Using embedded lyrics for ${track.title}")
            return@withContext applyStoredOffset(track, embedded)
        }

        // ── Step 2 & 3: Local .lrc — manual first, then auto ──────────────
        val cachedManual = dao.getLyrics(track.uri)?.takeIf { it.manualSelection }
        if (cachedManual != null) {
            Log.d(TAG, "Using manually selected local/cache lyrics for ${track.title}")
            return@withContext applyStoredOffset(track, parseCachedEntity(cachedManual))
        }

        val localLrc = findLocalLrc(track)
        if (localLrc != null) {
            Log.d(TAG, "Using local .lrc file for ${track.title}")
            return@withContext applyStoredOffset(track, localLrc)
        }

        // ── Step 4: Valid online cache ─────────────────────────────────────
        val cached = dao.getLyrics(track.uri)
        if (cached != null && isCacheValid(cached)) {
            Log.d(TAG, "Using cached LRCLIB lyrics for ${track.title}")
            return@withContext applyStoredOffset(track, parseCachedEntity(cached))
        }

        val missKey = track.uri.ifBlank { "${track.title}|${track.artist}|${track.durationMs}" }
        val lastMiss = negativeCache.getLong(missKey, 0L)
        if (!forceRefresh && System.currentTimeMillis() - lastMiss < NEGATIVE_CACHE_MS) {
            return@withContext SyncedLyrics.EMPTY
        }

        // Local/cache resolution is complete. Do not touch the network while offline.
        if (!isNetworkAvailable()) return@withContext SyncedLyrics.EMPTY

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
                dao.clearAutoCache(track.uri)
                dao.upsertLyrics(entity)
                negativeCache.edit().remove(missKey).apply()
                return@withContext applyStoredOffset(track, parseCachedEntity(entity))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "LRCLIB lookup failed: ${e.message}")
        }

        if (!lrclib.lastLookupWasRateLimited && lrclib.lastLookupError == null) {
            negativeCache.edit().putLong(missKey, System.currentTimeMillis()).apply()
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
    private suspend fun readEmbeddedLyrics(track: Track): SyncedLyrics? {
        return try {
            val uri = Uri.parse(track.uri)
            if (uri.scheme !in setOf("content", "file")) return null
            EmbeddedLyricsReader.read(context, uri)
                ?.let { text -> LrcParser.parse(text, LyricsSource.EMBEDDED) }
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
            val baseName = track.displayName.substringBeforeLast('.', track.title).ifBlank { track.title }
            val content = when (uri.scheme) {
                "file" -> readFileSidecar(File(uri.path ?: return null), baseName)
                "content" -> readMediaStoreSidecar(uri, baseName) ?: readDocumentSidecar(uri, baseName)
                else -> null
            } ?: return null
            LrcParser.parse(content, LyricsSource.LOCAL_LRC)
        } catch (e: Exception) {
            null
        }
    }

    private fun readFileSidecar(audioFile: File, baseName: String): String? {
        val parent = audioFile.parentFile ?: return null
        val sidecar = parent.listFiles()?.firstOrNull {
            it.isFile && it.extension.equals("lrc", true) &&
                (it.nameWithoutExtension.equals(audioFile.nameWithoutExtension, true) || it.nameWithoutExtension.equals(baseName, true))
        } ?: return null
        return sidecar.readText(Charsets.UTF_8)
    }

    @Suppress("DEPRECATION")
    private fun readMediaStoreSidecar(uri: Uri, baseName: String): String? {
        val path = runCatching {
            context.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.DATA), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull() ?: return null
        return readFileSidecar(File(path), baseName)
    }

    private fun readDocumentSidecar(uri: Uri, baseName: String): String? {
        if (!DocumentsContract.isDocumentUri(context, uri)) return null
        val documentId = DocumentsContract.getDocumentId(uri)
        val parentId = documentId.substringBeforeLast('/', missingDelimiterValue = "")
        if (parentId.isBlank()) return null
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(uri, parentId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        )
        val matchId = context.contentResolver.query(children, projection, null, null, null)?.use { cursor ->
            var found: String? = null
            while (cursor.moveToNext() && found == null) {
                val name = cursor.getString(1).orEmpty()
                if (name.substringBeforeLast('.', name).equals(baseName, true) && name.endsWith(".lrc", true)) {
                    found = cursor.getString(0)
                }
            }
            found
        } ?: return null
        val lrcUri = DocumentsContract.buildDocumentUriUsingTree(uri, matchId)
        return context.contentResolver.openInputStream(lrcUri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
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

    /** Persists playback correction separately from downloaded/embedded source text. */
    suspend fun updateOffset(track: Track, offsetMs: Long) {
        val safeOffset = offsetMs.coerceIn(-5_000L, 5_000L)
        offsetStore.edit().putLong(offsetKey(track), safeOffset).apply()
        dao.getLyrics(track.uri)?.let { dao.updateOffset(it.id, safeOffset) }
    }

    private fun applyStoredOffset(track: Track, lyrics: SyncedLyrics): SyncedLyrics =
        lyrics.copy(offsetMs = offsetStore.getLong(offsetKey(track), lyrics.offsetMs).coerceIn(-5_000L, 5_000L))

    private fun offsetKey(track: Track): String = track.uri.ifBlank {
        "${track.title}|${track.artist}|${track.durationMs}"
    }

    /** Clears all cache (including manual) — user-initiated reset. */
    suspend fun clearAll(track: Track) {
        dao.clearAllLyrics(track.uri)
    }

    fun isNetworkAvailable(): Boolean {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    val lastLookupWasRateLimited: Boolean get() = lrclib.lastLookupWasRateLimited
    val lastLookupError: String? get() = lrclib.lastLookupError

    private companion object {
        const val NEGATIVE_CACHE_MS = 6L * 60L * 60L * 1_000L
    }
}
