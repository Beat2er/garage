package de.beat2er.garage.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import de.beat2er.garage.data.Device
import de.beat2er.garage.data.DeviceRepository
import de.beat2er.garage.ui.theme.BgCard
import de.beat2er.garage.ui.theme.BgDark
import de.beat2er.garage.ui.theme.GarageTheme
import de.beat2er.garage.ui.theme.TextDim
import de.beat2er.garage.ui.theme.TextPrimary
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

class WidgetConfigActivity : ComponentActivity() {

    private val scope = MainScope()
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Set canceled result in case the user backs out
        setResult(RESULT_CANCELED)

        // Get the widget id from the intent
        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        val devices = DeviceRepository(this).getDevices()

        // Check for pre-selected device (from "pin widget" in app settings)
        val settingsPrefs = getSharedPreferences("garage_settings", MODE_PRIVATE)
        val pendingDeviceId = settingsPrefs.getString("pending_widget_device", null)
        val preselectedDevice = if (pendingDeviceId != null) {
            settingsPrefs.edit().remove("pending_widget_device").commit()
            devices.find { it.id == pendingDeviceId }
        } else null

        if (preselectedDevice != null) {
            Log.d("WidgetConfig", "Auto-config: ${preselectedDevice.name} (widget=$appWidgetId)")
        }

        setContent {
            GarageTheme {
                // Auto-select if pre-selected device exists
                if (preselectedDevice != null) {
                    androidx.compose.runtime.LaunchedEffect(Unit) {
                        onDeviceChosen(preselectedDevice)
                    }
                }

                ConfigScreen(
                    devices = devices,
                    onDeviceSelected = { device -> onDeviceChosen(device) }
                )
            }
        }
    }

    private fun onDeviceChosen(device: Device) {
        scope.launch {
            // Get the GlanceId for this widget
            val glanceId = GlanceAppWidgetManager(this@WidgetConfigActivity)
                .getGlanceIdBy(appWidgetId)

            // Save device config to Glance DataStore
            updateAppWidgetState(this@WidgetConfigActivity, PreferencesGlanceStateDefinition, glanceId) { prefs ->
                prefs.toMutablePreferences().apply {
                    this[WidgetKeys.DEVICE_ID] = device.id
                    this[WidgetKeys.DEVICE_NAME] = device.name
                    this[WidgetKeys.DEVICE_MAC] = device.mac
                    this[WidgetKeys.DEVICE_PASSWORD] = device.password
                    this[WidgetKeys.DEVICE_SWITCH_ID] = device.switchId.toString()
                    this[WidgetKeys.STATUS] = WidgetStatus.IDLE
                    this[WidgetKeys.STATUS_TEXT] = "Tippen zum Auslösen"
                }
            }

            // Update the widget
            GarageWidget().update(this@WidgetConfigActivity, glanceId)

            // Return success
            val resultValue = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            setResult(RESULT_OK, resultValue)
            finish()
        }
    }
}

@Composable
private fun ConfigScreen(
    devices: List<Device>,
    onDeviceSelected: (Device) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            Spacer(modifier = Modifier.height(32.dp))
            Text(
                text = "Widget einrichten",
                color = TextPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Wähle ein Gerät für das Widget",
                color = TextDim,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(24.dp))

            if (devices.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Keine Geräte",
                            color = TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Öffne die App um Geräte hinzuzufügen",
                            color = TextDim,
                            fontSize = 14.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(devices) { device ->
                        DeviceItem(
                            device = device,
                            onClick = { onDeviceSelected(device) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceItem(
    device: Device,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(BgCard)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = device.name,
                color = TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = device.mac,
                color = TextDim,
                fontSize = 12.sp
            )
        }
    }
}
