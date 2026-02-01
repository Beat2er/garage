package de.beat2er.garage.ui.components

import android.util.Base64
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import de.beat2er.garage.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    onDismiss: () -> Unit,
    exportConfig: () -> String,
    onImport: (String) -> Int,
    onShowToast: (String, Boolean) -> Unit
) {
    val clipboardManager = LocalClipboardManager.current

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

            // Teilen Sektion
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "KONFIGURATION TEILEN",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextDim
                )

                Text(
                    text = "Exportiere deine Geraete als Link (ohne Passwoerter)",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextDim
                )

                OutlinedButton(
                    onClick = {
                        val config = exportConfig()
                        val base64 = Base64.encodeToString(
                            config.toByteArray(Charsets.UTF_8),
                            Base64.NO_WRAP
                        )
                        clipboardManager.setText(AnnotatedString(base64))
                        onShowToast("Link kopiert", false)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Border)
                ) {
                    Text("Konfiguration exportieren")
                }

                OutlinedButton(
                    onClick = {
                        val clip = clipboardManager.getText()?.text
                        if (clip != null) {
                            try {
                                val json = if (clip.contains("#import=")) {
                                    val b64 = clip.substringAfter("#import=")
                                    String(Base64.decode(b64, Base64.DEFAULT), Charsets.UTF_8)
                                } else if (!clip.startsWith("{")) {
                                    String(Base64.decode(clip, Base64.DEFAULT), Charsets.UTF_8)
                                } else {
                                    clip
                                }
                                onImport(json)
                            } catch (e: Exception) {
                                onShowToast("Import fehlgeschlagen", true)
                            }
                        } else {
                            onShowToast("Zwischenablage leer", true)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Border)
                ) {
                    Text("Aus Zwischenablage importieren")
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
