package de.beat2er.garage.widget

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition

class TriggerAction : ActionCallback {

    companion object {
        private const val TAG = "WidgetTrigger"
    }

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val prefs = getAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId)

        // Debounce: ignore if already connecting
        if (prefs[WidgetKeys.STATUS] == WidgetStatus.CONNECTING) {
            Log.d(TAG, "Bereits verbunden, ignoriere Tap")
            return
        }

        val deviceMac = prefs[WidgetKeys.DEVICE_MAC]
        if (deviceMac.isNullOrEmpty()) {
            Log.d(TAG, "Kein Gerät konfiguriert")
            return
        }

        val appWidgetId = prefs[WidgetKeys.APP_WIDGET_ID]?.toIntOrNull()
        if (appWidgetId == null) {
            Log.e(TAG, "Keine Widget-ID gespeichert")
            return
        }

        val deviceName = prefs[WidgetKeys.DEVICE_NAME] ?: "?"
        val password = prefs[WidgetKeys.DEVICE_PASSWORD] ?: ""
        val switchId = prefs[WidgetKeys.DEVICE_SWITCH_ID]?.toIntOrNull() ?: 0

        Log.d(TAG, "Starte Service: $deviceName MAC=$deviceMac widgetId=$appWidgetId")

        // Set connecting status immediately for visual feedback
        updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { p ->
            p.toMutablePreferences().apply {
                this[WidgetKeys.STATUS] = WidgetStatus.CONNECTING
                this[WidgetKeys.STATUS_TEXT] = "Verbinde..."
            }
        }
        GarageWidget().update(context, glanceId)

        // Start ForegroundService for BLE operations
        val intent = Intent(context, WidgetTriggerService::class.java).apply {
            putExtra(WidgetTriggerService.EXTRA_APP_WIDGET_ID, appWidgetId)
            putExtra(WidgetTriggerService.EXTRA_DEVICE_NAME, deviceName)
            putExtra(WidgetTriggerService.EXTRA_DEVICE_MAC, deviceMac)
            putExtra(WidgetTriggerService.EXTRA_DEVICE_PASSWORD, password)
            putExtra(WidgetTriggerService.EXTRA_DEVICE_SWITCH_ID, switchId)
        }

        try {
            context.startForegroundService(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Service-Start fehlgeschlagen: ${e.message}", e)
            updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { p ->
                p.toMutablePreferences().apply {
                    this[WidgetKeys.STATUS] = WidgetStatus.ERROR
                    this[WidgetKeys.STATUS_TEXT] = "Start fehlgeschlagen"
                }
            }
            GarageWidget().update(context, glanceId)
        }
    }
}
