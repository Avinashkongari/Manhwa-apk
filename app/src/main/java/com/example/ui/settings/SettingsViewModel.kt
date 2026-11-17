package com.example.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.AxionApplication
import com.example.data.model.AppSettings
import com.example.data.model.ZipFilenameFormat
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as AxionApplication
    private val settingsRepo = app.container.settingsRepository
    private val downloadRepo = app.container.downloadRepository

    val settings: StateFlow<AppSettings> = settingsRepo.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())

    fun setDarkTheme(enabled: Boolean) {
        viewModelScope.launch { settingsRepo.setDarkTheme(enabled) }
    }

    fun setSafFolder(treeUri: String?, displayName: String?) {
        viewModelScope.launch { settingsRepo.setSafFolder(treeUri, displayName) }
    }

    fun setZipFilenameFormat(format: ZipFilenameFormat) {
        viewModelScope.launch { settingsRepo.setZipFilenameFormat(format) }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepo.setNotificationsEnabled(enabled) }
    }

    fun clearHistoryOnly() {
        viewModelScope.launch { downloadRepo.clearHistory(deleteFilesFromDisk = false) }
    }
}
