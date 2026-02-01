package de.beat2er.garage.widget

import android.content.Context
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import de.beat2er.garage.data.DeviceRepository
import de.beat2er.garage.ui.theme.Accent
import de.beat2er.garage.ui.theme.BgCard
import de.beat2er.garage.ui.theme.Success
import de.beat2er.garage.ui.theme.TextDim
import de.beat2er.garage.ui.theme.TextPrimary
import de.beat2er.garage.ui.theme.Warning

class GarageWidget : GlanceAppWidget() {

    companion object {
        private const val TAG = "GarageWidget"
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // Auto-config: check for pending device from pin-from-settings
        tryAutoConfig(context, id)

        provideContent {
            GarageWidgetContent()
        }
    }

    private suspend fun tryAutoConfig(context: Context, glanceId: GlanceId) {
        try {
            val settingsPrefs = context.getSharedPreferences("garage_settings", Context.MODE_PRIVATE)
            val pendingDeviceId = settingsPrefs.getString("pending_widget_device", null) ?: return

            // Check if this widget is unconfigured
            val state = getAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId)
            if (state[WidgetKeys.DEVICE_NAME] != null) return

            // Consume the pending device
            settingsPrefs.edit().remove("pending_widget_device").commit()

            val device = DeviceRepository(context).getDevices()
                .find { it.id == pendingDeviceId } ?: return

            // Get appWidgetId for this glanceId
            val manager = GlanceAppWidgetManager(context)
            val allWidgetIds = manager.getGlanceIds(GarageWidget::class.java)
            var appWidgetId = -1
            for (gId in allWidgetIds) {
                if (gId == glanceId) {
                    // Find the matching appWidgetId by iterating all known IDs
                    val appWidgetIds = android.appwidget.AppWidgetManager.getInstance(context)
                        .getAppWidgetIds(
                            android.content.ComponentName(context, GarageWidgetReceiver::class.java)
                        )
                    for (wId in appWidgetIds) {
                        if (manager.getGlanceIdBy(wId) == glanceId) {
                            appWidgetId = wId
                            break
                        }
                    }
                    break
                }
            }

            Log.d(TAG, "Auto-config: ${device.name} (widgetId=$appWidgetId)")

            updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
                prefs.toMutablePreferences().apply {
                    this[WidgetKeys.DEVICE_ID] = device.id
                    this[WidgetKeys.DEVICE_NAME] = device.name
                    this[WidgetKeys.DEVICE_MAC] = device.mac
                    this[WidgetKeys.DEVICE_PASSWORD] = device.password
                    this[WidgetKeys.DEVICE_SWITCH_ID] = device.switchId.toString()
                    if (appWidgetId != -1) {
                        this[WidgetKeys.APP_WIDGET_ID] = appWidgetId.toString()
                    }
                    this[WidgetKeys.STATUS] = WidgetStatus.IDLE
                    this[WidgetKeys.STATUS_TEXT] = "Tippen zum Auslösen"
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Auto-config fehlgeschlagen: ${e.message}", e)
        }
    }

    @Composable
    private fun GarageWidgetContent() {
        val state = currentState<Preferences>()
        val deviceName = state[WidgetKeys.DEVICE_NAME]
        val status = state[WidgetKeys.STATUS] ?: WidgetStatus.IDLE
        val statusText = state[WidgetKeys.STATUS_TEXT] ?: "Tippen zum Auslösen"
        val isConfigured = deviceName != null

        val statusColor = when (status) {
            WidgetStatus.CONNECTING -> ColorProvider(Warning)
            WidgetStatus.TRIGGERED -> ColorProvider(Success)
            WidgetStatus.ERROR -> ColorProvider(Accent)
            else -> ColorProvider(TextDim)
        }

        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .cornerRadius(16.dp)
                .background(BgCard)
                .clickable(actionRunCallback<TriggerAction>())
                .padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            if (isConfigured) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = deviceName!!,
                        style = TextStyle(
                            color = ColorProvider(TextPrimary),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        maxLines = 1
                    )
                    Spacer(modifier = GlanceModifier.height(4.dp))
                    Text(
                        text = statusText,
                        style = TextStyle(
                            color = statusColor,
                            fontSize = 11.sp
                        ),
                        maxLines = 2
                    )
                }
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Kein Gerät",
                        style = TextStyle(
                            color = ColorProvider(TextDim),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Spacer(modifier = GlanceModifier.height(4.dp))
                    Text(
                        text = "Lange drücken\nzum Einrichten",
                        style = TextStyle(
                            color = ColorProvider(TextDim),
                            fontSize = 11.sp
                        )
                    )
                }
            }
        }
    }
}
