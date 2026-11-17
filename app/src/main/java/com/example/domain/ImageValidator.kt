package com.example.domain

import android.graphics.BitmapFactory
import java.io.File
import java.io.FileInputStream

enum class ImageFormat(val extension: String, val mimeType: String) {
    JPEG("jpg", "image/jpeg"),
    PNG("png", "image/png"),
    WEBP("webp", "image/webp")
}

sealed class ImageValidationResult {
    data class Valid(
        val format: ImageFormat,
        val width: Int,
        val height: Int,
        val sizeBytes: Long
    ) : ImageValidationResult()

    data class Invalid(val reason: String) : ImageValidationResult()
}

object ImageValidator {

    /**
     * Validates an image file on disk using magic bytes and header-only bounds decoding
     * (BitmapFactory.inJustDecodeBounds = true) so pixel buffers are never loaded into RAM.
     */
    fun validateImageFile(file: File, minDimensionPx: Int = 60): ImageValidationResult {
        if (!file.exists() || !file.isFile) {
            return ImageValidationResult.Invalid("Downloaded file is missing.")
        }
        val size = file.length()
        if (size < 128L) {
            return ImageValidationResult.Invalid("File is too small ($size bytes) to be a valid page.")
        }

        val header = ByteArray(16)
        val bytesRead = runCatching {
            FileInputStream(file).use { it.read(header) }
        }.getOrElse {
            return ImageValidationResult.Invalid("Could not read file header: ${it.message}")
        }

        val format = detectFormatFromMagicBytes(header, bytesRead)
            ?: return ImageValidationResult.Invalid("Unsupported or invalid image format (expected JPG, PNG, or WebP).")

        val opts = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(file.absolutePath, opts)
        val width = opts.outWidth
        val height = opts.outHeight

        if (width <= 0 || height <= 0) {
            return ImageValidationResult.Invalid("Corrupt or unreadable image dimensions.")
        }
        if (width < minDimensionPx || height < minDimensionPx) {
            return ImageValidationResult.Invalid("Image too small (${width}x${height}) to be a chapter page.")
        }

        return ImageValidationResult.Valid(
            format = format,
            width = width,
            height = height,
            sizeBytes = size
        )
    }

    fun detectFormatFromMagicBytes(header: ByteArray, length: Int = header.size): ImageFormat? {
        if (length < 4) return null

        // JPEG: FF D8 FF
        if ((header[0].toInt() and 0xFF) == 0xFF &&
            (header[1].toInt() and 0xFF) == 0xD8 &&
            (header[2].toInt() and 0xFF) == 0xFF
        ) {
            return ImageFormat.JPEG
        }

        // PNG: 89 50 4E 47 0D 0A 1A 0A
        if (length >= 8 &&
            (header[0].toInt() and 0xFF) == 0x89 &&
            header[1] == 0x50.toByte() &&
            header[2] == 0x4E.toByte() &&
            header[3] == 0x47.toByte() &&
            header[4] == 0x0D.toByte() &&
            header[5] == 0x0A.toByte() &&
            header[6] == 0x1A.toByte() &&
            header[7] == 0x0A.toByte()
        ) {
            return ImageFormat.PNG
        }

        // WEBP: "RIFF" .... "WEBP"
        if (length >= 12 &&
            header[0] == 'R'.code.toByte() &&
            header[1] == 'I'.code.toByte() &&
            header[2] == 'F'.code.toByte() &&
            header[3] == 'F'.code.toByte() &&
            header[8] == 'W'.code.toByte() &&
            header[9] == 'E'.code.toByte() &&
            header[10] == 'B'.code.toByte() &&
            header[11] == 'P'.code.toByte()
        ) {
            return ImageFormat.WEBP
        }

        return null
    }
}
