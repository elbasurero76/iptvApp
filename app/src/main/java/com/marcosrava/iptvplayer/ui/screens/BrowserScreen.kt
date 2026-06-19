package com.marcosrava.iptvplayer.ui.screens

import androidx.compose.animation.core.*
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.marcosrava.iptvplayer.data.model.RemoteFile
import com.marcosrava.iptvplayer.ui.viewmodel.BrowserViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(
    onBack: () -> Unit,
    browserViewModel: BrowserViewModel = hiltViewModel()
) {
    val uiState by browserViewModel.uiState.collectAsState()
    var serverInput by remember { mutableStateOf("192.168.1.") }
    var showConnectDialog by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Mostrar Snackbar cuando hay mensaje de import
    LaunchedEffect(uiState.importMessage) {
        uiState.importMessage?.let { msg ->
            scope.launch {
                snackbarHostState.showSnackbar(
                    message = msg,
                    duration = SnackbarDuration.Short
                )
            }
            browserViewModel.clearImportMessage()
        }
    }

    // Animación de rotación para el icono de escaneo
    val infiniteTransition = rememberInfiniteTransition(label = "scan")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing)),
        label = "rotation"
    )

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Servidor de listas", style = MaterialTheme.typography.titleMedium)
                        if (uiState.serverUrl.isNotBlank()) {
                            Text(
                                uiState.serverUrl,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                navigationIcon = {
                    if (browserViewModel.canNavigateUp()) {
                        IconButton(onClick = { browserViewModel.navigateUp() }) {
                            Icon(Icons.Default.ArrowBack, "Atrás")
                        }
                    } else {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.Close, "Cerrar")
                        }
                    }
                },
                actions = {
                    // Buscar en red
                    IconButton(
                        onClick = { browserViewModel.discoverServers() },
                        enabled = !uiState.isDiscovering
                    ) {
                        Icon(
                            Icons.Default.Wifi,
                            contentDescription = "Buscar en red",
                            modifier = if (uiState.isDiscovering) Modifier.rotate(rotation) else Modifier,
                            tint = if (uiState.isDiscovering) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    if (uiState.serverUrl.isNotBlank()) {
                        IconButton(onClick = { browserViewModel.refresh() }) {
                            Icon(Icons.Default.Refresh, "Actualizar")
                        }
                        Box {
                            IconButton(onClick = { showOverflowMenu = true }) {
                                Icon(Icons.Default.MoreVert, "Más opciones")
                            }
                            DropdownMenu(
                                expanded = showOverflowMenu,
                                onDismissRequest = { showOverflowMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Introducir IP manual") },
                                    leadingIcon = { Icon(Icons.Default.Computer, null) },
                                    onClick = { showOverflowMenu = false; showConnectDialog = true }
                                )
                                DropdownMenuItem(
                                    text = { Text("Buscar en red") },
                                    leadingIcon = { Icon(Icons.Default.Wifi, null) },
                                    onClick = { showOverflowMenu = false; browserViewModel.discoverServers() }
                                )
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text("Desconectar", color = MaterialTheme.colorScheme.error) },
                                    leadingIcon = {
                                        Icon(Icons.Default.WifiOff, null, tint = MaterialTheme.colorScheme.error)
                                    },
                                    onClick = { showOverflowMenu = false; browserViewModel.disconnect() }
                                )
                            }
                        }
                    } else {
                        IconButton(onClick = { showConnectDialog = true }) {
                            Icon(Icons.Default.Computer, "Conectar manual")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
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
                // ── Escaneando la red ────────────────────────────────────────
                uiState.isDiscovering -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(56.dp))
                        Spacer(Modifier.height(20.dp))
                        Text("Buscando servidores en la red…", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Esto puede tardar 30-60 segundos",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                        if (uiState.discoveredServers.isNotEmpty()) {
                            Spacer(Modifier.height(16.dp))
                            Text(
                                "Encontrados: ${uiState.discoveredServers.size}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                // ── Servidores encontrados ───────────────────────────────────
                uiState.discoveredServers.isNotEmpty() && uiState.serverUrl.isBlank() -> {
                    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                        Text(
                            "Servidores encontrados",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        uiState.discoveredServers.forEach { server ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clickable { browserViewModel.connectToServer(server) },
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Computer, null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(32.dp)
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(server, fontWeight = FontWeight.Bold)
                                        Text(
                                            "Toca para conectar",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                        )
                                    }
                                    Icon(Icons.Default.ChevronRight, null)
                                }
                            }
                        }
                    }
                }

                // ── Sin servidor configurado ─────────────────────────────────
                uiState.serverUrl.isBlank() -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Default.Wifi, null,
                            modifier = Modifier.size(72.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(24.dp))
                        Text(
                            "Conecta con tu servidor de listas",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "En tu Ubuntu, ejecuta:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "bash iptv-server.sh",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                            ),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                        )
                        Spacer(Modifier.height(32.dp))
                        Button(
                            onClick = { browserViewModel.discoverServers() },
                            modifier = Modifier.fillMaxWidth().height(56.dp)
                        ) {
                            Icon(Icons.Default.Wifi, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Buscar servidor automáticamente", style = MaterialTheme.typography.titleSmall)
                        }
                        Spacer(Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = { showConnectDialog = true },
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) {
                            Icon(Icons.Default.Computer, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Introducir IP manualmente")
                        }
                        uiState.error?.let { err ->
                            Spacer(Modifier.height(16.dp))
                            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                                Text(
                                    err, modifier = Modifier.padding(12.dp),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                }

                // ── Cargando archivos ────────────────────────────────────────
                uiState.isLoading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(16.dp))
                            Text("Cargando archivos…")
                        }
                    }
                }

                // ── Error de conexión ────────────────────────────────────────
                uiState.error != null -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Default.WifiOff, null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            uiState.error ?: "Error de conexión",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { browserViewModel.refresh() }) { Text("Reintentar") }
                            OutlinedButton(onClick = { browserViewModel.discoverServers() }) { Text("Buscar en red") }
                        }
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = { browserViewModel.disconnect() }) { Text("Desconectar") }
                    }
                }

                // ── Lista de archivos ────────────────────────────────────────
                else -> {
                    if (uiState.files.isEmpty()) {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                Icons.Default.FolderOpen, null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                            )
                            Spacer(Modifier.height(16.dp))
                            Text(
                                "La carpeta está vacía",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Asegúrate de que hay archivos .m3u en la\ncarpeta donde lanzaste el servidor.",
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                            )
                            Spacer(Modifier.height(24.dp))
                            OutlinedButton(onClick = { browserViewModel.refresh() }) {
                                Icon(Icons.Default.Refresh, null)
                                Spacer(Modifier.width(8.dp))
                                Text("Recargar")
                            }
                        }
                    } else {
                        LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
                            items(uiState.files, key = { it.url }) { file ->
                                FileListItem(
                                    file = file,
                                    isImporting = uiState.importingUrl == file.url,
                                    isImported = file.url in uiState.importedUrls,
                                    onDirClick = { browserViewModel.navigateTo(file) },
                                    onImportClick = { browserViewModel.importPlaylist(file) }
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
    }

    // ── Diálogo conectar manual ─────────────────────────────────────────────
    if (showConnectDialog) {
        AlertDialog(
            onDismissRequest = { showConnectDialog = false },
            title = { Text("Conectar al servidor") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Introduce la IP:puerto de tu servidor\n(ej: 192.168.1.100:8080)",
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
                    onClick = { browserViewModel.connectToServer(serverInput); showConnectDialog = false },
                    enabled = serverInput.isNotBlank()
                ) { Text("Conectar") }
            },
            dismissButton = {
                TextButton(onClick = { showConnectDialog = false }) { Text("Cancelar") }
            }
        )
    }
}

@Composable
fun FileListItem(
    file: RemoteFile,
    isImporting: Boolean,
    isImported: Boolean,
    onDirClick: () -> Unit,
    onImportClick: () -> Unit
) {
    val isM3U = file.name.endsWith(".m3u", ignoreCase = true) ||
            file.name.endsWith(".m3u8", ignoreCase = true)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (file.isDirectory) Modifier.clickable(onClick = onDirClick)
                else Modifier
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icono tipo de archivo
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
                else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            },
            modifier = Modifier.size(32.dp)
        )
        Spacer(Modifier.width(16.dp))

        // Nombre
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = file.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (file.isDirectory || isM3U) FontWeight.Medium else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (isImported) {
                Text(
                    "✓ Añadida",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(Modifier.width(8.dp))

        // Acción derecha
        when {
            file.isDirectory -> Icon(
                Icons.Default.ChevronRight, null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
            )
            isM3U -> {
                if (isImporting) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
                } else if (isImported) {
                    Icon(
                        Icons.Default.CheckCircle, null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                } else {
                    // Botón importar visible y clicable
                    FilledTonalButton(
                        onClick = onImportClick,
                        modifier = Modifier.height(36.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp)
                    ) {
                        Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Importar", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}
