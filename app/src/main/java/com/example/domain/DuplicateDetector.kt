package com.example.domain

import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

data class SkippedDuplicate(
    val sourceIndex: Int,
    val url: String,
    val matchedOriginalIndex: Int,
    val sha256Prefix: String
)

/**
 * Streaming SHA-256 duplicate detector.
 *
 * Uses exact cryptographic content hashing so:
 * - Identical duplicate images (duplicate DOM nodes, repeated credit banners, mirror URLs)
 *   are detected and skipped without wasting ZIP space.
 * - Legitimate separate pages that reuse similar comic panels keep distinct full-page hashes
 *   and are always preserved in reading order.
 */
class DuplicateDetector {

    private val seenHashesToIndex = mutableMapOf<String, Int>()
    private val _skipped = mutableListOf<SkippedDuplicate>()
    val skippedDuplicates: List<SkippedDuplicate> get() = _skipped

    fun checkAndRegister(file: File, sourceIndex: Int, url: String): Boolean {
        val hash = computeSha256(file)
        val existingIndex = seenHashesToIndex[hash]
        return if (existingIndex != null) {
            _skipped.add(
                SkippedDuplicate(
                    sourceIndex = sourceIndex,
                    url = url,
                    matchedOriginalIndex = existingIndex,
                    sha256Prefix = hash.take(10)
                )
            )
            true // Is duplicate
        } else {
            seenHashesToIndex[hash] = sourceIndex
            false // Unique page
        }
    }

    fun formatSkippedSummary(): String {
        if (_skipped.isEmpty()) return ""
        return _skipped.joinToString("; ") { dup ->
            "Page #${dup.sourceIndex + 1} matched #${dup.matchedOriginalIndex + 1} (${dup.sha256Prefix})"
        }
    }

    companion object {
        fun computeSha256(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(16 * 1024)
            FileInputStream(file).use { fis ->
                while (true) {
                    val read = fis.read(buffer)
                    if (read <= 0) break
                    digest.update(buffer, 0, read)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
    }
}
