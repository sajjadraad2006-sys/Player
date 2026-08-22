package dev.auralis.studio.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LyricsDao {

    /** Returns cached lyrics for a track, prioritising manual selections. */
    @Query("""
        SELECT * FROM lyrics_cache
        WHERE trackUri = :trackUri
        ORDER BY manualSelection DESC, fetchedAt DESC
        LIMIT 1
    """)
    suspend fun getLyrics(trackUri: String): LyricsCacheEntity?

    /** Observe live so the lyrics page can react to user imports. */
    @Query("""
        SELECT * FROM lyrics_cache
        WHERE trackUri = :trackUri
        ORDER BY manualSelection DESC, fetchedAt DESC
        LIMIT 1
    """)
    fun observeLyrics(trackUri: String): Flow<LyricsCacheEntity?>

    /** Upsert — replace existing row for same trackUri+source combo unless it's a manual selection. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLyrics(entity: LyricsCacheEntity): Long

    /** Delete all non-manual cache entries for a track (used on refresh). */
    @Query("DELETE FROM lyrics_cache WHERE trackUri = :trackUri AND manualSelection = 0")
    suspend fun clearAutoCache(trackUri: String)

    /** Delete all cache for a track including manual entries (user reset). */
    @Query("DELETE FROM lyrics_cache WHERE trackUri = :trackUri")
    suspend fun clearAllLyrics(trackUri: String)

    /** Update just the user-adjustable offset without re-fetching. */
    @Query("UPDATE lyrics_cache SET offsetMs = :offsetMs WHERE id = :id")
    suspend fun updateOffset(id: Long, offsetMs: Long)
}
