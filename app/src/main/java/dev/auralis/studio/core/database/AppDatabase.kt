package dev.auralis.studio.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        FavoriteEntity::class,
        PlaybackHistoryEntity::class,
        PlaylistEntity::class,
        PlaylistTrackEntity::class,
        AudioPresetEntity::class,
        BookmarkEntity::class,
        // v2 additions
        LyricsCacheEntity::class,
        DownloadEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): AppDao
    abstract fun lyricsDao(): LyricsDao
    abstract fun downloadsDao(): DownloadsDao

    companion object {
        /**
         * Migration 1 → 2: adds lyrics_cache and downloads tables.
         * Existing user data (favorites, history, playlists, queue, presets, bookmarks)
         * is completely preserved — no destructive migration.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // lyrics_cache table
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `lyrics_cache` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `trackUri` TEXT NOT NULL,
                        `source` TEXT NOT NULL,
                        `providerItemId` TEXT NOT NULL DEFAULT '',
                        `plainLyrics` TEXT NOT NULL DEFAULT '',
                        `syncedLyrics` TEXT NOT NULL DEFAULT '',
                        `trackDurationMs` INTEGER NOT NULL DEFAULT 0,
                        `fetchedAt` INTEGER NOT NULL,
                        `lastValidatedAt` INTEGER NOT NULL,
                        `manualSelection` INTEGER NOT NULL DEFAULT 0,
                        `offsetMs` INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_lyrics_cache_trackUri` ON `lyrics_cache` (`trackUri`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_lyrics_cache_source` ON `lyrics_cache` (`source`)")

                // downloads table
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `downloads` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `trackUri` TEXT NOT NULL DEFAULT '',
                        `provider` TEXT NOT NULL,
                        `providerTrackId` TEXT NOT NULL,
                        `remoteUrl` TEXT NOT NULL,
                        `fileName` TEXT NOT NULL,
                        `destinationPath` TEXT NOT NULL DEFAULT '',
                        `title` TEXT NOT NULL,
                        `artist` TEXT NOT NULL,
                        `album` TEXT NOT NULL,
                        `artworkUrl` TEXT NOT NULL DEFAULT '',
                        `durationMs` INTEGER NOT NULL DEFAULT 0,
                        `status` TEXT NOT NULL DEFAULT 'QUEUED',
                        `bytesDownloaded` INTEGER NOT NULL DEFAULT 0,
                        `bytesTotal` INTEGER NOT NULL DEFAULT -1,
                        `createdAt` INTEGER NOT NULL,
                        `completedAt` INTEGER NOT NULL DEFAULT 0,
                        `errorMessage` TEXT NOT NULL DEFAULT '',
                        `retryCount` INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_downloads_status` ON `downloads` (`status`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_downloads_trackUri` ON `downloads` (`trackUri`)")
            }
        }
    }
}
