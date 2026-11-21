package com.example.ui.browser

import android.app.Application
import android.webkit.CookieManager
import android.webkit.WebView
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.AxionApplication
import com.example.data.model.AppSettings
import com.example.data.model.ChapterDetectionResult
import com.example.data.model.DetectedPage
import com.example.data.model.ZipFilenameFormat
import com.example.domain.ChapterPageDetector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class MangaSource(
    val displayName: String,
    val shortLabel: String,
    val homeUrl: String,
    val hostKeyword: String
) {
    THUNDER_SCANS(
        displayName = "Thunder Scans",
        shortLabel = "Thunder",
        homeUrl = "https://en-thunderscans.com/",
        hostKeyword = "thunderscans"
    ),
    MANHWA_HUB(
        displayName = "ManhwaHub",
        shortLabel = "ManhwaHub",
        homeUrl = "https://manhwahub.to/",
        hostKeyword = "manhwahub"
    );

    fun next(): MangaSource = when (this) {
        THUNDER_SCANS -> MANHWA_HUB
        MANHWA_HUB -> THUNDER_SCANS
    }

    companion object {
        fun detectFromUrl(url: String?): MangaSource? {
            if (url.isNullOrBlank()) return null
            val low = url.lowercase()
            return entries.firstOrNull { low.contains(it.hostKeyword) }
        }
    }
}

data class BrowserUiState(
    val activeSource: MangaSource = MangaSource.THUNDER_SCANS,
    val currentUrl: String = HOME_URL,
    val pageTitle: String = MangaSource.THUNDER_SCANS.displayName,
    val loadingProgress: Int = 0,
    val isLoading: Boolean = true,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val networkError: String? = null,
    val isScanningPages: Boolean = false,
    val detectionResult: ChapterDetectionResult? = null,
    val customZipFilename: String = "",
    val showReviewSheet: Boolean = false,
    val snackbarMessage: String? = null,
    val capturedCookie: String = "",
    val capturedUserAgent: String = ""
) {
    companion object {
        const val HOME_URL = "https://en-thunderscans.com/"
        const val MANHWA_HUB_URL = "https://manhwahub.to/"
    }
}

class BrowserViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as AxionApplication
    private val settingsRepo = app.container.settingsRepository
    private val downloadRepo = app.container.downloadRepository

    val settings: StateFlow<AppSettings> = settingsRepo.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())

    private val _uiState = MutableStateFlow(BrowserUiState())
    val uiState: StateFlow<BrowserUiState> = _uiState.asStateFlow()

    fun onPageStarted(url: String?, canBack: Boolean, canForward: Boolean) {
        val detectedSource = MangaSource.detectFromUrl(url)
        _uiState.update {
            it.copy(
                activeSource = detectedSource ?: it.activeSource,
                currentUrl = url ?: it.currentUrl,
                isLoading = true,
                networkError = null,
                canGoBack = canBack,
                canGoForward = canForward
            )
        }
    }

    fun onPageFinished(url: String?, title: String?, canBack: Boolean, canForward: Boolean) {
        val detectedSource = MangaSource.detectFromUrl(url)
        _uiState.update {
            it.copy(
                activeSource = detectedSource ?: it.activeSource,
                currentUrl = url ?: it.currentUrl,
                pageTitle = title?.takeIf { t -> t.isNotBlank() } ?: it.pageTitle,
                isLoading = false,
                loadingProgress = 100,
                canGoBack = canBack,
                canGoForward = canForward
            )
        }
    }

    fun switchSource(webView: WebView?) {
        val nextSource = _uiState.value.activeSource.next()
        _uiState.update {
            it.copy(
                activeSource = nextSource,
                currentUrl = nextSource.homeUrl,
                pageTitle = nextSource.displayName,
                isLoading = true,
                loadingProgress = 10,
                networkError = null
            )
        }
        webView?.stopLoading()
        webView?.loadUrl(nextSource.homeUrl)
    }

    fun onProgressChanged(progress: Int, canBack: Boolean, canForward: Boolean) {
        _uiState.update {
            it.copy(
                loadingProgress = progress,
                isLoading = progress < 100,
                canGoBack = canBack,
                canGoForward = canForward
            )
        }
    }

    fun onTitleReceived(title: String?) {
        if (!title.isNullOrBlank()) {
            _uiState.update { it.copy(pageTitle = title) }
        }
    }

    fun onNavigationStateChanged(canBack: Boolean, canForward: Boolean) {
        _uiState.update { it.copy(canGoBack = canBack, canGoForward = canForward) }
    }

    fun onMainFrameError(description: String?) {
        val sourceName = _uiState.value.activeSource.displayName
        val message = when {
            description.isNullOrBlank() -> "Unable to reach $sourceName. Check your internet connection."
            description.contains("ERR_INTERNET_DISCONNECTED", ignoreCase = true) ||
                description.contains("ERR_NAME_NOT_RESOLVED", ignoreCase = true) ->
                "No internet connection or $sourceName is unreachable. Check your connection and retry."
            description.contains("ERR_CONNECTION_TIMED_OUT", ignoreCase = true) ->
                "Connection timed out while loading the page."
            else -> "Page load error: $description"
        }
        _uiState.update {
            it.copy(
                isLoading = false,
                networkError = message
            )
        }
    }

    fun clearError() {
        _uiState.update { it.copy(networkError = null) }
    }

    fun scanCurrentPage(webView: WebView?) {
        if (webView == null) {
            _uiState.update { it.copy(snackbarMessage = "Browser is still initializing.") }
            return
        }
        if (_uiState.value.isScanningPages) return

        val pageUrl = webView.url ?: _uiState.value.currentUrl
        val userAgent = webView.settings.userAgentString.orEmpty()
        val cookie = runCatching { CookieManager.getInstance().getCookie(pageUrl).orEmpty() }.getOrDefault("")

        _uiState.update { it.copy(isScanningPages = true) }

        webView.evaluateJavascript(ChapterPageDetector.CHAPTER_DETECTION_JS) { rawResult ->
            val currentFormat = settings.value.zipFilenameFormat
            val parsed = ChapterPageDetector.parseDetectionResult(
                rawJsResult = rawResult,
                pageUrl = pageUrl,
                filenameFormat = currentFormat
            )
            _uiState.update {
                it.copy(
                    isScanningPages = false,
                    detectionResult = parsed,
                    customZipFilename = parsed.suggestedZipName,
                    showReviewSheet = true,
                    capturedCookie = cookie,
                    capturedUserAgent = userAgent
                )
            }
        }
    }

    fun togglePageSelection(index: Int) {
        _uiState.update { state ->
            val current = state.detectionResult ?: return@update state
            val updatedPages = current.pages.map { page ->
                if (page.index == index) page.copy(isSelected = !page.isSelected) else page
            }
            state.copy(detectionResult = current.copy(pages = updatedPages))
        }
    }

    fun setAllPagesSelected(selected: Boolean) {
        _uiState.update { state ->
            val current = state.detectionResult ?: return@update state
            val updatedPages = current.pages.map { it.copy(isSelected = selected) }
            state.copy(detectionResult = current.copy(pages = updatedPages))
        }
    }

    fun updateCustomZipFilename(name: String) {
        _uiState.update { it.copy(customZipFilename = name) }
    }

    fun dismissReviewSheet() {
        _uiState.update { it.copy(showReviewSheet = false) }
    }

    fun setSafFolder(treeUri: String, displayName: String) {
        viewModelScope.launch {
            settingsRepo.setSafFolder(treeUri, displayName)
        }
    }

    fun startDownload(onStarted: (String) -> Unit) {
        val state = _uiState.value
        val result = state.detectionResult ?: return
        val selectedUrls = result.pages
            .filter { it.isSelected }
            .sortedBy { it.index }
            .map { it.url }

        if (selectedUrls.isEmpty()) {
            _uiState.update { it.copy(snackbarMessage = "Select at least one chapter page to download.") }
            return
        }

        val finalZipName = ZipFilenameFormat.ensureZipExtension(
            state.customZipFilename.ifBlank { result.suggestedZipName }
        )

        viewModelScope.launch {
            val taskId = downloadRepo.startChapterDownload(
                mangaTitle = result.mangaTitle,
                chapterTitle = result.chapterTitle,
                chapterNumber = result.chapterNumber,
                sourcePageUrl = result.pageUrl,
                zipFilename = finalZipName,
                pageUrls = selectedUrls,
                requestCookie = state.capturedCookie,
                requestUserAgent = state.capturedUserAgent
            )
            _uiState.update {
                it.copy(
                    showReviewSheet = false,
                    snackbarMessage = "Downloading ${selectedUrls.size} pages to $finalZipName"
                )
            }
            onStarted(taskId)
        }
    }

    fun consumeSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }
}
