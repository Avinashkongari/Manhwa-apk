package com.example.ui.downloads

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.AxionApplication
import com.example.data.local.DownloadTaskEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DownloadsViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = (application as AxionApplication).container.downloadRepository

    val tasks: StateFlow<List<DownloadTaskEntity>> = repo.downloadsQueueFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun cancelDownload(taskId: String) {
        viewModelScope.launch { repo.cancelDownload(taskId) }
    }

    fun resumeDownload(taskId: String) {
        viewModelScope.launch { repo.resumeDownload(taskId) }
    }

    fun retryDownload(taskId: String) {
        viewModelScope.launch { repo.retryDownload(taskId) }
    }

    fun dismissTask(taskId: String) {
        viewModelScope.launch { repo.dismissFromDownloads(taskId) }
    }
}
