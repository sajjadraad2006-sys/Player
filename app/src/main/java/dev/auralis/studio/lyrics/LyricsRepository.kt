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
     * Matching-algorithm version. Bump when candidate scoring materially changes so
     * stale low-confidence auto-matched cache entries are re-evaluated instead of
     * being served forever. Manual selections and imports are never invalidated.
     *
     * v1 (pre-fix): matched against preview-duration metadata (29 s bug) which
     * poisoned LRCLIB exact lookups. Current version requires full-track metadata.
     */
    private companion object {
        const val NEGATIVE_CACHE_MS = 15L * 60L * 1_000L
        const val MATCHER_VERSION = 2
        /** Local durations below this are previews/incomplete — never used for lyric matching. */
        const val MIN_FULL_TRACK_MS_FOR_MATCHING = 45_000L
        /**
         * Deploy-time epoch of matching algorithm v2. Set to 0 so ALL pre-existing
         * auto-matched cache rows are re-validated exactly once after this update
         * (they were matched against poisoned preview-duration metadata). Rows
         * fetched afterwards carry a fresh lastValidatedAt and are trusted.
         */
        const val MATCHER_EPOCH_MS = 0L
    }

    /**
     * Resolves lyrics for [track], following the offline-first pipeline.
     * Returns [SyncedLyrics.EMPTY] if nothing is available.
     */
    suspend fun resolve(track: Track, forceRefresh: Boolean = false): SyncedLyrics = withContext(Dispatchers.IO) {
        pipeline.resolve(LyricsTrack(track.uri, track.title, track.artist, track.album, track.durationMs), forceRefresh)
    }

    private val pipeline by lazy {
        LyricsResolutionPipeline(object : LyricsStorage {
            override suspend fun embedded(track: LyricsTrack): String? {
                val uri = Uri.parse(track.key)
                if (uri.scheme !in setOf("content", "file")) return null
                return try { EmbeddedLyricsReader.read(context, uri) } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
            }
            override suspend fun sidecar(track: LyricsTrack): String? {
                val uri = Uri.parse(track.key)
                val name = uri.lastPathSegment.orEmpty().substringBeforeLast('.')
                return try { when(uri.scheme) {
                    "file" -> readFileSidecar(File(uri.path ?: return null), name)
                    "content" -> readMediaStoreSidecar(uri, name) ?: readDocumentSidecar(uri, name)
                    else -> null
                } } catch (_: Exception) { null }
            }
            override suspend fun read(key: String) = dao.getLyrics(key)?.let {
                LyricsRecord(it.plainLyrics, it.syncedLyrics, it.lyricsfile, it.source, it.manualSelection, it.fetchedAt, it.trackDurationMs)
            }
            override suspend fun write(key: String, record: LyricsRecord) {
                dao.clearAutoCache(key)
                dao.upsertLyrics(LyricsCacheEntity(trackUri = key, plainLyrics = record.plain, syncedLyrics = record.lrc,
                    lyricsfile = record.lyricsfile, source = record.source, manualSelection = record.manual,
                    fetchedAt = record.fetchedAt, lastValidatedAt = record.fetchedAt, trackDurationMs = record.durationMs))
            }
            override suspend fun lastMiss(key: String) = negativeCache.getLong(key, 0)
            override suspend fun setMiss(key: String, at: Long) { negativeCache.edit().putLong(key, at).apply() }
            override suspend fun offset(key: String) = if(offsetStore.contains(key)) offsetStore.getLong(key, 0) else null
            override suspend fun setOffset(key: String, offset: Long) { offsetStore.edit().putLong(key, offset).apply() }
            override fun online() = isNetworkAvailable()
        }, lrclib)
    }

    /**
     * Parses a [LyricsCacheEntity] into [SyncedLyrics].
     *
     * Source priority for one record:
     *   1. Lyricsfile with genuine WORD timing
     *   2. Lyricsfile with LINE timing
     *   3. Legacy synced LRC (`syncedLyrics`, incl. Enhanced LRC word tags)
     *   4. Plain lyrics
     *
     * A Lyricsfile whose word timing is absent/invalid falls back to its line
     * timing inside the parser — never to fabricated word timestamps.
     */
    private fun parseCachedEntity(entity: LyricsCacheEntity): SyncedLyrics {
        val source = runCatching { LyricsSource.valueOf(entity.source) }.getOrDefault(LyricsSource.CACHE)
        val offset = entity.offsetMs

        if (entity.lyricsfile.isNotBlank()) {
            val fromLyricsfile = LyricsfileParser.parse(entity.lyricsfile, source)
            if (fromLyricsfile != null) return fromLyricsfile.copy(offsetMs = offset)
            // Malformed document → fall through to legacy representations.
        }
        return when {
            entity.syncedLyrics.isNotBlank() -> LrcParser.parse(entity.syncedLyrics, source).copy(offsetMs = offset)
            entity.plainLyrics.isNotBlank()  -> LrcParser.parse(entity.plainLyrics, source).copy(offsetMs = offset)
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

    /** Searches for candidate lyrics from online provider for manual selection. */
    suspend fun searchCandidates(track: Track): List<LrclibTrack> = withContext(Dispatchers.IO) {
        if (!isNetworkAvailable()) return@withContext emptyList()
        val cleanTitle = LyricsMetadataNormalizer.title(track.title)
        val cleanArtist = LyricsMetadataNormalizer.artist(track.artist)
        lrclib.searchCandidates(cleanTitle, cleanArtist, track.album, track.durationMs)
    }

    /** User-initiated: manually select a candidate version from the candidates list. */
    suspend fun selectCandidate(track: Track, candidate: LrclibTrack): SyncedLyrics = withContext(Dispatchers.IO) {
        val entity = LyricsCacheEntity(
            trackUri        = track.uri,
            source          = LyricsSource.LRCLIB.name,
            providerItemId  = candidate.id.toString(),
            plainLyrics     = candidate.plainLyrics.orEmpty(),
            syncedLyrics    = candidate.syncedLyrics.orEmpty(),
            lyricsfile      = candidate.lyricsfile.orEmpty(),
            trackDurationMs = track.durationMs,
            manualSelection = true, // never overwritten automatically
            fetchedAt       = System.currentTimeMillis(),
            lastValidatedAt = System.currentTimeMillis(),
        )
        dao.clearAutoCache(track.uri)
        dao.upsertLyrics(entity)
        val missKey = track.uri.ifBlank { "${track.title}|${track.artist}|${track.durationMs}" }
        negativeCache.edit().remove(missKey).apply()
        applyStoredOffset(track, parseCachedEntity(entity))
    }

    /** Persists playback correction separately from downloaded/embedded source text. */
    suspend fun updateOffset(track: Track, offsetMs: Long) {
        val safeOffset = offsetMs.coerceIn(-30_000L, 30_000L)
        offsetStore.edit().putLong(offsetKey(track), safeOffset).apply()
        dao.getLyrics(track.uri)?.let { dao.updateOffset(it.id, safeOffset) }
    }

    private fun applyStoredOffset(track: Track, lyrics: SyncedLyrics): SyncedLyrics =
        lyrics.copy(offsetMs = offsetStore.getLong(offsetKey(track), lyrics.offsetMs).coerceIn(-30_000L, 30_000L))

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

}
