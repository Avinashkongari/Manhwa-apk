package com.example.data.local

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import com.example.data.model.DownloadStatus
import com.example.domain.ZipStorageManager
import com.example.worker.ChapterDownloadWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

class DownloadRepository(
    private val context: Context,
    private val downloadDao: DownloadDao,
    private val settingsRepository: SettingsRepository
) {
    private val workManager: WorkManager = WorkManager.getInstance(context)

    val downloadsQueueFlow: Flow<List<DownloadTaskEntity>> = downloadDao.observeDownloadsQueue()
    val historyFlow: Flow<List<DownloadTaskEntity>> = downloadDao.observeHistory()
    val activeCountFlow: Flow<Int> = downloadDao.observeActiveCount()

    suspend fun startChapterDownload(
        mangaTitle: String,
        chapterTitle: String,
        chapterNumber: String,
        sourcePageUrl: String,
        zipFilename: String,
        pageUrls: List<String>,
        requestCookie: String,
        requestUserAgent: String,
        overrideSafTreeUri: String? = null
    ): String {
        val settings = settingsRepository.getCurrentSettings()
        val taskId = UUID.randomUUID().toString()
        val pagesJson = Json.encodeToString(pageUrls)
        val effectiveSafUri = overrideSafTreeUri ?: settings.safTreeUri
        val folderDisplay = if (effectiveSafUri != null) {
            settings.safFolderDisplayName ?: "Selected Folder"
        } else {
            "Download"
        }

        val entity = DownloadTaskEntity(
            id = taskId,
            mangaTitle = mangaTitle,
            chapterTitle = chapterTitle,
            chapterNumber = chapterNumber,
            sourcePageUrl = sourcePageUrl,
            zipFilename = zipFilename,
            pageUrlsJson = pagesJson,
            requestCookie = requestCookie,
            requestUserAgent = requestUserAgent,
            safTreeUri = effectiveSafUri,
            totalPages = pageUrls.size,
            status = DownloadStatus.QUEUED,
            outputDisplayPath = "$folderDisplay/$zipFilename",
            inDownloadsQueue = true,
            inHistory = false
        )

        downloadDao.insert(entity)
        enqueueWorker(taskId)
        return taskId
    }

    fun enqueueWorker(taskId: String) {
        val inputData = Data.Builder()
            .putString(ChapterDownloadWorker.KEY_TASK_ID, taskId)
            .build()

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = OneTimeWorkRequestBuilder<ChapterDownloadWorker>()
            .setInputData(inputData)
            .setConstraints(constraints)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .addTag(workTag(taskId))
            .build()

        workManager.enqueueUniqueWork(
            uniqueWorkName(taskId),
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    suspend fun cancelDownload(taskId: String) {
        workManager.cancelUniqueWork(uniqueWorkName(taskId))
        val existing = downloadDao.getById(taskId) ?: return
        if (existing.status.isActive) {
            downloadDao.markStopped(
                id = taskId,
                status = DownloadStatus.CANCELLED,
                errorMessage = "Download cancelled by user. Tap Resume to continue.",
                inHistory = false
            )
        }
    }

    suspend fun resumeDownload(taskId: String) {
        val existing = downloadDao.getById(taskId) ?: return
        if (existing.status == DownloadStatus.CANCELLED || existing.status == DownloadStatus.FAILED) {
            downloadDao.updateProgress(
                id = taskId,
                downloadedPages = existing.downloadedPages,
                failedPages = 0,
                skippedDuplicates = existing.skippedDuplicates,
                skippedDuplicateInfo = existing.skippedDuplicateInfo,
                speedBytesPerSec = 0L,
                zipCreationProgress = 0,
                status = DownloadStatus.QUEUED,
                errorMessage = null
            )
            enqueueWorker(taskId)
        }
    }

    suspend fun retryDownload(taskId: String) {
        val existing = downloadDao.getById(taskId) ?: return
        if (existing.status == DownloadStatus.FAILED || existing.status == DownloadStatus.CANCELLED) {
            downloadDao.updateProgress(
                id = taskId,
                downloadedPages = existing.downloadedPages,
                failedPages = 0,
                skippedDuplicates = 0,
                skippedDuplicateInfo = "",
                speedBytesPerSec = 0L,
                zipCreationProgress = 0,
                status = DownloadStatus.QUEUED,
                errorMessage = null
            )
            enqueueWorker(taskId)
        }
    }

    suspend fun dismissFromDownloads(taskId: String) {
        val existing = downloadDao.getById(taskId)
        if (existing != null && existing.status.isActive) {
            workManager.cancelUniqueWork(uniqueWorkName(taskId))
        }
        downloadDao.dismissFromDownloads(taskId)
        withContext(Dispatchers.IO) {
            cleanupTempDir(taskId)
        }
        downloadDao.pruneOrphaned()
    }

    suspend fun deleteHistoryEntry(taskId: String, deleteFileFromDisk: Boolean) {
        val existing = downloadDao.getById(taskId)
        if (deleteFileFromDisk && existing?.outputUri != null) {
            withContext(Dispatchers.IO) {
                ZipStorageManager.deleteZip(context, existing.outputUri)
            }
        }
        downloadDao.removeFromHistory(taskId)
        downloadDao.pruneOrphaned()
    }

    suspend fun clearHistory(deleteFilesFromDisk: Boolean = false) {
        if (deleteFilesFromDisk) {
            // Only delete files if explicitly requested
            // Currently history clear preserves files by default per spec
        }
        downloadDao.clearAllHistory()
        downloadDao.pruneOrphaned()
    }

    private fun cleanupTempDir(taskId: String) {
        val dir = File(context.cacheDir, "axion_tmp/$taskId")
        if (dir.exists()) {
            dir.deleteRecursively()
        }
    }

    companion object {
        fun uniqueWorkName(taskId: String) = "axion_chapter_dl_$taskId"
        fun workTag(taskId: String) = "axion_tag_$taskId"
    }
}
