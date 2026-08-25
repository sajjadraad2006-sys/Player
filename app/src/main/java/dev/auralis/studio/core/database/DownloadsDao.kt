package dev.auralis.studio.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadsDao {

    @Query("SELECT * FROM downloads ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE status = :status ORDER BY createdAt DESC")
    fun observeByStatus(status: String): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE id = :id")
    suspend fun getById(id: Long): DownloadEntity?

    /** Stable identity lookup — the single source of truth for download deduplication. */
    @Query("SELECT * FROM downloads WHERE provider = :provider AND providerTrackId = :providerTrackId LIMIT 1")
    suspend fun findByKey(provider: String, providerTrackId: String): DownloadEntity?

    @Query("SELECT * FROM downloads WHERE recordingIdentity = :identity AND status = 'COMPLETED' LIMIT 1")
    suspend fun findCompletedByRecordingIdentity(identity: String): DownloadEntity?

    @Query("SELECT * FROM downloads WHERE recordingIdentity = :identity AND status IN ('COMPLETED', 'DOWNLOADING', 'QUEUED') LIMIT 1")
    suspend fun findOwnedByRecordingIdentity(identity: String): DownloadEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(download: DownloadEntity): Long

    @Update
    suspend fun update(download: DownloadEntity)

    @Query("UPDATE downloads SET status = :status, errorMessage = :error WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String, error: String = "")

    @Query("UPDATE downloads SET status = :status, bytesDownloaded = :bytes, bytesTotal = :total WHERE id = :id")
    suspend fun updateProgress(id: Long, status: String, bytes: Long, total: Long)

    @Query(
        "UPDATE downloads SET status = 'COMPLETED', completedAt = :completedAt, trackUri = :trackUri, " +
            "destinationPath = :path, title = :title, artist = :artist, album = :album, durationMs = :durationMs " +
            "WHERE id = :id",
    )
    suspend fun markCompleted(
        id: Long,
        trackUri: String,
        path: String,
        title: String,
        artist: String,
        album: String,
        durationMs: Long,
        completedAt: Long = System.currentTimeMillis(),
    )

    /**
     * Repairs metadata for an already-completed Lyrune download from trusted
     * provider records. Only touches rows this app owns (downloads table).
     */
    @Query(
        "UPDATE downloads SET title = :title, artist = :artist, album = :album, durationMs = :durationMs " +
            "WHERE id = :id AND status = 'COMPLETED'",
    )
    suspend fun repairMetadata(id: Long, title: String, artist: String, album: String, durationMs: Long)

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** Duplicate DB rows for the same stable key (historical pollution cleanup). */
    @Query("SELECT * FROM downloads WHERE provider = :provider AND providerTrackId = :providerTrackId ORDER BY createdAt ASC")
    suspend fun findAllByKey(provider: String, providerTrackId: String): List<DownloadEntity>

    @Query("SELECT * FROM downloads WHERE status = 'COMPLETED' ORDER BY createdAt DESC")
    suspend fun findAllCompleted(): List<DownloadEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM downloads WHERE providerTrackId = :providerTrackId AND provider = :provider AND status IN ('COMPLETED', 'DOWNLOADING', 'QUEUED'))")
    suspend fun isDownloaded(provider: String, providerTrackId: String): Boolean
}
