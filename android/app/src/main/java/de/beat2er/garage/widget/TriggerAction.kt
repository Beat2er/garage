package de.beat2er.garage.widget

import android.content.Context
import android.util.Log
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import de.beat2er.garage.ble.ShellyBleManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout

class TriggerAction : ActionCallback {

    companion object {
        private const val TAG = "WidgetTrigger"
        private const val TRIGGER_TIMEOUT_MS = 25_000L
        private const val RESET_DELAY_MS = 3_000L
    }

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        // Read current state
        val prefs = getAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId)

        // Debounce: ignore if already connecting
        if (prefs[WidgetKeys.STATUS] == WidgetStatus.CONNECTING) {
            Log.d(TAG, "Bereits verbunden, ignoriere Tap")
            return
        }

        // Read device config
        val deviceMac = prefs[WidgetKeys.DEVICE_MAC]
        if (deviceMac.isNullOrEmpty()) {
            Log.d(TAG, "Kein Gerät konfiguriert")
            return
        }

        val password = prefs[WidgetKeys.DEVICE_PASSWORD]
        val switchId = prefs[WidgetKeys.DEVICE_SWITCH_ID]?.toIntOrNull() ?: 0
        val deviceName = prefs[WidgetKeys.DEVICE_NAME] ?: "?"

        Log.d(TAG, "Trigger: $deviceName MAC=$deviceMac switchId=$switchId")

        // Set connecting status
        updateStatus(context, glanceId, WidgetStatus.CONNECTING, "Verbinde...")

        var bleManager: ShellyBleManager? = null
        try {
            withTimeout(TRIGGER_TIMEOUT_MS) {
                bleManager = ShellyBleManager(context)

                // Try direct MAC connection
                Log.d(TAG, "Versuche Direktverbindung: $deviceMac")
                updateStatus(context, glanceId, WidgetStatus.CONNECTING, "MAC $deviceMac")
                try {
                    bleManager!!.connect(deviceMac)
                    Log.d(TAG, "Direktverbindung OK")
                } catch (e: Exception) {
                    Log.d(TAG, "Direktverbindung fehlgeschlagen: ${e.message}")
                    updateStatus(context, glanceId, WidgetStatus.CONNECTING, "Scan...")

                    // Fallback: scan
                    val macSuffix = deviceMac.replace(":", "").uppercase()
                    Log.d(TAG, "Versuche Scan: suffix=$macSuffix")
                    bleManager!!.disconnect()
                    bleManager = ShellyBleManager(context)
                    bleManager!!.connectByScan(macSuffix)
                    Log.d(TAG, "Scan-Verbindung OK")
                }

                // Trigger the switch
                updateStatus(context, glanceId, WidgetStatus.CONNECTING, "Sende...")
                Log.d(TAG, "Sende Switch.Set switchId=$switchId")
                val success = bleManager!!.triggerSwitch(
                    switchId = switchId,
                    password = password?.ifEmpty { null }
                )

                if (success) {
                    Log.d(TAG, "Erfolgreich ausgelöst")
                    updateStatus(context, glanceId, WidgetStatus.TRIGGERED, "Ausgelöst!")
                } else {
                    Log.d(TAG, "triggerSwitch returned false")
                    updateStatus(context, glanceId, WidgetStatus.ERROR, "Keine Antwort")
                }
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Bluetooth-Berechtigung fehlt", e)
            updateStatus(context, glanceId, WidgetStatus.ERROR, "Berechtigung fehlt")
        } catch (e: Exception) {
            Log.e(TAG, "Trigger fehlgeschlagen: ${e.javaClass.simpleName}: ${e.message}", e)
            val msg = e.message?.take(40) ?: e.javaClass.simpleName
            updateStatus(context, glanceId, WidgetStatus.ERROR, msg)
        } finally {
            try {
                bleManager?.disconnect()
            } catch (_: Exception) {}
        }

        // Reset to idle after delay
        delay(RESET_DELAY_MS)
        updateStatus(context, glanceId, WidgetStatus.IDLE, "Tippen zum Auslösen")
    }

    private suspend fun updateStatus(
        context: Context,
        glanceId: GlanceId,
        status: String,
        text: String
    ) {
        updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
            prefs.toMutablePreferences().apply {
                this[WidgetKeys.STATUS] = status
                this[WidgetKeys.STATUS_TEXT] = text
            }
        }
        GarageWidget().update(context, glanceId)
    }
}
