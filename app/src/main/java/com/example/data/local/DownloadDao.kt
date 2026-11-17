package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.DownloadStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {

    @Query("SELECT * FROM download_tasks WHERE inDownloadsQueue = 1 ORDER BY createdAt DESC")
    fun observeDownloadsQueue(): Flow<List<DownloadTaskEntity>>

    @Query("SELECT * FROM download_tasks WHERE inHistory = 1 ORDER BY COALESCE(completedAt, createdAt) DESC")
    fun observeHistory(): Flow<List<DownloadTaskEntity>>

    @Query("SELECT COUNT(*) FROM download_tasks WHERE status IN ('QUEUED', 'DETECTING', 'DOWNLOADING', 'CREATING_ZIP')")
    fun observeActiveCount(): Flow<Int>

    @Query("SELECT * FROM download_tasks WHERE id = :id")
    suspend fun getById(id: String): DownloadTaskEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(task: DownloadTaskEntity)

    @Update
    suspend fun update(task: DownloadTaskEntity)

    @Query(
        "UPDATE download_tasks SET downloadedPages = :downloadedPages, failedPages = :failedPages, " +
            "skippedDuplicates = :skippedDuplicates, skippedDuplicateInfo = :skippedDuplicateInfo, " +
            "speedBytesPerSec = :speedBytesPerSec, zipCreationProgress = :zipCreationProgress, " +
            "status = :status, errorMessage = :errorMessage WHERE id = :id"
    )
    suspend fun updateProgress(
        id: String,
        downloadedPages: Int,
        failedPages: Int,
        skippedDuplicates: Int,
        skippedDuplicateInfo: String,
        speedBytesPerSec: Long,
        zipCreationProgress: Int,
        status: DownloadStatus,
        errorMessage: String?
    )

    @Query(
        "UPDATE download_tasks SET status = :status, outputUri = :outputUri, " +
            "outputDisplayPath = :outputDisplayPath, outputSizeBytes = :outputSizeBytes, " +
            "downloadedPages = :downloadedPages, skippedDuplicates = :skippedDuplicates, " +
            "skippedDuplicateInfo = :skippedDuplicateInfo, zipCreationProgress = 100, " +
            "speedBytesPerSec = 0, errorMessage = NULL, inHistory = 1, completedAt = :completedAt " +
            "WHERE id = :id"
    )
    suspend fun markCompleted(
        id: String,
        status: DownloadStatus = DownloadStatus.COMPLETED,
        outputUri: String,
        outputDisplayPath: String,
        outputSizeBytes: Long,
        downloadedPages: Int,
        skippedDuplicates: Int,
        skippedDuplicateInfo: String,
        completedAt: Long = System.currentTimeMillis()
    )

    @Query(
        "UPDATE download_tasks SET status = :status, errorMessage = :errorMessage, " +
            "speedBytesPerSec = 0, inHistory = :inHistory, completedAt = :completedAt WHERE id = :id"
    )
    suspend fun markStopped(
        id: String,
        status: DownloadStatus,
        errorMessage: String?,
        inHistory: Boolean,
        completedAt: Long? = null
    )

    @Query("UPDATE download_tasks SET inDownloadsQueue = 0 WHERE id = :id")
    suspend fun dismissFromDownloads(id: String)

    @Query("UPDATE download_tasks SET inHistory = 0 WHERE id = :id")
    suspend fun removeFromHistory(id: String)

    @Query("UPDATE download_tasks SET inHistory = 0")
    suspend fun clearAllHistory()

    @Query("DELETE FROM download_tasks WHERE inDownloadsQueue = 0 AND inHistory = 0")
    suspend fun pruneOrphaned()
}
