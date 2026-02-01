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

class MultiTriggerAction : ActionCallback {

    companion object {
        private const val TAG = "MultiWidgetTrigger"

        val PARAM_DEVICE_MAC = ActionParameters.Key<String>("device_mac")
        val PARAM_DEVICE_NAME = ActionParameters.Key<String>("device_name")
        val PARAM_DEVICE_PASSWORD = ActionParameters.Key<String>("device_password")
        val PARAM_DEVICE_SWITCH_ID = ActionParameters.Key<String>("device_switch_id")
        val PARAM_DEVICE_ID = ActionParameters.Key<String>("device_id")
    }

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val deviceId = parameters[PARAM_DEVICE_ID] ?: return
        val deviceMac = parameters[PARAM_DEVICE_MAC] ?: return
        val deviceName = parameters[PARAM_DEVICE_NAME] ?: "?"
        val password = parameters[PARAM_DEVICE_PASSWORD] ?: ""
        val switchId = parameters[PARAM_DEVICE_SWITCH_ID]?.toIntOrNull() ?: 0

        // Debounce: check per-device status
        val prefs = getAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId)
        if (prefs[MultiWidgetKeys.statusKey(deviceId)] == WidgetStatus.CONNECTING) {
            Log.d(TAG, "Bereits verbunden für $deviceName, ignoriere Tap")
            return
        }

        Log.d(TAG, "Starte Service: $deviceName MAC=$deviceMac deviceId=$deviceId")

        // Set connecting status immediately for visual feedback
        updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { p ->
            p.toMutablePreferences().apply {
                this[MultiWidgetKeys.statusKey(deviceId)] = WidgetStatus.CONNECTING
                this[MultiWidgetKeys.statusTextKey(deviceId)] = "Verbinde..."
            }
        }
        MultiGarageWidget().update(context, glanceId)

        // Start ForegroundService for BLE operations
        val intent = Intent(context, WidgetTriggerService::class.java).apply {
            putExtra(WidgetTriggerService.EXTRA_WIDGET_TYPE, "multi")
            putExtra(WidgetTriggerService.EXTRA_DEVICE_ID, deviceId)
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
                    this[MultiWidgetKeys.statusKey(deviceId)] = WidgetStatus.ERROR
                    this[MultiWidgetKeys.statusTextKey(deviceId)] = "Start fehlgeschlagen"
                }
            }
            MultiGarageWidget().update(context, glanceId)
        }
    }
}
