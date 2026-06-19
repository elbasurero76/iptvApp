package com.marcosrava.iptvplayer.ui.viewmodel

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.marcosrava.iptvplayer.data.model.Playlist
import com.marcosrava.iptvplayer.data.model.PlaylistSource
import com.marcosrava.iptvplayer.data.repository.PlaylistRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Archivos M3U disponibles en el servidor Ubuntu de Marcos */
data class ServerFile(
    val name: String,
    val filename: String,
    val description: String
)

val MARCOS_PLAYLISTS = listOf(
    ServerFile("Mi lista",              "milista.m3u",                        "Lista personal"),
    ServerFile("Argentina + España",    "argentina_espana.m3u",               "Canales AR + ES actualizados"),
    ServerFile("Mundial 2026",          "mundial2026_funcionan.m3u",          "Canales verificados del Mundial"),
    ServerFile("IPTV.org Argentina",    "iptvorg-ar.m3u",                     "Canales en abierto AR"),
    ServerFile("IPTV.org España",       "iptvorg-es.m3u",                     "Canales en abierto ES"),
    ServerFile("AR + ES + Rugby",       "argentina_espana_rugby.m3u",         "Argentina, España y rugby"),
    ServerFile("Canales",               "canales.m3u",                        "Lista general"),
    ServerFile("FabiList 2026",         "fabilist26.m3u",                     "Lista completa verificada"),
    ServerFile("Lista Canales",         "lista_canales.m3u",                  "Lista amplia"),
    ServerFile("Che",                   "che.m3u",                            "Lista grande AR"),
    ServerFile("Marco Plus",            "tv_channels_MarcoSP140S1_plus.m3u",  "Lista premium (34 MB)"),
    ServerFile("Pibita Plus",           "tv_channels_pibita_plus.m3u",        "Lista premium (55 MB)"),
)

data class PlaylistsUiState(
    val playlists: List<Playlist> = emptyList(),
    val isRefreshing: Boolean = false,
    val refreshingPlaylistId: Long? = null,
    val error: String? = null,
    val successMessage: String? = null,
    // Servidor
    val serverUrl: String = "",
    val importingFilename: String? = null,
    val importedFilenames: Set<String> = emptySet()
)

@HiltViewModel
class PlaylistsViewModel @Inject constructor(
    private val repository: PlaylistRepository,
    private val dataStore: DataStore<Preferences>
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlaylistsUiState())
    val uiState: StateFlow<PlaylistsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.allPlaylists.collect { playlists ->
                _uiState.update { it.copy(playlists = playlists) }
            }
        }
        // Leer server URL guardado
        viewModelScope.launch {
            dataStore.data
                .map { prefs -> prefs[BrowserViewModel.KEY_SERVER_URL] ?: "" }
                .collect { url -> _uiState.update { it.copy(serverUrl = url) } }
        }
    }

    /** Importa un archivo del servidor usando la URL base guardada */
    fun importServerFile(serverFile: ServerFile) {
        val baseUrl = _uiState.value.serverUrl.trimEnd('/')
        if (baseUrl.isBlank()) {
            _uiState.update { it.copy(error = "Primero conecta al servidor en la pestaña Ubuntu") }
            return
        }
        val fullUrl = "$baseUrl/${serverFile.filename}"
        _uiState.update { it.copy(importingFilename = serverFile.filename) }
        viewModelScope.launch {
            val playlist = Playlist(
                name = serverFile.name,
                url = fullUrl,
                source = PlaylistSource.UBUNTU
            )
            val id = repository.addPlaylist(playlist)
            val result = repository.refreshPlaylist(playlist.copy(id = id))
            result.fold(
                onSuccess = { count ->
                    _uiState.update {
                        it.copy(
                            importingFilename = null,
                            importedFilenames = it.importedFilenames + serverFile.filename,
                            successMessage = "✓ ${serverFile.name}: $count canales cargados"
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            importingFilename = null,
                            error = "Error al cargar ${serverFile.name}: ${err.message}"
                        )
                    }
                }
            )
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
            val playlist = Playlist(name = name, url = url, source = PlaylistSource.UBUNTU)
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
        viewModelScope.launch { repository.deletePlaylist(playlist) }
    }

    fun clearError() = _uiState.update { it.copy(error = null) }
    fun clearSuccess() = _uiState.update { it.copy(successMessage = null) }
}
