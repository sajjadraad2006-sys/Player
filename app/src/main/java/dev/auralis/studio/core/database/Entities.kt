package dev.auralis.studio.core.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

// ─────────────────────────────────────────────────────────────────────────────
// v1 entities — preserved exactly for migration safety
// ─────────────────────────────────────────────────────────────────────────────

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

// ─────────────────────────────────────────────────────────────────────────────
// v2 entities — added in database migration 1→2
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Cached lyrics result. Stores provenance so the pipeline knows which source
 * provided the result and whether the user has overridden automatic matching.
 */
@Entity(tableName = "lyrics_cache", indices = [Index("trackUri"), Index("source")])
data class LyricsCacheEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Local track URI or a stable composite key for unindexed tracks. */
    val trackUri: String,
    /** LyricsSource enum name */
    val source: String,
    /** Provider-specific item ID (e.g. LRCLIB track id) for re-validation. */
    val providerItemId: String = "",
    /** Plain (unsynced) lyrics text. Empty if none. */
    val plainLyrics: String = "",
    /** LRC-formatted synced lyrics string. Empty if none. */
    val syncedLyrics: String = "",
    /**
     * Raw Lyricsfile YAML (LRCLIB `lyricsfile`). Kept verbatim so the parser
     * can be re-run when its logic improves. Empty if unavailable.
     */
    val lyricsfile: String = "",
    /** Track duration at time of fetch, used for match validation (ms). */
    val trackDurationMs: Long = 0,
    /** Epoch ms when this was fetched from the provider. */
    val fetchedAt: Long = System.currentTimeMillis(),
    /** Epoch ms of last re-validation attempt. */
    val lastValidatedAt: Long = System.currentTimeMillis(),
    /**
     * True if the user manually selected or imported this result.
     * A manual selection must never be silently overwritten by auto-matching.
     */
    val manualSelection: Boolean = false,
    /** User timing offset applied in the player (ms). Positive = delay lyrics. */
    val offsetMs: Long = 0,
)

/** Download record — one row per queued/active/completed download. */
@Entity(
    tableName = "downloads",
    indices = [
        Index("status"),
        Index("trackUri"),
        // Stable DownloadKey — DB-level guarantee of one row per remote track.
        Index(value = ["provider", "providerTrackId"], unique = true),
    ],
)
data class DownloadEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Local track URI after download, or empty while pending. */
    val trackUri: String = "",
    /** Provider name (e.g. "jamendo"). */
    val provider: String,
    /** Provider-specific track identifier. */
    val providerTrackId: String,
    /** Remote download URL. */
    val remoteUrl: String,
    /** Filename used in storage. */
    val fileName: String,
    /** Destination path after download completion. */
    val destinationPath: String = "",
    /** Human-readable track title (for display before the file is scanned). */
    val title: String,
    val artist: String,
    val album: String,
    val artworkUrl: String = "",
    val durationMs: Long = 0,
    /** DownloadStatus enum name. */
    val status: String = DownloadStatus.QUEUED.name,
    /** Bytes downloaded so far. */
    val bytesDownloaded: Long = 0,
    /** Total bytes expected (-1 if unknown). */
    val bytesTotal: Long = -1,
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long = 0,
    val errorMessage: String = "",
    val retryCount: Int = 0,
)

enum class DownloadStatus { QUEUED, DOWNLOADING, PAUSED, COMPLETED, FAILED, CANCELLED }
