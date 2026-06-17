package com.marcosrava.iptvplayer.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.marcosrava.iptvplayer.data.model.Playlist
import com.marcosrava.iptvplayer.data.model.PlaylistSource
import com.marcosrava.iptvplayer.data.repository.PlaylistRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PlaylistsUiState(
    val playlists: List<Playlist> = emptyList(),
    val isRefreshing: Boolean = false,
    val refreshingPlaylistId: Long? = null,
    val error: String? = null,
    val successMessage: String? = null
)

@HiltViewModel
class PlaylistsViewModel @Inject constructor(
    private val repository: PlaylistRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlaylistsUiState())
    val uiState: StateFlow<PlaylistsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.allPlaylists.collect { playlists ->
                _uiState.update { it.copy(playlists = playlists) }
            }
        }
    }

    fun addPlaylist(name: String, url: String, epgUrl: String? = null) {
        viewModelScope.launch {
            val playlist = Playlist(
                name = name,
                url = url,
                epgUrl = epgUrl?.ifBlank { null },
                source = PlaylistSource.URL
            )
            val id = repository.addPlaylist(playlist)
            refreshPlaylist(playlist.copy(id = id))
        }
    }

    fun addUbuntuPlaylist(name: String, url: String) {
        viewModelScope.launch {
            val playlist = Playlist(
                name = name,
                url = url,
                source = PlaylistSource.UBUNTU
            )
            val id = repository.addPlaylist(playlist)
            refreshPlaylist(playlist.copy(id = id))
        }
    }

    fun refreshPlaylist(playlist: Playlist) {
        viewModelScope.launch {
            _uiState.update { it.copy(refreshingPlaylistId = playlist.id) }
            val result = repository.refreshPlaylist(playlist)
            result.fold(
                onSuccess = { count ->
                    _uiState.update {
                        it.copy(
                            refreshingPlaylistId = null,
                            successMessage = "${playlist.name}: $count canales cargados"
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            refreshingPlaylistId = null,
                            error = "Error al actualizar ${playlist.name}: ${error.message}"
                        )
                    }
                }
            )
        }
    }

    fun deletePlaylist(playlist: Playlist) {
        viewModelScope.launch {
            repository.deletePlaylist(playlist)
        }
    }

    fun clearError() = _uiState.update { it.copy(error = null) }
    fun clearSuccess() = _uiState.update { it.copy(successMessage = null) }
}
