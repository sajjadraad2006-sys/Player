package dev.auralis.studio.core.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface AppDao {
    @Query("SELECT trackUri FROM favorites ORDER BY addedAt DESC")
    fun observeFavoriteUris(): Flow<List<String>>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE trackUri = :uri)")
    suspend fun isFavorite(uri: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addFavorite(favorite: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE trackUri = :uri")
    suspend fun removeFavorite(uri: String)

    @Transaction
    suspend fun toggleFavorite(uri: String) {
        if (isFavorite(uri)) removeFavorite(uri) else addFavorite(FavoriteEntity(uri))
    }

    @Query("SELECT * FROM playback_history ORDER BY lastPlayedAt DESC LIMIT :limit")
    fun observeHistory(limit: Int = 100): Flow<List<PlaybackHistoryEntity>>

    @Query("SELECT * FROM playback_history WHERE trackUri = :uri")
    suspend fun history(uri: String): PlaybackHistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertHistory(item: PlaybackHistoryEntity)

    @Query("SELECT * FROM playlists ORDER BY name COLLATE NOCASE")
    fun observePlaylists(): Flow<List<PlaylistEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun createPlaylist(playlist: PlaylistEntity): Long

    @Delete
    suspend fun deletePlaylist(playlist: PlaylistEntity)

    @Query("SELECT trackUri FROM playlist_tracks WHERE playlistId = :playlistId ORDER BY position")
    fun observePlaylistTracks(playlistId: Long): Flow<List<String>>

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun nextPlaylistPosition(playlistId: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylistTrack(item: PlaylistTrackEntity)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId AND trackUri = :uri")
    suspend fun removePlaylistTrack(playlistId: Long, uri: String)

    @Query("SELECT * FROM audio_presets ORDER BY name COLLATE NOCASE")
    fun observeAudioPresets(): Flow<List<AudioPresetEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveAudioPreset(preset: AudioPresetEntity): Long

    @Query("DELETE FROM audio_presets WHERE id = :id")
    suspend fun deleteAudioPreset(id: Long)

    @Query("SELECT * FROM bookmarks WHERE trackUri = :uri ORDER BY positionMs")
    fun observeBookmarks(uri: String): Flow<List<BookmarkEntity>>

    @Insert
    suspend fun addBookmark(bookmark: BookmarkEntity): Long

    @Delete
    suspend fun deleteBookmark(bookmark: BookmarkEntity)
}
