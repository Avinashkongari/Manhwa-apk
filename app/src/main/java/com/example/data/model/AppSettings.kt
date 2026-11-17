package com.example.data.model

data class AppSettings(
    val isDarkTheme: Boolean = true,
    val safTreeUri: String? = null,
    val safFolderDisplayName: String? = null,
    val zipFilenameFormat: ZipFilenameFormat = ZipFilenameFormat.MANGA_CHAPTER,
    val notificationsEnabled: Boolean = true
)
