package com.marcosrava.iptvplayer.ui.viewmodel

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.marcosrava.iptvplayer.data.model.RemoteFile
import com.marcosrava.iptvplayer.network.HttpFileBrowser
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import javax.inject.Inject

data class BrowserUiState(
    val serverUrl: String = "",
    val currentPath: String = "",
    val files: List<RemoteFile> = emptyList(),
    val isLoading: Boolean = false,
    val isDiscovering: Boolean = false,
    val discoveredServers: List<String> = emptyList(),
    val error: String? = null,
    val navigationStack: List<String> = emptyList()
)

@HiltViewModel
class BrowserViewModel @Inject constructor(
    private val browser: HttpFileBrowser,
    private val dataStore: DataStore<Preferences>,
    @ApplicationContext private val context: Context
) : ViewModel() {

    companion object {
        val KEY_SERVER_URL = stringPreferencesKey("last_server_url")
        val KEY_SERVER_PORT = stringPreferencesKey("last_server_port")
        const val DEFAULT_PORT = 8080
    }

    private val _uiState = MutableStateFlow(BrowserUiState())
    val uiState: StateFlow<BrowserUiState> = _uiState.asStateFlow()

    init {
        // Al arrancar, recuperar la URL guardada y conectar automáticamente
        viewModelScope.launch {
            dataStore.data.map { it[KEY_SERVER_URL] ?: "" }.first().let { savedUrl ->
                if (savedUrl.isNotBlank()) {
                    _uiState.update { it.copy(serverUrl = savedUrl, currentPath = savedUrl) }
                    loadDirectory(savedUrl)
                }
            }
        }
    }

    fun connectToServer(serverUrl: String) {
        val normalizedUrl = if (serverUrl.startsWith("http")) serverUrl
        else "http://$serverUrl"
        _uiState.update {
            it.copy(
                serverUrl = normalizedUrl,
                currentPath = normalizedUrl,
                navigationStack = emptyList(),
                discoveredServers = emptyList()
            )
        }
        // Guardar URL para próximas sesiones
        viewModelScope.launch {
            dataStore.edit { it[KEY_SERVER_URL] = normalizedUrl }
        }
        loadDirectory(normalizedUrl)
    }

    /** Escanea la red local buscando servidores HTTP en el puerto indicado.
     *  Si hay un servidor ya conectado, desconecta primero para mostrar los resultados. */
    fun discoverServers(port: Int = DEFAULT_PORT) {
        viewModelScope.launch {
            // Limpiar servidor actual para que el estado de resultados se muestre correctamente
            dataStore.edit { it.remove(KEY_SERVER_URL) }
            _uiState.update { it.copy(isDiscovering = true, discoveredServers = emptyList(), error = null, serverUrl = "", currentPath = "", files = emptyList()) }

            val localIp = withContext(Dispatchers.IO) { getLocalIpAddress() }
            if (localIp == null) {
                _uiState.update {
                    it.copy(isDiscovering = false, error = "No se pudo obtener la IP local. ¿Estás conectado al WiFi?")
                }
                return@launch
            }

            val subnet = localIp.substringBeforeLast(".")
            val found = mutableListOf<String>()

            // Escanear en bloques de 32 para no saturar la red
            (1..254).chunked(32).forEach { chunk ->
                chunk.map { i ->
                    async(Dispatchers.IO) {
                        val host = "$subnet.$i"
                        try {
                            Socket().use { socket ->
                                socket.connect(InetSocketAddress(host, port), 400)
                                "http://$host:$port"
                            }
                        } catch (e: Exception) {
                            null
                        }
                    }
                }.awaitAll().filterNotNull().let { found.addAll(it) }

                // Actualizar la lista en tiempo real según se van encontrando
                if (found.isNotEmpty()) {
                    _uiState.update { it.copy(discoveredServers = found.toList()) }
                }
            }

            _uiState.update {
                it.copy(
                    isDiscovering = false,
                    discoveredServers = found,
                    error = if (found.isEmpty()) "No se encontraron servidores en la red. ¿Está corriendo 'python3 -m http.server $port' en tu Ubuntu?" else null
                )
            }
        }
    }

    private fun getLocalIpAddress(): String? {
        try {
            NetworkInterface.getNetworkInterfaces()?.toList()?.forEach { iface ->
                if (iface.isLoopback || !iface.isUp) return@forEach
                iface.inetAddresses?.toList()?.forEach { addr ->
                    if (!addr.isLoopbackAddress && addr is java.net.Inet4Address) {
                        return addr.hostAddress
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    fun navigateTo(directory: RemoteFile) {
        val currentPath = _uiState.value.currentPath
        _uiState.update { it.copy(navigationStack = it.navigationStack + currentPath) }
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
                            error = "Error: ${error.message}\n\nAsegúrate de que en Ubuntu ejecutas:\npython3 -m http.server $DEFAULT_PORT"
                        )
                    }
                }
            )
        }
    }

    fun refresh() = loadDirectory(_uiState.value.currentPath)

    fun clearError() = _uiState.update { it.copy(error = null) }

    fun disconnect() {
        viewModelScope.launch {
            dataStore.edit { it.remove(KEY_SERVER_URL) }
        }
        _uiState.update { BrowserUiState() }
    }
}
