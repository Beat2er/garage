package de.beat2er.garage.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import de.beat2er.garage.data.Device
import de.beat2er.garage.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditDeviceSheet(
    device: Device,
    onDismiss: () -> Unit,
    onSave: (Device) -> Unit,
    onDelete: (Device) -> Unit
) {
    var name by remember { mutableStateOf(device.name) }
    var password by remember { mutableStateOf(device.password) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

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
                text = "Geraet bearbeiten",
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

            // MAC (readonly)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "MAC-ADRESSE",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextDim
                )
                OutlinedTextField(
                    value = device.mac.uppercase(),
                    onValueChange = {},
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        disabledBorderColor = Border,
                        disabledContainerColor = BgDark,
                        disabledTextColor = TextDim
                    ),
                    enabled = false,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace
                    )
                )
            }

            // Passwort
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "PASSWORT",
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

            // Speichern Button
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        onSave(device.copy(name = name.trim(), password = password))
                        onDismiss()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Accent),
                enabled = name.isNotBlank()
            ) {
                Text(
                    text = "Speichern",
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
                )
            }

            // Loeschen Button
            if (showDeleteConfirm) {
                OutlinedButton(
                    onClick = {
                        onDelete(device)
                        onDismiss()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Accent),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Accent)
                ) {
                    Text(
                        text = "Wirklich loeschen?",
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = Accent
                    )
                }
            } else {
                OutlinedButton(
                    onClick = { showDeleteConfirm = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Accent),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Accent)
                ) {
                    Text(
                        text = "Geraet loeschen",
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = Accent
                    )
                }
            }
        }
    }
}
