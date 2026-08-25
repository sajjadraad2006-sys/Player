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
        LyricsCacheEntity::class,
        DownloadEntity::class,
    ],
    version = 5,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): AppDao
    abstract fun lyricsDao(): LyricsDao
    abstract fun downloadsDao(): DownloadsDao

    companion object {
        /**
         * Migration 1 → 2: adds lyrics_cache and downloads tables.
         * Existing user data is completely preserved — no destructive migration.
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

        /**
         * Migration 2 → 3:
         * - Deduplicates historical download rows sharing the same (provider, providerTrackId),
         *   keeping the oldest row (first record wins; later duplicates are removed).
         * - Adds the unique DownloadKey index so the DB itself blocks future duplicates.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    DELETE FROM downloads WHERE id NOT IN (
                        SELECT MIN(id) FROM downloads GROUP BY provider, providerTrackId
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_downloads_provider_providerTrackId` " +
                        "ON `downloads` (`provider`, `providerTrackId`)",
                )
            }
        }

        /**
         * Migration 3 → 4: adds `lyricsfile` column to lyrics_cache for LRCLIB
         * Lyricsfile (YAML) storage. Existing rows get an empty document.
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `lyrics_cache` ADD COLUMN `lyricsfile` TEXT NOT NULL DEFAULT ''")
            }
        }

        /** Adds recording-level identity without changing provider-specific DownloadKey uniqueness. */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `downloads` ADD COLUMN `recordingIdentity` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `downloads` ADD COLUMN `isrc` TEXT NOT NULL DEFAULT ''")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_downloads_recordingIdentity` ON `downloads` (`recordingIdentity`)")
            }
        }

        val ALL_MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
    }
}
