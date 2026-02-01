package de.beat2er.garage.ui.components

import android.graphics.Bitmap
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import de.beat2er.garage.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareSheet(
    onDismiss: () -> Unit,
    exportConfig: () -> String,
    onImport: (String) -> Int,
    onShowToast: (String, Boolean) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val clipboardManager = LocalClipboardManager.current

    // QR Scanner via ZXing Activity
    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        val contents = result.contents
        if (contents != null) {
            try {
                val json = if (contents.contains("#import=")) {
                    val b64 = contents.substringAfter("#import=")
                    String(Base64.decode(b64, Base64.DEFAULT), Charsets.UTF_8)
                } else if (!contents.startsWith("{")) {
                    String(Base64.decode(contents, Base64.DEFAULT), Charsets.UTF_8)
                } else {
                    contents
                }
                val count = onImport(json)
                if (count > 0) onDismiss()
            } catch (e: Exception) {
                onShowToast("Import fehlgeschlagen", true)
            }
        }
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
                text = "Konfiguration teilen",
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
                listOf("QR erstellen", "QR scannen").forEachIndexed { index, title ->
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
                // ===== QR erstellen =====
                Text(
                    text = "Andere koennen diesen QR-Code scannen um die Geraete zu importieren",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextDim
                )

                val config = remember { exportConfig() }
                val configBase64 = remember(config) {
                    Base64.encodeToString(config.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
                }

                val qrBitmap = remember(configBase64) {
                    generateQrBitmap(configBase64, 600)
                }

                if (qrBitmap != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White)
                            .padding(20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            bitmap = qrBitmap.asImageBitmap(),
                            contentDescription = "QR-Code",
                            modifier = Modifier.size(200.dp)
                        )
                    }
                } else {
                    Text(
                        text = "Keine Geraete zum Teilen",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextDim,
                        modifier = Modifier.padding(40.dp)
                    )
                }

                OutlinedButton(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(configBase64))
                        onShowToast("Link kopiert", false)
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Border)
                ) {
                    Text("Link kopieren")
                }

            } else {
                // ===== QR scannen =====
                Text(
                    text = "Scanne einen QR-Code um Geraete zu importieren",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextDim
                )

                Button(
                    onClick = {
                        val options = ScanOptions().apply {
                            setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                            setPrompt("QR-Code scannen")
                            setBeepEnabled(false)
                            setOrientationLocked(true)
                            setCameraId(0)
                        }
                        scanLauncher.launch(options)
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Accent)
                ) {
                    Text("Kamera starten")
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
                                val count = onImport(json)
                                if (count > 0) onDismiss()
                            } catch (e: Exception) {
                                onShowToast("Import fehlgeschlagen", true)
                            }
                        } else {
                            onShowToast("Zwischenablage leer", true)
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Border)
                ) {
                    Text("Aus Zwischenablage importieren")
                }
            }
        }
    }
}

private fun generateQrBitmap(content: String, size: Int): Bitmap? {
    if (content.isBlank()) return null
    return try {
        val hints = mapOf(
            EncodeHintType.MARGIN to 1,
            EncodeHintType.CHARACTER_SET to "UTF-8"
        )
        val bitMatrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bitmap.setPixel(x, y, if (bitMatrix[x, y]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt())
            }
        }
        bitmap
    } catch (e: Exception) {
        null
    }
}
