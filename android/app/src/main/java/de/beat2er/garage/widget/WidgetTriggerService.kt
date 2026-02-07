package de.beat2er.garage.widget

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import de.beat2er.garage.R
import de.beat2er.garage.ble.ShellyBleManager
import kotlinx.coroutines.*

@SuppressLint("MissingPermission")
class WidgetTriggerService : Service() {

    companion object {
        private const val TAG = "WidgetTrigger"
        private const val CHANNEL_ID = "widget_trigger"
        private const val NOTIFICATION_ID = 1001
        private const val TRIGGER_TIMEOUT_MS = 25_000L
        private const val RESET_DELAY_MS = 3_000L

        const val EXTRA_APP_WIDGET_ID = "app_widget_id"
        const val EXTRA_DEVICE_NAME = "device_name"
        const val EXTRA_DEVICE_MAC = "device_mac"
        const val EXTRA_DEVICE_PASSWORD = "device_password"
        const val EXTRA_DEVICE_SWITCH_ID = "device_switch_id"
        const val EXTRA_WIDGET_TYPE = "widget_type"
        const val EXTRA_DEVICE_ID = "device_id"

        fun createChannel(context: Context) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Widget-Auslösung",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Zeigt Status beim Auslösen via Widget"
                setShowBadge(false)
            }
            context.getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val widgetType = intent?.getStringExtra(EXTRA_WIDGET_TYPE) ?: "single"
        val appWidgetId = intent?.getIntExtra(EXTRA_APP_WIDGET_ID, -1) ?: -1
        val deviceId = intent?.getStringExtra(EXTRA_DEVICE_ID)
        val deviceName = intent?.getStringExtra(EXTRA_DEVICE_NAME) ?: "?"
        val deviceMac = intent?.getStringExtra(EXTRA_DEVICE_MAC)
        val password = intent?.getStringExtra(EXTRA_DEVICE_PASSWORD)
        val switchId = intent?.getIntExtra(EXTRA_DEVICE_SWITCH_ID, 0) ?: 0

        if (widgetType == "single" && (appWidgetId == -1 || deviceMac.isNullOrEmpty())) {
            Log.e(TAG, "Ungültige Parameter (single): widgetId=$appWidgetId mac=$deviceMac")
            stopSelf(startId)
            return START_NOT_STICKY
        }
        if (widgetType == "multi" && (deviceId.isNullOrEmpty() || deviceMac.isNullOrEmpty())) {
            Log.e(TAG, "Ungültige Parameter (multi): deviceId=$deviceId mac=$deviceMac")
            stopSelf(startId)
            return START_NOT_STICKY
        }
        if (widgetType == "tile" && deviceMac.isNullOrEmpty()) {
            Log.e(TAG, "Ungültige Parameter (tile): mac=$deviceMac")
            stopSelf(startId)
            return START_NOT_STICKY
        }

        createChannel(this)
        val notification = buildNotification("Verbinde mit $deviceName...")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        Log.d(TAG, "Service gestartet ($widgetType): $deviceName MAC=$deviceMac switchId=$switchId")

