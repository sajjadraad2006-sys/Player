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

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(download: DownloadEntity): Long

    @Update
    suspend fun update(download: DownloadEntity)

    @Query("UPDATE downloads SET status = :status, errorMessage = :error WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String, error: String = "")

    @Query("UPDATE downloads SET status = :status, bytesDownloaded = :bytes, bytesTotal = :total WHERE id = :id")
    suspend fun updateProgress(id: Long, status: String, bytes: Long, total: Long)

    @Query("UPDATE downloads SET status = 'COMPLETED', completedAt = :completedAt, trackUri = :trackUri, destinationPath = :path WHERE id = :id")
    suspend fun markCompleted(id: Long, trackUri: String, path: String, completedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT EXISTS(SELECT 1 FROM downloads WHERE providerTrackId = :providerTrackId AND provider = :provider AND status IN ('COMPLETED', 'DOWNLOADING', 'QUEUED'))")
    suspend fun isDownloaded(provider: String, providerTrackId: String): Boolean
}
