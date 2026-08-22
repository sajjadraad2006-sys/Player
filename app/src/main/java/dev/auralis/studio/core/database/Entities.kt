package dev.auralis.studio.core.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val trackUri: String,
    val addedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "playback_history", indices = [Index("lastPlayedAt")])
data class PlaybackHistoryEntity(
    @PrimaryKey val trackUri: String,
    val lastPlayedAt: Long,
    val playCount: Int,
    val lastPositionMs: Long,
)

@Entity(tableName = "playlists", indices = [Index(value = ["name"], unique = true)])
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String = "",
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "playlist_tracks",
    primaryKeys = ["playlistId", "trackUri"],
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("playlistId"), Index("trackUri")],
)
data class PlaylistTrackEntity(
    val playlistId: Long,
    val trackUri: String,
    val position: Int,
)

@Entity(tableName = "audio_presets", indices = [Index(value = ["name"], unique = true)])
data class AudioPresetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val serializedConfig: String,
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "bookmarks", indices = [Index("trackUri")])
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trackUri: String,
    val positionMs: Long,
    val label: String,
)
