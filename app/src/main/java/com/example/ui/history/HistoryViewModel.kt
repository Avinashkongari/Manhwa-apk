package com.example.ui.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.AxionApplication
import com.example.data.local.DownloadTaskEntity
import com.example.data.model.DownloadStatus
import com.example.domain.ZipStorageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class HistoryFilter(val label: String) {
    ALL("All"),
    SAVED("Saved"),
    FAILED("Failed")
}

data class HistoryItemUi(
    val task: DownloadTaskEntity,
    val fileExistsOnDisk: Boolean
)

class HistoryViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as AxionApplication
    private val repo = app.container.downloadRepository

    val searchQuery = MutableStateFlow("")
    val selectedFilter = MutableStateFlow(HistoryFilter.ALL)

    val historyItems: StateFlow<List<HistoryItemUi>> = combine(
        repo.historyFlow,
        searchQuery,
        selectedFilter
    ) { list, query, filter ->
        val filtered = list.filter { task ->
            val matchesQuery = query.isBlank() ||
                task.mangaTitle.contains(query, ignoreCase = true) ||
                task.chapterTitle.contains(query, ignoreCase = true) ||
                task.zipFilename.contains(query, ignoreCase = true)
            val matchesFilter = when (filter) {
                HistoryFilter.ALL -> true
                HistoryFilter.SAVED -> task.status == DownloadStatus.COMPLETED
                HistoryFilter.FAILED -> task.status == DownloadStatus.FAILED
            }
            matchesQuery && matchesFilter
        }
        withContext(Dispatchers.IO) {
            filtered.map { task ->
                val exists = task.status == DownloadStatus.COMPLETED &&
                    ZipStorageManager.verifyZipExists(app, task.outputUri)
                HistoryItemUi(task = task, fileExistsOnDisk = exists)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun updateSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun updateFilter(filter: HistoryFilter) {
        selectedFilter.value = filter
    }

    fun deleteEntry(taskId: String, deleteFileFromDisk: Boolean) {
        viewModelScope.launch {
            repo.deleteHistoryEntry(taskId, deleteFileFromDisk)
        }
    }
}
