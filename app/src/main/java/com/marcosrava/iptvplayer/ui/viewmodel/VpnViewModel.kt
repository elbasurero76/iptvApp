package com.marcosrava.iptvplayer.ui.viewmodel

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.VpnManager
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class VpnStatus { DISCONNECTED, CONNECTING, CONNECTED, ERROR }

enum class VpnCountry(val displayName: String, val flag: String, val host: String) {
    FRANCE("Francia",    "🇫🇷", "france.privateinternetaccess.com"),
    SPAIN("España",      "🇪🇸", "spain.privateinternetaccess.com"),
    ARGENTINA("Argentina","🇦🇷", "argentina.privateinternetaccess.com")
}

data class VpnUiState(
    val status: VpnStatus = VpnStatus.DISCONNECTED,
    val selectedCountry: VpnCountry = VpnCountry.SPAIN,
    val username: String = "",
    val password: String = "",
    val error: String? = null,
    val needsApproval: Boolean = false,
    val approvalPending: Boolean = false
)

@HiltViewModel
class VpnViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dataStore: DataStore<Preferences>
) : ViewModel() {

    companion object {
        val KEY_VPN_USER = stringPreferencesKey("vpn_username")
        val KEY_VPN_PASS = stringPreferencesKey("vpn_password")
        const val REQUEST_VPN_APPROVAL = 1001
    }

    private val _uiState = MutableStateFlow(VpnUiState())
    val uiState: StateFlow<VpnUiState> = _uiState.asStateFlow()

    private val vpnManager: VpnManager? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            context.getSystemService(VpnManager::class.java)
        else null

    init {
        // Cargar credenciales guardadas
        viewModelScope.launch {
            dataStore.data
                .map { prefs -> (prefs[KEY_VPN_USER] ?: "") to (prefs[KEY_VPN_PASS] ?: "") }
                .collect { (user, pass) ->
                    _uiState.update { it.copy(username = user, password = pass) }
                }
        }
        observeVpnNetwork()
    }

    fun selectCountry(country: VpnCountry) = _uiState.update { it.copy(selectedCountry = country) }

    fun updateCredentials(username: String, password: String) {
        _uiState.update { it.copy(username = username, password = password) }
        viewModelScope.launch {
            dataStore.edit { prefs ->
                prefs[KEY_VPN_USER] = username
                prefs[KEY_VPN_PASS] = password
            }
        }
    }

    fun clearError() = _uiState.update { it.copy(error = null) }

    /**
     * Conecta al país seleccionado.
     * Devuelve un Intent si el sistema necesita aprobación del usuario (primera vez).
     * Devuelve null si ya está aprobado (empieza a conectar directamente).
     */
    @RequiresApi(Build.VERSION_CODES.S) // API 31 = Android 12
    fun connect(country: VpnCountry): android.content.Intent? {
        val username = _uiState.value.username.trim()
        val password = _uiState.value.password.trim()
        if (username.isBlank() || password.isBlank()) {
            _uiState.update { it.copy(error = "Introduce usuario y contraseña de PIA") }
            return null
        }

        _uiState.update { it.copy(status = VpnStatus.CONNECTING, selectedCountry = country, error = null) }

        return try {
            val profile = android.net.Ikev2VpnProfile.Builder(country.host, country.host)
                .setAuthUsernamePassword(username, password, null) // null = usar CAs del sistema
                .build()

            val approvalIntent = vpnManager?.provisionVpnProfile(profile)

            if (approvalIntent == null) {
                // Ya aprobado por el usuario anteriormente — conectar directamente
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    vpnManager?.startProvisionedVpnProfileSession()
                } else {
                    // API 31-32: perfil instalado, pero el usuario debe conectar desde ajustes
                    _uiState.update {
                        it.copy(
                            status = VpnStatus.DISCONNECTED,
                            error = "Perfil VPN instalado. Ve a Ajustes → VPN para conectar.",
                            needsApproval = false
                        )
                    }
                }
                null
            } else {
                // Primera vez: el sistema pide aprobación al usuario
                _uiState.update { it.copy(status = VpnStatus.DISCONNECTED, approvalPending = true) }
                approvalIntent
            }
        } catch (e: Exception) {
            _uiState.update {
                it.copy(
                    status = VpnStatus.ERROR,
                    error = "Error al conectar: ${e.message}\n\nComprueba usuario/contraseña de PIA."
                )
            }
            null
        }
    }

    /** Llamado tras obtener resultado OK del Intent de aprobación */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    fun onApprovalGranted() {
        _uiState.update { it.copy(approvalPending = false, status = VpnStatus.CONNECTING) }
        try {
            vpnManager?.startProvisionedVpnProfileSession()
        } catch (e: Exception) {
            _uiState.update { it.copy(status = VpnStatus.ERROR, error = e.message) }
        }
    }

    fun onApprovalDenied() {
        _uiState.update { it.copy(approvalPending = false, status = VpnStatus.DISCONNECTED) }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    fun disconnect() {
        try {
            vpnManager?.stopProvisionedVpnProfile()
            _uiState.update { it.copy(status = VpnStatus.DISCONNECTED, error = null) }
        } catch (e: Exception) {
            _uiState.update { it.copy(error = "Error al desconectar: ${e.message}") }
        }
    }

    /** Observa la red del sistema para detectar conexión/desconexión VPN */
    private fun observeVpnNetwork() {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_VPN)
            .build()
        cm.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                _uiState.update { it.copy(status = VpnStatus.CONNECTED, error = null) }
            }
            override fun onLost(network: Network) {
                if (_uiState.value.status == VpnStatus.CONNECTED ||
                    _uiState.value.status == VpnStatus.CONNECTING) {
                    _uiState.update { it.copy(status = VpnStatus.DISCONNECTED) }
                }
            }
        })
    }

    val isApiSupported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
}
