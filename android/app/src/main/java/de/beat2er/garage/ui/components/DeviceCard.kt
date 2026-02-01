package de.beat2er.garage.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Garage
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.beat2er.garage.data.Device
import de.beat2er.garage.ui.theme.*
import de.beat2er.garage.viewmodel.DeviceConnectionState
import de.beat2er.garage.viewmodel.DeviceUiState

@Composable
fun DeviceCard(
    device: Device,
    state: DeviceUiState,
    onTrigger: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor by animateColorAsState(
        targetValue = when (state.connectionState) {
            DeviceConnectionState.CONNECTING -> Warning
            DeviceConnectionState.CONNECTED -> Success
            DeviceConnectionState.TRIGGERED -> Success
            DeviceConnectionState.ERROR -> Accent
            DeviceConnectionState.DISCONNECTED -> Border
        },
        label = "borderColor"
    )

    val iconTint by animateColorAsState(
        targetValue = when (state.connectionState) {
            DeviceConnectionState.CONNECTED, DeviceConnectionState.TRIGGERED -> Success
            else -> TextDim
        },
        label = "iconTint"
    )

    val statusColor by animateColorAsState(
        targetValue = when (state.connectionState) {
            DeviceConnectionState.CONNECTED -> Success
            DeviceConnectionState.TRIGGERED -> Success
            DeviceConnectionState.CONNECTING -> Warning
            DeviceConnectionState.ERROR -> Accent
            DeviceConnectionState.DISCONNECTED -> TextDim
        },
        label = "statusColor"
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = BgCard),
        border = CardDefaults.outlinedCardBorder().let {
            androidx.compose.foundation.BorderStroke(2.dp, borderColor)
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onTrigger)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Garage Icon
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(BgDark),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Garage,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(28.dp)
                )
            }

            // Device Info
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = device.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = device.mac.uppercase(),
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp
                    ),
                    color = TextDim
                )
                Text(
                    text = state.statusText,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = statusColor
                )
            }

            // Edit Button
            IconButton(
                onClick = onEdit,
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(BgDark)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Edit,
                    contentDescription = "Bearbeiten",
                    tint = TextDim,
                    modifier = Modifier.size(18.dp)
                )
            }

            // Trigger Button
            Button(
                onClick = onTrigger,
                modifier = Modifier.size(56.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Accent,
                    disabledContainerColor = BgDark
                ),
                contentPadding = PaddingValues(0.dp),
                enabled = state.connectionState != DeviceConnectionState.CONNECTING
            ) {
                Icon(
                    imageVector = Icons.Rounded.Bolt,
                    contentDescription = "Auslösen",
                    tint = TextPrimary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}
