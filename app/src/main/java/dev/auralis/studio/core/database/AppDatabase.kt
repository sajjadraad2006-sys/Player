package dev.auralis.studio.core.database

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        FavoriteEntity::class,
        PlaybackHistoryEntity::class,
        PlaylistEntity::class,
        PlaylistTrackEntity::class,
        AudioPresetEntity::class,
        BookmarkEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): AppDao
}
