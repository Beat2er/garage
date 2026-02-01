package de.beat2er.garage.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BluetoothSearching
import androidx.compose.material.icons.rounded.Garage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.beat2er.garage.ui.theme.*
import de.beat2er.garage.viewmodel.BleScanDevice

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddDeviceSheet(
    onDismiss: () -> Unit,
    onAdd: (name: String, mac: String, password: String) -> Unit,
    isScanning: Boolean = false,
    scanResults: List<BleScanDevice> = emptyList(),
    onStartScan: () -> Unit = {},
    onStopScan: () -> Unit = {}
) {
    var name by remember { mutableStateOf("") }
    var mac by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var selectedTab by remember { mutableIntStateOf(0) }

    DisposableEffect(Unit) {
        onDispose { onStopScan() }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = BgCard,
        contentColor = TextPrimary,
        dragHandle = { BottomSheetDefaults.DragHandle(color = TextDim) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Geraet hinzufuegen",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary
            )

            // Tabs
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(BgDark)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf("Manuell", "Scannen").forEachIndexed { index, title ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selectedTab == index) BgCard else BgDark)
                            .clickable { selectedTab = index }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (selectedTab == index) TextPrimary else TextDim
                        )
                    }
                }
            }

            if (selectedTab == 0) {
                // ===== Manuell Tab =====
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("NAME", style = MaterialTheme.typography.labelSmall, color = TextDim)
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("z.B. Hauptgarage", color = TextDim) },
                        shape = RoundedCornerShape(10.dp),
                        colors = garageTextFieldColors(),
                        singleLine = true
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("MAC-ADRESSE", style = MaterialTheme.typography.labelSmall, color = TextDim)
                    OutlinedTextField(
                        value = mac,
                        onValueChange = { mac = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = {
                            Text("CC:DB:A7:CF:EB:02", color = TextDim, fontFamily = FontFamily.Monospace)
                        },
                        shape = RoundedCornerShape(10.dp),
                        colors = garageTextFieldColors(),
                        singleLine = true
                    )
                    Text(
                        text = "WiFi-MAC aus Shelly Webinterface (nicht Bluetooth-MAC!)",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextDim
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("PASSWORT (OPTIONAL)", style = MaterialTheme.typography.labelSmall, color = TextDim)
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Shelly Auth Passwort", color = TextDim) },
                        shape = RoundedCornerShape(10.dp),
                        colors = garageTextFieldColors(),
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = {
                        if (name.isNotBlank() && mac.isNotBlank()) {
                            onAdd(name.trim(), mac.trim(), password)
                            onDismiss()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Accent),
                    enabled = name.isNotBlank() && mac.isNotBlank()
                ) {
                    Text(
                        "Hinzufuegen",
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
                    )
                }

            } else {
                // ===== Scan Tab =====
                Text(
                    text = "Suche nach Shelly-Geraeten in der Naehe",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextDim
                )

                Button(
                    onClick = { if (isScanning) onStopScan() else onStartScan() },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isScanning) BgDark else Accent
                    )
                ) {
                    if (isScanning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = Accent,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Scanne...", color = TextPrimary)
                    } else {
                        Icon(
                            Icons.Rounded.BluetoothSearching,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Bluetooth-Scan starten")
                    }
                }

                // Scan-Ergebnisse
                if (scanResults.isNotEmpty()) {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.heightIn(max = 300.dp)
                    ) {
                        items(scanResults) { device ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        // Friendly name: entferne Modell-Prefix
                                        val friendly = device.name
                                            .replace(Regex("^Shelly[A-Za-z0-9]+-"), "")
                                            .ifEmpty { device.name }
                                        name = friendly
                                        mac = device.mac ?: ""
                                        selectedTab = 0
                                    },
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = BgDark)
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(BgCard),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Rounded.Garage,
                                            contentDescription = null,
                                            tint = TextDim,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = device.name,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = TextPrimary,
                                            fontSize = 14.sp
                                        )
                                        Text(
                                            text = device.mac ?: device.bleAddress,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontFamily = FontFamily.Monospace,
                                            color = TextDim
                                        )
                                    }
                                    Text(
                                        text = if (device.mac != null) "MAC erkannt" else "Manuell",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (device.mac != null) Success else Warning
                                    )
                                }
                            }
                        }
                    }
                } else if (!isScanning) {
                    Text(
                        text = "Noch keine Geraete gefunden",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextDim,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun garageTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Accent,
    unfocusedBorderColor = Border,
    focusedContainerColor = BgDark,
    unfocusedContainerColor = BgDark,
    cursorColor = Accent,
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary
)
