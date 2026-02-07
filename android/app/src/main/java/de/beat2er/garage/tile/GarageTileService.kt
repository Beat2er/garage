package de.beat2er.garage.tile

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import de.beat2er.garage.MainActivity
import de.beat2er.garage.R
import de.beat2er.garage.data.DeviceRepository
import de.beat2er.garage.widget.WidgetTriggerService
import kotlinx.coroutines.*

@SuppressLint("MissingPermission")
class GarageTileService : TileService() {

    companion object {
        private const val TAG = "GarageTile"
        private const val TILE_RESET_DELAY_MS = 3_000L
    }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onStartListening() {
        super.onStartListening()
        updateTileAppearance()
    }

    override fun onClick() {
        super.onClick()

        val devices = DeviceRepository(this).getDevices()
        Log.d(TAG, "Tile geklickt, ${devices.size} Geräte gefunden")

        when (devices.size) {
            0 -> openMainApp()
            1 -> triggerDevice(devices.first())
            else -> openDevicePicker()
        }
    }

    private fun openMainApp() {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivityCompat(intent)
    }

    private fun triggerDevice(device: de.beat2er.garage.data.Device) {
        // Set tile to active state
        qsTile?.let { tile ->
            tile.state = Tile.STATE_ACTIVE
            tile.updateTile()
        }

        val serviceIntent = Intent(this, WidgetTriggerService::class.java).apply {
            putExtra(WidgetTriggerService.EXTRA_WIDGET_TYPE, "tile")
            putExtra(WidgetTriggerService.EXTRA_DEVICE_NAME, device.name)
            putExtra(WidgetTriggerService.EXTRA_DEVICE_MAC, device.mac)
            putExtra(WidgetTriggerService.EXTRA_DEVICE_PASSWORD, device.password)
            putExtra(WidgetTriggerService.EXTRA_DEVICE_SWITCH_ID, device.switchId)
        }
        startForegroundService(serviceIntent)

        // Reset tile state after delay
        scope.launch {
            delay(TILE_RESET_DELAY_MS)
            qsTile?.let { tile ->
                tile.state = Tile.STATE_INACTIVE
                tile.updateTile()
            }
        }
    }

    private fun openDevicePicker() {
        val intent = Intent(this, TileDevicePickerActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivityCompat(intent)
    }

    @Suppress("DEPRECATION")
    private fun startActivityCompat(intent: Intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pendingIntent = PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            startActivityAndCollapse(pendingIntent)
        } else {
            startActivityAndCollapse(intent)
        }
    }

    private fun updateTileAppearance() {
        val tile = qsTile ?: return
        val devices = DeviceRepository(this).getDevices()

        tile.state = Tile.STATE_INACTIVE
        tile.label = getString(R.string.tile_label)
        tile.subtitle = when (devices.size) {
            0 -> null
            1 -> devices.first().name
            else -> "${devices.size} Geräte"
        }
        tile.updateTile()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
