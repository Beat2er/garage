package de.beat2er.garage.tile

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.beat2er.garage.data.DeviceRepository
import de.beat2er.garage.ui.theme.GarageTheme
import de.beat2er.garage.widget.WidgetTriggerService

class TileDevicePickerActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val devices = DeviceRepository(this).getDevices()
        if (devices.isEmpty()) {
            finish()
            return
        }

        setContent {
            GarageTheme {
                AlertDialog(
                    onDismissRequest = { finish() },
                    title = { Text("Garage auslösen") },
                    text = {
                        Column {
                            devices.forEach { device ->
                                TextButton(
                                    onClick = { triggerDevice(device) },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = device.name,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                                if (device != devices.last()) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(horizontal = 8.dp),
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                            }
                        }
                    },
                    confirmButton = {},
                    dismissButton = {
                        TextButton(onClick = { finish() }) {
                            Text("Abbrechen")
                        }
                    }
                )
            }
        }
    }

    private fun triggerDevice(device: de.beat2er.garage.data.Device) {
        val intent = Intent(this, WidgetTriggerService::class.java).apply {
            putExtra(WidgetTriggerService.EXTRA_WIDGET_TYPE, "tile")
            putExtra(WidgetTriggerService.EXTRA_DEVICE_NAME, device.name)
            putExtra(WidgetTriggerService.EXTRA_DEVICE_MAC, device.mac)
            putExtra(WidgetTriggerService.EXTRA_DEVICE_PASSWORD, device.password)
            putExtra(WidgetTriggerService.EXTRA_DEVICE_SWITCH_ID, device.switchId)
        }
        startForegroundService(intent)
        finish()
    }
}
