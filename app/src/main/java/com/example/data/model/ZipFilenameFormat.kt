package com.example.data.model

enum class ZipFilenameFormat(
    val id: String,
    val label: String,
    val example: String
) {
    MANGA_CHAPTER(
        id = "manga_chapter",
        label = "Manga_Name_Chapter_01.zip",
        example = "Thunder_Hero_Chapter_01.zip"
    ),
    MANGA_CH_DASH(
        id = "manga_ch_dash",
        label = "Manga Name - Ch.01.zip",
        example = "Thunder Hero - Ch.01.zip"
    ),
    CHAPTER_MANGA(
        id = "chapter_manga",
        label = "Ch01_Manga_Name.zip",
        example = "Ch01_Thunder_Hero.zip"
    );

    fun formatFilename(mangaTitle: String, chapterNumber: String): String {
        val cleanManga = sanitizePathSegment(mangaTitle.ifBlank { "Manga" })
        val paddedChapter = padChapterNumber(chapterNumber)
        val raw = when (this) {
            MANGA_CHAPTER -> "${cleanManga.replace(' ', '_')}_Chapter_$paddedChapter.zip"
            MANGA_CH_DASH -> "$cleanManga - Ch.$paddedChapter.zip"
            CHAPTER_MANGA -> "Ch${paddedChapter}_${cleanManga.replace(' ', '_')}.zip"
        }
        return ensureZipExtension(raw)
    }

    companion object {
        fun fromId(id: String?): ZipFilenameFormat =
            entries.firstOrNull { it.id == id } ?: MANGA_CHAPTER

        fun sanitizePathSegment(input: String): String {
            return input
                .replace(Regex("[\\\\/:*?\"<>|\\r\\n\\t]+"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()
                .take(80)
                .ifBlank { "Manga" }
        }

        fun padChapterNumber(chapterNumber: String): String {
            val trimmed = chapterNumber.trim()
            val intVal = trimmed.toIntOrNull()
            if (intVal != null) {
                return if (intVal in 0..9) "0$intVal" else intVal.toString()
            }
            // Handle decimal chapters like "12.5" -> "12.5" or "5.5" -> "05.5"
            val parts = trimmed.split(".")
            val major = parts.firstOrNull()?.toIntOrNull()
            return if (major != null && parts.size == 2) {
                val majorStr = if (major in 0..9) "0$major" else major.toString()
                "$majorStr.${parts[1]}"
            } else {
                sanitizePathSegment(trimmed).replace(' ', '_').ifBlank { "01" }
            }
        }

        fun ensureZipExtension(name: String): String {
            val sanitized = name
                .replace(Regex("[\\\\/:*?\"<>|\\r\\n\\t]+"), "_")
                .trim()
                .ifBlank { "Chapter.zip" }
            return if (sanitized.endsWith(".zip", ignoreCase = true)) sanitized else "$sanitized.zip"
        }
    }
}
