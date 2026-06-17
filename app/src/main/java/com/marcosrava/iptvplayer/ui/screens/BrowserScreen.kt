package com.marcosrava.iptvplayer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.marcosrava.iptvplayer.data.model.RemoteFile
import com.marcosrava.iptvplayer.ui.viewmodel.BrowserViewModel
import com.marcosrava.iptvplayer.ui.viewmodel.PlaylistsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(
    onBack: () -> Unit,
    browserViewModel: BrowserViewModel = hiltViewModel(),
    playlistsViewModel: PlaylistsViewModel = hiltViewModel()
) {
    val uiState by browserViewModel.uiState.collectAsState()
    var serverInput by remember { mutableStateOf("192.168.1.") }
    var showConnectDialog by remember { mutableStateOf(uiState.serverUrl.isBlank()) }
    var fileToImport by remember { mutableStateOf<RemoteFile?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "Servidor Ubuntu",
                            style = MaterialTheme.typography.titleMedium
                        )
                        if (uiState.serverUrl.isNotBlank()) {
                            Text(
                                uiState.serverUrl,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                navigationIcon = {
                    if (browserViewModel.canNavigateUp()) {
                        IconButton(onClick = { browserViewModel.navigateUp() }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Atrás")
                        }
                    } else {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.Close, contentDescription = "Cerrar")
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { showConnectDialog = true }) {
                        Icon(Icons.Default.Computer, contentDescription = "Conectar")
                    }
                    IconButton(onClick = { browserViewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Actualizar")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
        ) {
            when {
                uiState.serverUrl.isBlank() -> {
                    // Pantalla inicial de instrucciones
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Default.Computer,
                            contentDescription = null,
                            modifier = Modifier.size(72.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(24.dp))
                        Text(
                            "Conecta con tu Ubuntu",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(16.dp))
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("En tu Ubuntu, ejecuta:", style = MaterialTheme.typography.labelMedium)
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "cd /ruta/a/tus/listas\npython3 -m http.server 8080",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .background(
                                            MaterialTheme.colorScheme.surfaceVariant,
                                            RoundedCornerShape(8.dp)
                                        )
                                        .padding(12.dp)
                                        .fillMaxWidth()
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "Luego introduce la IP de tu Ubuntu (ej: 192.168.1.X:8080)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                        }
                        Spacer(Modifier.height(24.dp))
                        Button(
                            onClick = { showConnectDialog = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Link, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Conectar a Ubuntu")
                        }
                    }
                }

                uiState.isLoading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(16.dp))
                            Text("Cargando archivos...")
                        }
                    }
                }

                uiState.error != null -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Default.WifiOff,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            uiState.error ?: "Error de conexión",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = { showConnectDialog = true }) {
                            Text("Reconectar")
                        }
                    }
                }

                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        items(uiState.files, key = { it.url }) { file ->
                            FileListItem(
                                file = file,
                                onClick = {
                                    if (file.isDirectory) {
                                        browserViewModel.navigateTo(file)
                                    } else if (file.name.endsWith(".m3u", ignoreCase = true) ||
                                        file.name.endsWith(".m3u8", ignoreCase = true)) {
                                        fileToImport = file
                                    }
                                }
                            )
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                thickness = 0.5.dp
                            )
                        }
                    }
                }
            }
        }
    }

    // Diálogo conectar
    if (showConnectDialog) {
        AlertDialog(
            onDismissRequest = {
                if (uiState.serverUrl.isNotBlank()) showConnectDialog = false
            },
            title = { Text("Conectar al servidor") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Introduce la IP:puerto de tu Ubuntu",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    OutlinedTextField(
                        value = serverInput,
                        onValueChange = { serverInput = it },
                        label = { Text("IP:Puerto") },
                        placeholder = { Text("192.168.1.100:8080") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        browserViewModel.connectToServer(serverInput)
                        showConnectDialog = false
                    },
                    enabled = serverInput.isNotBlank()
                ) { Text("Conectar") }
            },
            dismissButton = {
                if (uiState.serverUrl.isNotBlank()) {
                    TextButton(onClick = { showConnectDialog = false }) { Text("Cancelar") }
                }
            }
        )
    }

    // Diálogo importar archivo M3U
    fileToImport?.let { file ->
        AlertDialog(
            onDismissRequest = { fileToImport = null },
            title = { Text("Importar lista M3U") },
            text = { Text("¿Añadir \"${file.name}\" a tus playlists?") },
            confirmButton = {
                Button(
                    onClick = {
                        playlistsViewModel.addUbuntuPlaylist(
                            file.name.substringBeforeLast("."),
                            file.url
                        )
                        fileToImport = null
                        onBack()
                    }
                ) { Text("Importar") }
            },
            dismissButton = {
                TextButton(onClick = { fileToImport = null }) { Text("Cancelar") }
            }
        )
    }
}

@Composable
fun FileListItem(
    file: RemoteFile,
    onClick: () -> Unit
) {
    val isM3U = file.name.endsWith(".m3u", ignoreCase = true) ||
            file.name.endsWith(".m3u8", ignoreCase = true)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = when {
                file.isDirectory -> Icons.Default.Folder
                isM3U -> Icons.Default.PlaylistPlay
                file.name.endsWith(".xml", ignoreCase = true) -> Icons.Default.Description
                else -> Icons.Default.InsertDriveFile
            },
            contentDescription = null,
            tint = when {
                file.isDirectory -> MaterialTheme.colorScheme.primary
                isM3U -> MaterialTheme.colorScheme.secondary
                else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            },
            modifier = Modifier.size(32.dp)
        )
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = file.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (file.isDirectory) FontWeight.Medium else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (isM3U) {
                Text(
                    "Toca para importar",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }
        if (file.isDirectory) {
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
            )
        }
    }
}
