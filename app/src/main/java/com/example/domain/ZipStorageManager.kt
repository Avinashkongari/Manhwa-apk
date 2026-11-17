package com.example.domain

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class ZipPageInput(
    val file: File,
    val extension: String
)

data class ZipSaveResult(
    val uriString: String,
    val displayPath: String,
    val sizeBytes: Long,
    val entryCount: Int
)

object ZipStorageManager {

    private const val STREAM_BUFFER_BYTES = 16 * 1024

    fun getAvailableBytes(dir: File): Long {
        return runCatching {
            val stat = StatFs(dir.absolutePath)
            stat.availableBytes
        }.getOrDefault(Long.MAX_VALUE)
    }

    /**
     * Streams validated page files sequentially into a ZIP archive without holding
     * all images in memory. Saves either to the user's persisted SAF tree URI or
     * to the phone's Download folder via MediaStore.Downloads (API 29+).
     * Verifies the saved archive exists and is non-empty before returning success.
     */
    suspend fun createAndSaveZip(
        context: Context,
        pages: List<ZipPageInput>,
        zipFilename: String,
        safTreeUriString: String?,
        onProgress: suspend (percent: Int) -> Unit
    ): ZipSaveResult {
        require(pages.isNotEmpty()) { "No valid pages available to archive." }

        val estimatedBytes = pages.sumOf { it.file.length() } + 4096L
        val availableCache = getAvailableBytes(context.cacheDir)
        if (availableCache < estimatedBytes + (2L * 1024L * 1024L)) {
            throw InsufficientStorageException("Insufficient storage space to build the ZIP archive.")
        }

        val safeName = resolveUniqueFilename(zipFilename)

        val safUri = safTreeUriString?.let { runCatching { Uri.parse(it) }.getOrNull() }
        if (safUri != null) {
            return saveToSafTree(context, pages, safeName, safUri, onProgress)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return saveToMediaStoreDownloads(context, pages, safeName, onProgress)
        }

        return saveToAppExternalDownloads(context, pages, safeName, onProgress)
    }

    private suspend fun saveToSafTree(
        context: Context,
        pages: List<ZipPageInput>,
        filename: String,
        treeUri: Uri,
        onProgress: suspend (Int) -> Unit
    ): ZipSaveResult {
        val resolver = context.contentResolver
        val parentDocId = DocumentsContract.getTreeDocumentId(treeUri)
        val parentDocUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, parentDocId)
        val targetDocUri = DocumentsContract.createDocument(
            resolver,
            parentDocUri,
            "application/zip",
            filename
        ) ?: throw IllegalStateException("Permission denied or unable to create ZIP file in the selected folder.")

        var writtenEntries = 0
        try {
            val outStream = resolver.openOutputStream(targetDocUri, "w")
                ?: throw IllegalStateException("Unable to open output stream for SAF destination.")
            outStream.use { rawOut ->
                writtenEntries = writePagesToZipStream(pages, rawOut, onProgress)
            }
        } catch (t: Throwable) {
            runCatching { DocumentsContract.deleteDocument(resolver, targetDocUri) }
            throw t
        }

        val finalSize = queryUriSize(context, targetDocUri)
        if (finalSize <= 0L || writtenEntries <= 0) {
            runCatching { DocumentsContract.deleteDocument(resolver, targetDocUri) }
            throw IllegalStateException("ZIP archive verification failed (empty output).")
        }

