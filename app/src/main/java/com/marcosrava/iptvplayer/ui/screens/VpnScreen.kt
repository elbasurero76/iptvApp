package com.marcosrava.iptvplayer.ui.screens

import android.app.Activity
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.marcosrava.iptvplayer.ui.viewmodel.VpnCountry
import com.marcosrava.iptvplayer.ui.viewmodel.VpnStatus
import com.marcosrava.iptvplayer.ui.viewmodel.VpnViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VpnScreen(
    viewModel: VpnViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showCredentials by remember { mutableStateOf(false) }
    var editUser by remember(uiState.username) { mutableStateOf(uiState.username) }
    var editPass by remember(uiState.password) { mutableStateOf(uiState.password) }
    var showPass by remember { mutableStateOf(false) }

    // Lanzador para el Intent de aprobación del sistema VPN
    val approvalLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                viewModel.onApprovalGranted()
            }
        } else {
            viewModel.onApprovalDenied()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(16.dp))

        // ── Indicador de estado ───────────────────────────────────────────
        VpnStatusIndicator(status = uiState.status)

        Spacer(Modifier.height(24.dp))

        // ── Mensaje de error ──────────────────────────────────────────────
        uiState.error?.let { err ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        err,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        // ── Selección de país ─────────────────────────────────────────────
        Text(
            "Conectar a",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))

        VpnCountry.values().forEach { country ->
            CountryCard(
                country = country,
                isSelected = uiState.selectedCountry == country,
                isConnected = uiState.status == VpnStatus.CONNECTED && uiState.selectedCountry == country,
                enabled = uiState.status != VpnStatus.CONNECTING,
                onClick = { viewModel.selectCountry(country) }
            )
            Spacer(Modifier.height(8.dp))
        }

        Spacer(Modifier.height(16.dp))

        // ── Botón principal Conectar / Desconectar ────────────────────────
        if (!viewModel.isApiSupported) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "La VPN integrada requiere Android 12 o superior.",
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            when (uiState.status) {
                VpnStatus.DISCONNECTED, VpnStatus.ERROR -> {
                    Button(
                        onClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                val intent = viewModel.connect(uiState.selectedCountry)
                                intent?.let { approvalLauncher.launch(it) }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(Icons.Default.VpnKey, null)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Conectar a ${uiState.selectedCountry.flag} ${uiState.selectedCountry.displayName}",
                            style = MaterialTheme.typography.titleSmall
                        )
                    }
                }
                VpnStatus.CONNECTING -> {
                    OutlinedButton(
                        onClick = { viewModel.cancelConnect() },
                        modifier = Modifier.fillMaxWidth().height(56.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Conectando… (toca para cancelar)")
                    }
                }
                VpnStatus.CONNECTED -> {
                    Button(
                        onClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                viewModel.disconnect()
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(Icons.Default.VpnKeyOff, null)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Desconectar VPN",
                            style = MaterialTheme.typography.titleSmall
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        // ── Credenciales (expandible) ─────────────────────────────────────
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showCredentials = !showCredentials },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Key, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Credenciales PIA",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        if (showCredentials) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        null,
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
                if (showCredentials) {
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = editUser,
                        onValueChange = { editUser = it },
                        label = { Text("Usuario PIA") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = editPass,
                        onValueChange = { editPass = it },
                        label = { Text("Contraseña PIA") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = if (showPass) VisualTransformation.None
                        else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showPass = !showPass }) {
                                Icon(
                                    if (showPass) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    null
                                )
                            }
                        }
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = {
                            viewModel.updateCredentials(editUser, editPass)
                            showCredentials = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = editUser.isNotBlank() && editPass.isNotBlank()
                    ) {
                        Text("Guardar")
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // ── Info ──────────────────────────────────────────────────────────
        Text(
            "Utiliza tu cuenta de Private Internet Access (PIA).\n" +
                    "La primera vez el sistema pedirá permiso para crear la VPN.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun VpnStatusIndicator(status: VpnStatus) {
    val color by animateColorAsState(
        targetValue = when (status) {
            VpnStatus.CONNECTED -> Color(0xFF4CAF50)
            VpnStatus.CONNECTING -> Color(0xFFFFA000)
            VpnStatus.ERROR -> MaterialTheme.colorScheme.error
            VpnStatus.DISCONNECTED -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
        },
        label = "vpn_color"
    )

    val pulseAnim = rememberInfiniteTransition(label = "pulse")
    val scale by pulseAnim.animateFloat(
        initialValue = 1f, targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            tween(800), RepeatMode.Reverse
        ),
        label = "scale"
    )

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.12f))
                .border(2.dp, color, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (status == VpnStatus.CONNECTED) Icons.Default.VpnKey
                else Icons.Default.VpnKeyOff,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(44.dp)
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = when (status) {
                VpnStatus.CONNECTED -> "VPN Conectada"
                VpnStatus.CONNECTING -> "Conectando…"
                VpnStatus.DISCONNECTED -> "VPN Desconectada"
                VpnStatus.ERROR -> "Error de conexión"
            },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

@Composable
fun CountryCard(
    country: VpnCountry,
    isSelected: Boolean,
    isConnected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val borderColor = when {
        isConnected -> Color(0xFF4CAF50)
        isSelected -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val bgColor = when {
        isConnected -> Color(0xFF4CAF50).copy(alpha = 0.08f)
        isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        else -> MaterialTheme.colorScheme.surface
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (isSelected || isConnected) 2.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(enabled = enabled, onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(country.flag, fontSize = 28.sp)
            Spacer(Modifier.width(16.dp))
            Text(
                country.displayName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.weight(1f)
            )
            if (isConnected) {
                Text(
                    "CONECTADO",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF4CAF50)
                )
            } else if (isSelected) {
                Icon(
                    Icons.Default.RadioButtonChecked, null,
                    tint = MaterialTheme.colorScheme.primary
                )
            } else {
                Icon(
                    Icons.Default.RadioButtonUnchecked, null,
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                )
            }
        }
    }
}
