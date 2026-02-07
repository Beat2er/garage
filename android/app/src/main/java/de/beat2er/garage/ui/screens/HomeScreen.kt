package de.beat2er.garage.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Garage
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.beat2er.garage.data.Device
import de.beat2er.garage.ui.components.AddDeviceSheet
import de.beat2er.garage.ui.components.DeviceCard
import de.beat2er.garage.ui.components.EditDeviceSheet
import de.beat2er.garage.ui.components.SettingsSheet
import de.beat2er.garage.ui.components.ShareSheet
import de.beat2er.garage.ui.theme.*
import de.beat2er.garage.update.UpdateInfo
import de.beat2er.garage.viewmodel.DeviceUiState
import de.beat2er.garage.viewmodel.GarageUiState

@Composable
fun HomeScreen(
    uiState: GarageUiState,
    onTriggerDevice: (Device) -> Unit,
    onAddDevice: (String, String, String) -> Unit,
    onUpdateDevice: (Device) -> Unit,
    onDeleteDevice: (Device) -> Unit,
    onImportDevices: (String) -> Int,
    onExportConfig: () -> String,
    onClearToast: () -> Unit,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit,
    onToggleDebug: () -> Unit,
    onClearLogs: () -> Unit,
    onShowToast: (String, Boolean) -> Unit,
    onDismissUpdate: () -> Unit,
    onOpenDownload: (String) -> Unit,
    onCheckUpdate: () -> Unit,
    onPinWidget: (Device) -> Unit,
    versionName: String,
    modifier: Modifier = Modifier
) {
    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    var showAddSheet by remember { mutableStateOf(false) }
    var showShareSheet by remember { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var editingDevice by remember { mutableStateOf<Device?>(null) }

    // Toast
    val toastMessage = uiState.toastMessage
    LaunchedEffect(toastMessage) {
        if (toastMessage != null) {
            kotlinx.coroutines.delay(3000)
            onClearToast()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(BgDark)
    ) {
        // Subtiler Glow-Effekt wie in der PWA
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Accent.copy(alpha = 0.06f),
                            Color.Transparent
                        ),
                        radius = 600f
                    )
                )
        )

        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(top = 24.dp, bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "GARAGE",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 4.sp
                    ),
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Bluetooth Garagentor-Steuerung",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextDim
                )
            }

            // Update-Banner
            val updateInfo = uiState.updateInfo
            if (updateInfo != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = BgCard),
                    border = CardDefaults.outlinedCardBorder().let {
                        androidx.compose.foundation.BorderStroke(1.dp, Accent)
                    }
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Update verfügbar: v${updateInfo.versionName}",
                            style = MaterialTheme.typography.titleSmall,
                            color = Accent
                        )
                        if (updateInfo.changelog.isNotBlank()) {
                            Text(
                                text = updateInfo.changelog,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextDim
                            )
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { onOpenDownload(updateInfo.downloadUrl) },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Accent),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Text("Herunterladen", style = MaterialTheme.typography.bodySmall)
                            }
                            OutlinedButton(
                                onClick = onDismissUpdate,
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextDim),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Border),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Text("Später", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Content
            if (uiState.devices.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Garage,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = TextDim.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        text = "Noch keine Geräte konfiguriert",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextDim,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Füge dein erstes Garagentor hinzu",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextDim.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 100.dp + navBarBottom)
                ) {
                    items(
                        items = uiState.devices,
                        key = { it.id }
                    ) { device ->
                        val deviceState = uiState.deviceStates[device.mac] ?: DeviceUiState()
                        DeviceCard(
                            device = device,
                            state = deviceState,
                            onTrigger = { onTriggerDevice(device) },
                            onEdit = { editingDevice = device },
                            modifier = Modifier.animateItem()
                        )
                    }
                }
            }
        }

        // Bottom Actions
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            BgDark.copy(alpha = 0.9f),
                            BgDark
                        )
                    )
                )
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally)
        ) {
            // Hinzufügen
            OutlinedButton(
                onClick = { showAddSheet = true },
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = BgCard,
                    contentColor = TextPrimary
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, Border)
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Hinzufügen", style = MaterialTheme.typography.bodyMedium)
            }

            // Teilen
            OutlinedButton(
                onClick = { showShareSheet = true },
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = BgCard,
                    contentColor = TextPrimary
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, Border)
            ) {
                Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Teilen", style = MaterialTheme.typography.bodyMedium)
            }

            // Einstellungen
            OutlinedButton(
                onClick = { showSettingsSheet = true },
                modifier = Modifier.height(50.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = BgCard,
                    contentColor = TextPrimary
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, Border),
                contentPadding = PaddingValues(horizontal = 14.dp)
            ) {
                Icon(Icons.Rounded.Settings, contentDescription = "Einstellungen", modifier = Modifier.size(18.dp))
            }
        }

        // Toast
        AnimatedVisibility(
            visible = toastMessage != null,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 80.dp)
        ) {
            if (toastMessage != null) {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = BgCard),
                    border = CardDefaults.outlinedCardBorder().let {
                        androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (uiState.toastIsError) Accent else Success
                        )
                    }
                ) {
                    Text(
                        text = toastMessage,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (uiState.toastIsError) Accent else Success
                    )
                }
            }
        }
    }

    // Bottom Sheets
    if (showAddSheet) {
        AddDeviceSheet(
            onDismiss = { showAddSheet = false },
            onAdd = onAddDevice,
            isScanning = uiState.isScanning,
            scanResults = uiState.scanResults,
            onStartScan = onStartScan,
            onStopScan = onStopScan
        )
    }

    editingDevice?.let { device ->
        EditDeviceSheet(
            device = device,
            onDismiss = { editingDevice = null },
            onSave = onUpdateDevice,
            onDelete = onDeleteDevice
        )
    }

    if (showShareSheet) {
        ShareSheet(
            onDismiss = { showShareSheet = false },
            exportConfig = onExportConfig,
            onImport = onImportDevices,
            onShowToast = onShowToast
        )
    }

    if (showSettingsSheet) {
        SettingsSheet(
            onDismiss = { showSettingsSheet = false },
            debugMode = uiState.debugMode,
            debugLogs = uiState.debugLogs,
            onToggleDebug = onToggleDebug,
            onClearLogs = onClearLogs,
            versionName = versionName,
            onCheckUpdate = onCheckUpdate,
            devices = uiState.devices,
            onPinWidget = { device ->
                showSettingsSheet = false
                onPinWidget(device)
            }
        )
    }
}
