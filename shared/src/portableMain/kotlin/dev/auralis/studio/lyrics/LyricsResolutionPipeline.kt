package dev.auralis.studio.lyrics

import dev.auralis.studio.core.model.LyricsSource
import dev.auralis.studio.core.model.SyncedLyrics
import kotlinx.serialization.Serializable

data class LyricsTrack(val key: String, val title: String, val artist: String, val album: String, val durationMs: Long)

@Serializable
data class LyricsRecord(
    val plain: String = "", val lrc: String = "", val lyricsfile: String = "",
    val source: String = "LRCLIB", val manual: Boolean = false,
    val fetchedAt: Long = System.currentTimeMillis(), val durationMs: Long = 0,
)

/** Storage/metadata adapters are platform-specific; ordering, cache policy and matching are shared. */
interface LyricsStorage {
    suspend fun embedded(track: LyricsTrack): String?
    suspend fun sidecar(track: LyricsTrack): String?
    suspend fun read(key: String): LyricsRecord?
    suspend fun write(key: String, record: LyricsRecord)
    suspend fun lastMiss(key: String): Long
    suspend fun setMiss(key: String, at: Long)
    suspend fun offset(key: String): Long?
    suspend fun setOffset(key: String, offset: Long)
    fun online(): Boolean = true
}

/** Extracted from Android LyricsRepository: local first, manual choices preserved, network last. */
class LyricsResolutionPipeline(private val storage: LyricsStorage, val client: LrclibClient) {
    suspend fun resolve(track: LyricsTrack, forceRefresh: Boolean = false): SyncedLyrics {
        fun text(raw: String?, source: LyricsSource) = raw?.takeIf(String::isNotBlank)?.let { LrcParser.parse(it, source) }
        suspend fun adjusted(lyrics: SyncedLyrics) = lyrics.copy(offsetMs = (storage.offset(track.key) ?: lyrics.offsetMs).coerceIn(-30_000, 30_000))
        text(storage.embedded(track), LyricsSource.EMBEDDED)?.let { return adjusted(it) }
        val cached = storage.read(track.key)
        if (cached?.manual == true) return adjusted(parse(cached))
        text(storage.sidecar(track), LyricsSource.LOCAL_LRC)?.let { return adjusted(it) }
        if (!forceRefresh && cached != null && System.currentTimeMillis() - cached.fetchedAt < 30L * 24 * 3600 * 1000 &&
            cached.durationMs !in 1..44_999 && parse(cached).lines.isNotEmpty()) return adjusted(parse(cached))
        if (!storage.online() || (!forceRefresh && System.currentTimeMillis() - storage.lastMiss(track.key) < 900_000)) return SyncedLyrics.EMPTY
        val artist = LyricsMetadataNormalizer.artist(track.artist).takeUnless { it.equals("Music", true) || it.equals("Lyrune", true) }.orEmpty()
        val match = client.resolve(track.title, artist, track.album, track.durationMs.takeUnless { it in 1..44_999 } ?: 0)
        if (match != null) {
            val record = record(match, track.durationMs, false)
            storage.write(track.key, record)
            storage.setMiss(track.key, 0)
            return adjusted(parse(record))
        }
        if (!client.lastLookupWasRateLimited && client.lastLookupError == null) storage.setMiss(track.key, System.currentTimeMillis())
        return SyncedLyrics.EMPTY
    }

    suspend fun select(track: LyricsTrack, candidate: LrclibTrack): SyncedLyrics {
        val selected = record(candidate, track.durationMs, true)
        storage.write(track.key, selected)
        storage.setMiss(track.key, 0)
        val parsed = parse(selected)
        return parsed.copy(offsetMs = storage.offset(track.key) ?: parsed.offsetMs)
    }
    suspend fun import(track: LyricsTrack, content: String) {
        storage.write(track.key, LyricsRecord(lrc = content, source = "LOCAL_LRC", manual = true, durationMs = track.durationMs))
    }
    suspend fun updateOffset(track: LyricsTrack, offset: Long) = storage.setOffset(track.key, offset.coerceIn(-30_000, 30_000))
    suspend fun candidates(track: LyricsTrack) = client.searchCandidates(track.title, track.artist, track.album, track.durationMs)

    companion object {
        fun parse(record: LyricsRecord): SyncedLyrics {
            val source = runCatching { LyricsSource.valueOf(record.source) }.getOrDefault(LyricsSource.CACHE)
            if (record.lyricsfile.isNotBlank()) LyricsfileParser.parse(record.lyricsfile, source)?.let { return it }
            return LrcParser.parse(record.lrc.ifBlank { record.plain }, source)
        }
        private fun record(track: LrclibTrack, duration: Long, manual: Boolean) = LyricsRecord(
            plain = track.plainLyrics.orEmpty(), lrc = track.syncedLyrics.orEmpty(), lyricsfile = track.lyricsfile.orEmpty(),
            durationMs = duration, manual = manual,
        )
    }
}
