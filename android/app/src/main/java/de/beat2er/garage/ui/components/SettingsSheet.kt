package de.beat2er.garage.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.beat2er.garage.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    onDismiss: () -> Unit,
    debugMode: Boolean,
    debugLogs: List<String>,
    onToggleDebug: () -> Unit,
    onClearLogs: () -> Unit
) {
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
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text(
                text = "Einstellungen",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary
            )

            // Debug Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "DEBUG-LOGGING",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextDim
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Zeigt detaillierte BLE-Logs",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextDim
                    )
                }
                Switch(
                    checked = debugMode,
                    onCheckedChange = { onToggleDebug() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = TextPrimary,
                        checkedTrackColor = Accent,
                        uncheckedThumbColor = TextDim,
                        uncheckedTrackColor = BgDark,
                        uncheckedBorderColor = Border
                    )
                )
            }

            // Debug Log Viewer
            if (debugMode) {
                HorizontalDivider(color = Border)

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "DEBUG LOG",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextDim
                    )

                    if (debugLogs.isEmpty()) {
                        Text(
                            text = "Noch keine Log-Einträge",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextDim,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 200.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(BgDark)
                                .padding(12.dp)
                        ) {
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                items(debugLogs) { log ->
                                    Text(
                                        text = log,
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 10.sp
                                        ),
                                        color = TextDim
                                    )
                                }
                            }
                        }

                        OutlinedButton(
                            onClick = onClearLogs,
                            modifier = Modifier.fillMaxWidth().height(40.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextDim),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Border)
                        ) {
                            Text("Log leeren", fontSize = 12.sp)
                        }
                    }
                }
            }

            HorizontalDivider(color = Border)

            // Version
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "VERSION",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextDim
                )
                Text(
                    text = "v1.0.0",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextPrimary
                )
            }
        }
    }
}