        val folderLabel = getSafFolderLabel(context, treeUri)
        return ZipSaveResult(
            uriString = targetDocUri.toString(),
            displayPath = "$folderLabel/$filename",
            sizeBytes = finalSize,
            entryCount = writtenEntries
        )
    }

    private suspend fun saveToMediaStoreDownloads(
        context: Context,
        pages: List<ZipPageInput>,
        filename: String,
        onProgress: suspend (Int) -> Unit
    ): ZipSaveResult {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return saveToAppExternalDownloads(context, pages, filename, onProgress)
        }
        val resolver = context.contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, filename)
            put(MediaStore.Downloads.MIME_TYPE, "application/zip")
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }

        val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val itemUri = resolver.insert(collection, contentValues)
            ?: throw IllegalStateException("Unable to create ZIP entry in Download folder.")

        var writtenEntries = 0
        try {
            val outStream = resolver.openOutputStream(itemUri, "w")
                ?: throw IllegalStateException("Unable to open Download output stream.")
            outStream.use { rawOut ->
                writtenEntries = writePagesToZipStream(pages, rawOut, onProgress)
            }
            val finalizeValues = ContentValues().apply {
                put(MediaStore.Downloads.IS_PENDING, 0)
            }
            resolver.update(itemUri, finalizeValues, null, null)
        } catch (t: Throwable) {
            runCatching { resolver.delete(itemUri, null, null) }
            throw t
        }

        val finalSize = queryUriSize(context, itemUri)
        if (finalSize <= 0L || writtenEntries <= 0) {
            runCatching { resolver.delete(itemUri, null, null) }
            throw IllegalStateException("ZIP archive verification failed in Download folder.")
        }

        return ZipSaveResult(
            uriString = itemUri.toString(),
            displayPath = "Download/$filename",
            sizeBytes = finalSize,
            entryCount = writtenEntries
        )
    }

    private suspend fun saveToAppExternalDownloads(
        context: Context,
        pages: List<ZipPageInput>,
        filename: String,
        onProgress: suspend (Int) -> Unit
    ): ZipSaveResult {
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: File(context.filesDir, "exports")
        if (!dir.exists()) dir.mkdirs()

        var targetFile = File(dir, filename)
        if (targetFile.exists()) {
            val base = filename.removeSuffix(".zip")
            targetFile = File(dir, "${base}_${System.currentTimeMillis() % 10000}.zip")
        }

        var writtenEntries = 0
        try {
            FileOutputStream(targetFile).use { fos ->
                writtenEntries = writePagesToZipStream(pages, fos, onProgress)
            }
        } catch (t: Throwable) {
            targetFile.delete()
            throw t
        }

        if (!targetFile.exists() || targetFile.length() <= 0L || writtenEntries <= 0) {
            targetFile.delete()
            throw IllegalStateException("ZIP archive verification failed on local storage.")
        }

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            targetFile
        )
        return ZipSaveResult(
            uriString = uri.toString(),
            displayPath = "App Downloads/${targetFile.name}",
            sizeBytes = targetFile.length(),
            entryCount = writtenEntries
        )
    }

    private suspend fun writePagesToZipStream(
        pages: List<ZipPageInput>,
        rawOutputStream: OutputStream,
        onProgress: suspend (Int) -> Unit
    ): Int {
        val buffer = ByteArray(STREAM_BUFFER_BYTES)
        var entries = 0
        ZipOutputStream(BufferedOutputStream(rawOutputStream)).use { zos ->
            zos.setLevel(6)
            pages.forEachIndexed { idx, page ->
                val entryName = ChapterPageDetector.formatPageFilename(idx + 1, page.extension)
                val zipEntry = ZipEntry(entryName)
                zos.putNextEntry(zipEntry)
                FileInputStream(page.file).use { fis ->
                    while (true) {
                        val read = fis.read(buffer)
                        if (read <= 0) break
                        zos.write(buffer, 0, read)
                    }
                }
                zos.closeEntry()
                entries++
                val pct = (((idx + 1).toFloat() / pages.size) * 100f).toInt().coerceIn(0, 100)
                onProgress(pct)
            }
            zos.finish()
        }
        return entries
    }

    private fun resolveUniqueFilename(rawName: String): String {
        return if (rawName.endsWith(".zip", ignoreCase = true)) rawName else "$rawName.zip"
    }

    fun queryUriSize(context: Context, uri: Uri): Long {
        runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.SIZE),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (idx >= 0 && !cursor.isNull(idx)) {
                        val size = cursor.getLong(idx)
                        if (size > 0L) return size
                    }
                }
            }
        }
        return runCatching {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
                afd.length.coerceAtLeast(0L)
            } ?: 0L
        }.getOrDefault(0L)
    }

    fun verifyZipExists(context: Context, uriString: String?): Boolean {
        if (uriString.isNullOrBlank()) return false
        val uri = runCatching { Uri.parse(uriString) }.getOrNull() ?: return false
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                stream.read() != -1
            } ?: false
        }.getOrDefault(false)
    }

    fun deleteZip(context: Context, uriString: String?): Boolean {
        if (uriString.isNullOrBlank()) return false
        val uri = runCatching { Uri.parse(uriString) }.getOrNull() ?: return false
        return runCatching {
            if (DocumentsContract.isDocumentUri(context, uri)) {
                DocumentsContract.deleteDocument(context.contentResolver, uri)
            } else {
                context.contentResolver.delete(uri, null, null) > 0
            }
        }.getOrDefault(false)
    }

    fun createOpenZipIntent(uriString: String): Intent {
        val uri = Uri.parse(uriString)
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/zip")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun createShareZipIntent(uriString: String, title: String): Intent {
        val uri = Uri.parse(uriString)
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(sendIntent, "Share $title").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun getSafFolderLabel(context: Context, treeUri: Uri): String {
        return runCatching {
            val docId = DocumentsContract.getTreeDocumentId(treeUri)
            val tail = docId.substringAfter(':', docId).trim('/')
            if (tail.isBlank()) "Selected Folder" else tail
        }.getOrDefault("Selected Folder")
    }
}

class InsufficientStorageException(message: String) : Exception(message)
