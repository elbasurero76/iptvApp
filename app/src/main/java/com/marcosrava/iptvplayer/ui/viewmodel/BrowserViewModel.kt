package com.marcosrava.iptvplayer.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.marcosrava.iptvplayer.data.model.RemoteFile
import com.marcosrava.iptvplayer.network.HttpFileBrowser
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BrowserUiState(
    val serverUrl: String = "",
    val currentPath: String = "",
    val files: List<RemoteFile> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val navigationStack: List<String> = emptyList()
)

@HiltViewModel
class BrowserViewModel @Inject constructor(
    private val browser: HttpFileBrowser
) : ViewModel() {

    private val _uiState = MutableStateFlow(BrowserUiState())
    val uiState: StateFlow<BrowserUiState> = _uiState.asStateFlow()

    fun connectToServer(serverUrl: String) {
        val normalizedUrl = if (serverUrl.startsWith("http")) serverUrl
        else "http://$serverUrl"
        _uiState.update {
            it.copy(
                serverUrl = normalizedUrl,
                currentPath = normalizedUrl,
                navigationStack = emptyList()
            )
        }
        loadDirectory(normalizedUrl)
    }

    fun navigateTo(directory: RemoteFile) {
        val currentPath = _uiState.value.currentPath
        _uiState.update {
            it.copy(navigationStack = it.navigationStack + currentPath)
        }
        loadDirectory(directory.url)
    }

    fun navigateUp() {
        val stack = _uiState.value.navigationStack
        if (stack.isEmpty()) return
        val previousPath = stack.last()
        _uiState.update { it.copy(navigationStack = stack.dropLast(1)) }
        loadDirectory(previousPath)
    }

    fun canNavigateUp() = _uiState.value.navigationStack.isNotEmpty()

    private fun loadDirectory(url: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, currentPath = url) }
            browser.listFiles(url).fold(
                onSuccess = { files ->
                    _uiState.update { it.copy(files = files, isLoading = false) }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = "Error: ${error.message}\n\nAsegúrate de que en Ubuntu ejecutas:\npython3 -m http.server 8080"
                        )
                    }
                }
            )
        }
    }

    fun refresh() {
        loadDirectory(_uiState.value.currentPath)
    }

    fun clearError() = _uiState.update { it.copy(error = null) }
}
