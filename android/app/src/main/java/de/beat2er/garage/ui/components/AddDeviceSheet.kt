package de.beat2er.garage.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import de.beat2er.garage.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddDeviceSheet(
    onDismiss: () -> Unit,
    onAdd: (name: String, mac: String, password: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var mac by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

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

            // Name
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "NAME",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextDim
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text("z.B. Hauptgarage", color = TextDim)
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Accent,
                        unfocusedBorderColor = Border,
                        focusedContainerColor = BgDark,
                        unfocusedContainerColor = BgDark,
                        cursorColor = Accent,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    singleLine = true
                )
            }

            // MAC
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "MAC-ADRESSE",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextDim
                )
                OutlinedTextField(
                    value = mac,
                    onValueChange = { mac = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(
                            "CC:DB:A7:CF:EB:02",
                            color = TextDim,
                            fontFamily = FontFamily.Monospace
                        )
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Accent,
                        unfocusedBorderColor = Border,
                        focusedContainerColor = BgDark,
                        unfocusedContainerColor = BgDark,
                        cursorColor = Accent,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    singleLine = true
                )
                Text(
                    text = "WiFi-MAC aus Shelly Webinterface (nicht Bluetooth-MAC!)",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextDim
                )
            }

            // Passwort
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "PASSWORT (OPTIONAL)",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextDim
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text("Shelly Auth Passwort", color = TextDim)
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Accent,
                        unfocusedBorderColor = Border,
                        focusedContainerColor = BgDark,
                        unfocusedContainerColor = BgDark,
                        cursorColor = Accent,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Hinzufuegen Button
            Button(
                onClick = {
                    if (name.isNotBlank() && mac.isNotBlank()) {
                        onAdd(name.trim(), mac.trim(), password)
                        onDismiss()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Accent),
                enabled = name.isNotBlank() && mac.isNotBlank()
            ) {
                Text(
                    text = "Hinzufuegen",
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                    )
                )
            }
        }
    }
}
