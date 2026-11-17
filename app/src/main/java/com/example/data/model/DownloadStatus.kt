package com.example.data.model

import kotlinx.serialization.Serializable

enum class DownloadStatus {
    QUEUED,
    DETECTING,
    DOWNLOADING,
    CREATING_ZIP,
    COMPLETED,
    FAILED,
    CANCELLED;

    val isActive: Boolean
        get() = this == QUEUED || this == DETECTING || this == DOWNLOADING || this == CREATING_ZIP

    val canResume: Boolean
        get() = this == CANCELLED

    val canRetry: Boolean
        get() = this == FAILED
}

@Serializable
data class DetectedPage(
    val index: Int,
    val url: String,
    val width: Int = 0,
    val height: Int = 0,
    val alt: String = "",
    val isSelected: Boolean = true
)

data class ChapterDetectionResult(
    val mangaTitle: String,
    val chapterTitle: String,
    val chapterNumber: String,
    val pageUrl: String,
    val pages: List<DetectedPage>,
    val excludedCount: Int,
    val suggestedZipName: String,
    val errorMessage: String? = null
)
