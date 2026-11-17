package com.example.worker

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.webkit.CookieManager
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.example.AxionApplication
import com.example.MainActivity
import com.example.R
import com.example.data.local.DownloadTaskEntity
import com.example.data.model.DownloadStatus
import com.example.domain.DuplicateDetector
import com.example.domain.ImageValidationResult
import com.example.domain.ImageValidator
import com.example.domain.InsufficientStorageException
import com.example.domain.ZipPageInput
import com.example.domain.ZipStorageManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class ChapterDownloadWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val app = appContext.applicationContext as AxionApplication
    private val dao = app.container.database.downloadDao()
    private val settingsRepository = app.container.settingsRepository
    private val httpClient: OkHttpClient = app.container.okHttpClient

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val taskId = inputData.getString(KEY_TASK_ID) ?: "default"
        val notification = buildNotification(
            title = "AXION Manga Downloader",
            content = "Preparing chapter download...",
            progress = 0,
            indeterminate = true
        )
        return createForegroundInfo(taskId.hashCode(), notification)
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val taskId = inputData.getString(KEY_TASK_ID) ?: return@withContext Result.failure()
        val task = dao.getById(taskId) ?: return@withContext Result.failure()

        if (!isNetworkConnected()) {
            dao.markStopped(
                id = taskId,
                status = DownloadStatus.FAILED,
                errorMessage = "No internet connection. Connect to the network and tap Retry.",
                inHistory = true,
                completedAt = System.currentTimeMillis()
            )
            return@withContext Result.failure()
        }

        val settings = settingsRepository.getCurrentSettings()
        val pageUrls = runCatching {
            Json.decodeFromString<List<String>>(task.pageUrlsJson)
        }.getOrDefault(emptyList())

        if (pageUrls.isEmpty()) {
            dao.markStopped(
                id = taskId,
                status = DownloadStatus.FAILED,
                errorMessage = "No eligible pages found to download.",
                inHistory = true,
                completedAt = System.currentTimeMillis()
            )
            return@withContext Result.failure()
        }

        val tempDir = File(applicationContext.cacheDir, "axion_tmp/$taskId")
        if (!tempDir.exists() && !tempDir.mkdirs()) {
            dao.markStopped(
                id = taskId,
                status = DownloadStatus.FAILED,
                errorMessage = "Unable to allocate temporary cache storage.",
                inHistory = true,
                completedAt = System.currentTimeMillis()
            )
            return@withContext Result.failure()
        }

        // Verify minimum free storage (at least 15 MB safety buffer)
        if (ZipStorageManager.getAvailableBytes(applicationContext.cacheDir) < 15L * 1024L * 1024L) {
            dao.markStopped(
                id = taskId,
                status = DownloadStatus.FAILED,
                errorMessage = "Insufficient device storage available.",
                inHistory = true,
                completedAt = System.currentTimeMillis()
            )
            return@withContext Result.failure()
        }

        val notificationId = taskId.hashCode()
        if (settings.notificationsEnabled) {
            runCatching {
                setForeground(
                    createForegroundInfo(
                        notificationId,
                        buildNotification(
                            title = "${task.mangaTitle} • ${task.chapterTitle}",
                            content = "Starting download (${pageUrls.size} pages)...",
                            progress = 0,
                            indeterminate = true
                        )
                    )
                )
            }
        }

        val liveCookies = runCatching {
            CookieManager.getInstance().getCookie(task.sourcePageUrl).orEmpty()
        }.getOrDefault("").ifBlank { task.requestCookie }

        var downloadedCount = 0
        var failedCount = 0
        val validPageFiles = mutableListOf<Pair<Int, ZipPageInput>>()

        try {
            dao.updateProgress(
                id = taskId,
                downloadedPages = 0,
                failedPages = 0,
                skippedDuplicates = 0,
                skippedDuplicateInfo = "",
                speedBytesPerSec = 0L,
                zipCreationProgress = 0,
                status = DownloadStatus.DOWNLOADING,
                errorMessage = null
            )

            var speedWindowStart = System.currentTimeMillis()
            var speedWindowBytes = 0L
            var currentSpeedBps = 0L

            for (index in pageUrls.indices) {
                if (isStopped) {
                    throw CancellationException("Worker stopped")
                }
                val pageUrl = pageUrls[index]

                // Resume optimization: check if this page index was already downloaded and validated
                val cachedPage = findExistingValidCachedPage(tempDir, index)
                if (cachedPage != null) {
                    validPageFiles.add(index to cachedPage)
                    downloadedCount++
                } else {
                    var lastError: String? = null
                    var resolvedInput: ZipPageInput? = null

                    for (attempt in 1..3) {
                        if (isStopped) throw CancellationException("Worker stopped")
                        when (val outcome = downloadAndValidateSinglePage(
                            tempDir = tempDir,
                            index = index,
                            imageUrl = pageUrl,
                            refererUrl = task.sourcePageUrl,
                            cookie = liveCookies,
                            userAgent = task.requestUserAgent
                        ) { bytesDelta ->
                            speedWindowBytes += bytesDelta
                            val elapsed = System.currentTimeMillis() - speedWindowStart
                            if (elapsed >= 600L) {
                                currentSpeedBps = (speedWindowBytes * 1000L) / elapsed.coerceAtLeast(1L)
                                speedWindowStart = System.currentTimeMillis()
                                speedWindowBytes = 0L
                            }
                        }) {
                            is PageDownloadOutcome.Success -> {
                                resolvedInput = outcome.input
                                break
                            }
                            is PageDownloadOutcome.Failure -> {
                                lastError = outcome.reason
                                if (!outcome.retryable) break
                                delay(attempt * 700L)
                            }
                        }
                    }

                    if (resolvedInput != null) {
                        validPageFiles.add(index to resolvedInput)
                        downloadedCount++
                    } else {
                        failedCount++
                    }
                }

                dao.updateProgress(
                    id = taskId,
                    downloadedPages = downloadedCount,
                    failedPages = failedCount,
                    skippedDuplicates = 0,
                    skippedDuplicateInfo = "",
                    speedBytesPerSec = currentSpeedBps,
                    zipCreationProgress = 0,
                    status = DownloadStatus.DOWNLOADING,
                    errorMessage = null
                )

                if (settings.notificationsEnabled) {
                    val pct = (((downloadedCount + failedCount).toFloat() / pageUrls.size) * 100f).toInt()
                    updateNotification(
                        notificationId,
                        buildNotification(
                            title = "${task.mangaTitle} • ${task.chapterTitle}",
                            content = "Pages: $downloadedCount / ${pageUrls.size} ($pct%)",
                            progress = pct,
                            indeterminate = false
                        )
                    )
                }
            }

            if (isStopped) throw CancellationException("Worker stopped")

            if (validPageFiles.isEmpty()) {
                val msg = if (!isNetworkConnected()) {
                    "Network connection lost during download."
                } else {
                    "All chapter page downloads failed or returned invalid image files."
                }
                dao.markStopped(
                    id = taskId,
                    status = DownloadStatus.FAILED,
                    errorMessage = msg,
                    inHistory = true,
                    completedAt = System.currentTimeMillis()
                )
                return@withContext Result.failure()
            }

            if (failedCount > 0) {
                dao.markStopped(
                    id = taskId,
                    status = DownloadStatus.FAILED,
                    errorMessage = "$failedCount page(s) failed to download. Tap Retry to download missing pages.",
                    inHistory = true,
                    completedAt = System.currentTimeMillis()
                )
                return@withContext Result.failure()
            }

            // Duplicate detection via streaming SHA-256 while preserving original reading order
            val duplicateDetector = DuplicateDetector()
            val uniqueOrderedPages = mutableListOf<ZipPageInput>()
            for ((srcIdx, pageInput) in validPageFiles) {
                val isDup = duplicateDetector.checkAndRegister(
                    file = pageInput.file,
                    sourceIndex = srcIdx,
                    url = pageUrls[srcIdx]
                )
                if (!isDup) {
                    uniqueOrderedPages.add(pageInput)
                }
            }

            val skippedCount = duplicateDetector.skippedDuplicates.size
            val skippedSummary = duplicateDetector.formatSkippedSummary()

            if (uniqueOrderedPages.isEmpty()) {
                dao.markStopped(
                    id = taskId,
                    status = DownloadStatus.FAILED,
                    errorMessage = "No unique valid image pages remained after validation.",
                    inHistory = true,
                    completedAt = System.currentTimeMillis()
                )
                return@withContext Result.failure()
            }

            // Create ZIP archive via streaming IO
            dao.updateProgress(
                id = taskId,
                downloadedPages = uniqueOrderedPages.size,
                failedPages = 0,
                skippedDuplicates = skippedCount,
                skippedDuplicateInfo = skippedSummary,
                speedBytesPerSec = 0L,
                zipCreationProgress = 0,
                status = DownloadStatus.CREATING_ZIP,
                errorMessage = null
            )

            if (settings.notificationsEnabled) {
                updateNotification(
                    notificationId,
                    buildNotification(
                        title = "${task.mangaTitle} • ${task.chapterTitle}",
                        content = "Creating ZIP archive (${uniqueOrderedPages.size} pages)...",
                        progress = 0,
                        indeterminate = true
                    )
                )
            }

            var lastReportedZipPct = -1
            val zipResult = ZipStorageManager.createAndSaveZip(
                context = applicationContext,
                pages = uniqueOrderedPages,
                zipFilename = task.zipFilename,
                safTreeUriString = task.safTreeUri
            ) { zipPct ->
                if (zipPct == 100 || zipPct - lastReportedZipPct >= 10) {
                    lastReportedZipPct = zipPct
                    dao.updateProgress(
                        id = taskId,
                        downloadedPages = uniqueOrderedPages.size,
                        failedPages = 0,
                        skippedDuplicates = skippedCount,
                        skippedDuplicateInfo = skippedSummary,
                        speedBytesPerSec = 0L,
                        zipCreationProgress = zipPct,
                        status = DownloadStatus.CREATING_ZIP,
                        errorMessage = null
                    )
                }
            }

            // Strict final verification before marking COMPLETED
            if (!ZipStorageManager.verifyZipExists(applicationContext, zipResult.uriString) || zipResult.sizeBytes <= 0L) {
                throw IOException("Saved ZIP file could not be verified on storage.")
            }

            dao.markCompleted(
                id = taskId,
                status = DownloadStatus.COMPLETED,
                outputUri = zipResult.uriString,
                outputDisplayPath = zipResult.displayPath,
                outputSizeBytes = zipResult.sizeBytes,
                downloadedPages = zipResult.entryCount,
                skippedDuplicates = skippedCount,
                skippedDuplicateInfo = skippedSummary,
                completedAt = System.currentTimeMillis()
            )

            // Cleanup temp cache ONLY after verified completion
            tempDir.deleteRecursively()

            if (settings.notificationsEnabled) {
                updateNotification(
                    notificationId,
                    buildNotification(
                        title = "Saved ${task.zipFilename}",
                        content = "${zipResult.entryCount} pages archived in ${zipResult.displayPath}",
                        progress = 100,
                        indeterminate = false,
                        ongoing = false
                    )
                )
            }

            Result.success()
        } catch (ce: CancellationException) {
            val current = dao.getById(taskId)
            if (current != null && current.status != DownloadStatus.COMPLETED) {
                dao.markStopped(
                    id = taskId,
                    status = DownloadStatus.CANCELLED,
                    errorMessage = "Download paused/cancelled. Tap Resume to continue.",
                    inHistory = false
                )
            }
            // Keep tempDir so Resume can skip already-downloaded pages
            Result.failure()
        } catch (ise: InsufficientStorageException) {
            dao.markStopped(
                id = taskId,
                status = DownloadStatus.FAILED,
                errorMessage = ise.message ?: "Insufficient storage space.",
                inHistory = true,
                completedAt = System.currentTimeMillis()
            )
            Result.failure()
        } catch (sec: SecurityException) {
            dao.markStopped(
                id = taskId,
                status = DownloadStatus.FAILED,
                errorMessage = "Storage permission denied. Re-select your download folder in Settings.",
                inHistory = true,
                completedAt = System.currentTimeMillis()
            )
            Result.failure()
        } catch (t: Throwable) {
            dao.markStopped(
                id = taskId,
                status = DownloadStatus.FAILED,
                errorMessage = t.message ?: "Unexpected error while creating chapter ZIP.",
                inHistory = true,
                completedAt = System.currentTimeMillis()
            )
            Result.failure()
        }
    }

    private fun findExistingValidCachedPage(tempDir: File, index: Int): ZipPageInput? {
        val candidates = listOf("jpg", "png", "webp").map { ext ->
            File(tempDir, "page_${index}.$ext")
        }
        for (file in candidates) {
            if (file.exists()) {
                val validation = ImageValidator.validateImageFile(file)
                if (validation is ImageValidationResult.Valid) {
                    return ZipPageInput(file = file, extension = validation.format.extension)
                } else {
                    file.delete()
                }
            }
        }
        return null
    }

    private sealed class PageDownloadOutcome {
        data class Success(val input: ZipPageInput) : PageDownloadOutcome()
        data class Failure(val reason: String, val retryable: Boolean) : PageDownloadOutcome()
    }

    private fun downloadAndValidateSinglePage(
        tempDir: File,
        index: Int,
        imageUrl: String,
        refererUrl: String,
        cookie: String,
        userAgent: String,
        onBytesDownloaded: (Long) -> Unit
    ): PageDownloadOutcome {
        val tmpFile = File(tempDir, "raw_${index}.tmp")
        if (tmpFile.exists()) tmpFile.delete()

        val requestBuilder = Request.Builder()
            .url(imageUrl)
            .header("Referer", refererUrl)
            .header("Accept", "image/avif,image/webp,image/png,image/jpeg,*/*;q=0.8")
        if (userAgent.isNotBlank()) {
            requestBuilder.header("User-Agent", userAgent)
        }
        if (cookie.isNotBlank()) {
            requestBuilder.header("Cookie", cookie)
        }

        return try {
            httpClient.newCall(requestBuilder.build()).execute().use { response ->
                if (!response.isSuccessful) {
                    val retryable = response.code in listOf(408, 429, 500, 502, 503, 504)
                    return PageDownloadOutcome.Failure("HTTP ${response.code}", retryable)
                }
                val body = response.body
                    ?: return PageDownloadOutcome.Failure("Empty response body", true)

                val buffer = ByteArray(16 * 1024)
                body.byteStream().use { input ->
                    FileOutputStream(tmpFile).use { output ->
                        while (true) {
                            if (isStopped) throw CancellationException("Cancelled mid-stream")
                            val read = input.read(buffer)
                            if (read <= 0) break
                            output.write(buffer, 0, read)
                            onBytesDownloaded(read.toLong())
                        }
                    }
                }
            }

            when (val validation = ImageValidator.validateImageFile(tmpFile)) {
                is ImageValidationResult.Valid -> {
                    val finalFile = File(tempDir, "page_${index}.${validation.format.extension}")
                    if (finalFile.exists()) finalFile.delete()
                    if (!tmpFile.renameTo(finalFile)) {
                        tmpFile.copyTo(finalFile, overwrite = true)
                        tmpFile.delete()
                    }
                    PageDownloadOutcome.Success(
                        ZipPageInput(file = finalFile, extension = validation.format.extension)
                    )
                }
                is ImageValidationResult.Invalid -> {
                    tmpFile.delete()
                    PageDownloadOutcome.Failure(validation.reason, retryable = false)
                }
            }
        } catch (ce: CancellationException) {
            tmpFile.delete()
            throw ce
        } catch (ioe: IOException) {
            tmpFile.delete()
            PageDownloadOutcome.Failure(ioe.message ?: "Network error", retryable = true)
        } catch (t: Throwable) {
            tmpFile.delete()
            PageDownloadOutcome.Failure(t.message ?: "Download failed", retryable = false)
        }
    }

    private fun isNetworkConnected(): Boolean {
        val cm = applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return true
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun createForegroundInfo(notificationId: Int, notification: Notification): ForegroundInfo {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                notificationId,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(notificationId, notification)
        }
    }

    private fun buildNotification(
        title: String,
        content: String,
        progress: Int,
        indeterminate: Boolean,
        ongoing: Boolean = true
    ): Notification {
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(applicationContext, AxionApplication.CHANNEL_DOWNLOADS)
            .setSmallIcon(R.drawable.ic_axion_notification)
            .setContentTitle(title)
            .setContentText(content)
            .setContentIntent(pendingIntent)
            .setOngoing(ongoing)
            .setOnlyAlertOnce(true)
            .setProgress(100, progress, indeterminate)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(notificationId: Int, notification: Notification) {
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        runCatching { nm?.notify(notificationId, notification) }
    }

    companion object {
        const val KEY_TASK_ID = "task_id"
    }
}
