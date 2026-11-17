package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.data.model.DownloadStatus

@Entity(tableName = "download_tasks")
data class DownloadTaskEntity(
    @PrimaryKey val id: String,
    val mangaTitle: String,
    val chapterTitle: String,
    val chapterNumber: String,
    val sourcePageUrl: String,
    val zipFilename: String,
    val pageUrlsJson: String,
    val requestCookie: String,
    val requestUserAgent: String,
    val safTreeUri: String?,
    val totalPages: Int,
    val downloadedPages: Int = 0,
    val failedPages: Int = 0,
    val skippedDuplicates: Int = 0,
    val skippedDuplicateInfo: String = "",
    val speedBytesPerSec: Long = 0L,
    val zipCreationProgress: Int = 0,
    val status: DownloadStatus = DownloadStatus.QUEUED,
    val outputUri: String? = null,
    val outputDisplayPath: String = "",
    val outputSizeBytes: Long = 0L,
    val errorMessage: String? = null,
    val inDownloadsQueue: Boolean = true,
    val inHistory: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null
)