        scope.launch {
            // For single widget: resolve glanceId from appWidgetId
            // For multi widget: we update all instances
            val singleGlanceId = if (widgetType == "single") {
                try {
                    GlanceAppWidgetManager(this@WidgetTriggerService)
                        .getGlanceIdBy(appWidgetId)
                } catch (e: Exception) {
                    Log.e(TAG, "GlanceId nicht gefunden: ${e.message}")
                    stopSelf(startId)
                    return@launch
                }
            } else null

            val statusUpdater: suspend (String, String) -> Unit = when (widgetType) {
                "multi" -> { status, text -> updateMultiStatus(deviceId!!, status, text) }
                "tile" -> { _, _ -> } // No widget UI to update for tiles
                else -> { status, text -> updateStatus(singleGlanceId!!, status, text) }
            }

            var bleManager: ShellyBleManager? = null
            try {
                withTimeout(TRIGGER_TIMEOUT_MS) {
                    statusUpdater(WidgetStatus.CONNECTING, "Verbinde...")

                    bleManager = ShellyBleManager(this@WidgetTriggerService)

                    // Try direct MAC connection
                    Log.d(TAG, "Direktverbindung: $deviceMac")
                    statusUpdater(WidgetStatus.CONNECTING, "MAC $deviceMac")
                    try {
                        bleManager!!.connect(deviceMac!!)
                        Log.d(TAG, "Direktverbindung OK")
                    } catch (e: Exception) {
                        Log.d(TAG, "Direktverbindung fehlgeschlagen: ${e.message}")
                        statusUpdater(WidgetStatus.CONNECTING, "Scan...")

                        val macSuffix = deviceMac!!.replace(":", "").uppercase()
                        Log.d(TAG, "Scan: suffix=$macSuffix")
                        bleManager!!.disconnect()
                        bleManager = ShellyBleManager(this@WidgetTriggerService)
                        bleManager!!.connectByScan(macSuffix)
                        Log.d(TAG, "Scan-Verbindung OK")
                    }

                    // Trigger the switch
                    statusUpdater(WidgetStatus.CONNECTING, "Sende...")
                    Log.d(TAG, "Switch.Set switchId=$switchId")
                    val success = bleManager!!.triggerSwitch(
                        switchId = switchId,
                        password = password?.ifEmpty { null }
                    )

                    if (success) {
                        Log.d(TAG, "Erfolgreich ausgelöst")
                        statusUpdater(WidgetStatus.TRIGGERED, "Ausgelöst!")
                    } else {
                        Log.d(TAG, "triggerSwitch returned false")
                        statusUpdater(WidgetStatus.ERROR, "Keine Antwort")
                    }
                }
            } catch (e: SecurityException) {
                Log.e(TAG, "Bluetooth-Berechtigung fehlt", e)
                statusUpdater(WidgetStatus.ERROR, "Berechtigung fehlt")
            } catch (e: Exception) {
                Log.e(TAG, "Fehlgeschlagen: ${e.javaClass.simpleName}: ${e.message}", e)
                val msg = e.message?.take(40) ?: e.javaClass.simpleName
                statusUpdater(WidgetStatus.ERROR, msg)
            } finally {
                try { bleManager?.disconnect() } catch (_: Exception) {}
            }

            // Reset to idle after delay (skip for tile — no widget state to reset)
            if (widgetType != "tile") {
                delay(RESET_DELAY_MS)
                if (widgetType == "multi") {
                    statusUpdater(WidgetStatus.IDLE, "Bereit")
                } else {
                    statusUpdater(WidgetStatus.IDLE, "Tippen zum Auslösen")
                }
            }

            stopSelf(startId)
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun buildNotification(text: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Garage Widget")
            .setContentText(text)
            .setSilent(true)
            .setOngoing(true)
            .build()
    }

    private suspend fun updateStatus(
        glanceId: androidx.glance.GlanceId,
        status: String,
        text: String
    ) {
        updateAppWidgetState(
            this, PreferencesGlanceStateDefinition, glanceId
        ) { prefs ->
            prefs.toMutablePreferences().apply {
                this[WidgetKeys.STATUS] = status
                this[WidgetKeys.STATUS_TEXT] = text
            }
        }
        GarageWidget().update(this, glanceId)
    }

    private suspend fun updateMultiStatus(
        deviceId: String,
        status: String,
        text: String
    ) {
        val manager = GlanceAppWidgetManager(this)
        val multiIds = manager.getGlanceIds(MultiGarageWidget::class.java)
        for (id in multiIds) {
            updateAppWidgetState(
                this, PreferencesGlanceStateDefinition, id
            ) { prefs ->
                prefs.toMutablePreferences().apply {
                    this[MultiWidgetKeys.statusKey(deviceId)] = status
                    this[MultiWidgetKeys.statusTextKey(deviceId)] = text
                }
            }
            MultiGarageWidget().update(this, id)
        }
    }
}
