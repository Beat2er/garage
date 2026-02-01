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
        val appWidgetId = intent?.getIntExtra(EXTRA_APP_WIDGET_ID, -1) ?: -1
        val deviceName = intent?.getStringExtra(EXTRA_DEVICE_NAME) ?: "?"
        val deviceMac = intent?.getStringExtra(EXTRA_DEVICE_MAC)
        val password = intent?.getStringExtra(EXTRA_DEVICE_PASSWORD)
        val switchId = intent?.getIntExtra(EXTRA_DEVICE_SWITCH_ID, 0) ?: 0

        if (appWidgetId == -1 || deviceMac.isNullOrEmpty()) {
            Log.e(TAG, "Ungültige Parameter: widgetId=$appWidgetId mac=$deviceMac")
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

        Log.d(TAG, "Service gestartet: $deviceName MAC=$deviceMac switchId=$switchId widgetId=$appWidgetId")

        scope.launch {
            val glanceId = try {
                GlanceAppWidgetManager(this@WidgetTriggerService)
                    .getGlanceIdBy(appWidgetId)
            } catch (e: Exception) {
                Log.e(TAG, "GlanceId nicht gefunden: ${e.message}")
                stopSelf(startId)
                return@launch
            }

            var bleManager: ShellyBleManager? = null
            try {
                withTimeout(TRIGGER_TIMEOUT_MS) {
                    updateStatus(glanceId, WidgetStatus.CONNECTING, "Verbinde...")

                    bleManager = ShellyBleManager(this@WidgetTriggerService)

                    // Try direct MAC connection
                    Log.d(TAG, "Direktverbindung: $deviceMac")
                    updateStatus(glanceId, WidgetStatus.CONNECTING, "MAC $deviceMac")
                    try {
                        bleManager!!.connect(deviceMac)
                        Log.d(TAG, "Direktverbindung OK")
                    } catch (e: Exception) {
                        Log.d(TAG, "Direktverbindung fehlgeschlagen: ${e.message}")
                        updateStatus(glanceId, WidgetStatus.CONNECTING, "Scan...")

                        val macSuffix = deviceMac.replace(":", "").uppercase()
                        Log.d(TAG, "Scan: suffix=$macSuffix")
                        bleManager!!.disconnect()
                        bleManager = ShellyBleManager(this@WidgetTriggerService)
                        bleManager!!.connectByScan(macSuffix)
                        Log.d(TAG, "Scan-Verbindung OK")
                    }

                    // Trigger the switch
                    updateStatus(glanceId, WidgetStatus.CONNECTING, "Sende...")
                    Log.d(TAG, "Switch.Set switchId=$switchId")
                    val success = bleManager!!.triggerSwitch(
                        switchId = switchId,
                        password = password?.ifEmpty { null }
                    )

                    if (success) {
                        Log.d(TAG, "Erfolgreich ausgelöst")
                        updateStatus(glanceId, WidgetStatus.TRIGGERED, "Ausgelöst!")
                    } else {
                        Log.d(TAG, "triggerSwitch returned false")
                        updateStatus(glanceId, WidgetStatus.ERROR, "Keine Antwort")
                    }
                }
            } catch (e: SecurityException) {
                Log.e(TAG, "Bluetooth-Berechtigung fehlt", e)
                updateStatus(glanceId, WidgetStatus.ERROR, "Berechtigung fehlt")
            } catch (e: Exception) {
                Log.e(TAG, "Fehlgeschlagen: ${e.javaClass.simpleName}: ${e.message}", e)
                val msg = e.message?.take(40) ?: e.javaClass.simpleName
                updateStatus(glanceId, WidgetStatus.ERROR, msg)
            } finally {
                try { bleManager?.disconnect() } catch (_: Exception) {}
            }

            // Reset to idle after delay
            delay(RESET_DELAY_MS)
            updateStatus(glanceId, WidgetStatus.IDLE, "Tippen zum Auslösen")

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
}
