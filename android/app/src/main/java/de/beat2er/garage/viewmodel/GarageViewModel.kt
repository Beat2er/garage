package de.beat2er.garage.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.beat2er.garage.ble.ShellyBleManager
import de.beat2er.garage.data.Device
import de.beat2er.garage.data.DeviceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class DeviceConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    TRIGGERED,
    ERROR
}

data class DeviceUiState(
    val connectionState: DeviceConnectionState = DeviceConnectionState.DISCONNECTED,
    val statusText: String = "Nicht verbunden",
    val errorMessage: String? = null
)

data class GarageUiState(
    val devices: List<Device> = emptyList(),
    val deviceStates: Map<String, DeviceUiState> = emptyMap(),
    val toastMessage: String? = null,
    val toastIsError: Boolean = false
)

class GarageViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "GarageVM"
    }

    private val repository = DeviceRepository(application)
    private val bleManagers = mutableMapOf<String, ShellyBleManager>()

    private val _uiState = MutableStateFlow(GarageUiState())
    val uiState: StateFlow<GarageUiState> = _uiState.asStateFlow()

    init {
        loadDevices()
    }

    private fun loadDevices() {
        val devices = repository.getDevices()
        _uiState.update { it.copy(devices = devices) }
    }

    fun addDevice(name: String, mac: String, password: String = "") {
        val normalizedMac = normalizeMac(mac) ?: return
        val currentDevices = _uiState.value.devices

        if (currentDevices.any { it.mac == normalizedMac }) {
            showToast("Geraet bereits vorhanden", isError = true)
            return
        }

        val device = Device(name = name, mac = normalizedMac, password = password)
        repository.addDevice(device)
        loadDevices()
        showToast("$name hinzugefuegt")
    }

    fun updateDevice(device: Device) {
        repository.updateDevice(device)
        loadDevices()
        showToast("Gespeichert")
    }

    fun deleteDevice(device: Device) {
        bleManagers[device.mac]?.disconnect()
        bleManagers.remove(device.mac)
        repository.removeDevice(device.id)
        _uiState.update { state ->
            state.copy(
                deviceStates = state.deviceStates - device.mac
            )
        }
        loadDevices()
        showToast("Geloescht")
    }

    fun triggerDevice(device: Device) {
        viewModelScope.launch(Dispatchers.IO) {
            val mac = device.mac
            updateDeviceState(mac, DeviceUiState(
                connectionState = DeviceConnectionState.CONNECTING,
                statusText = "Verbinde..."
            ))

            try {
                var manager = bleManagers[mac]
                if (manager == null || !manager.isConnected) {
                    manager?.disconnect()
                    manager = ShellyBleManager(getApplication())
                    bleManagers[mac] = manager

                    // Zuerst Direktverbindung via MAC versuchen
                    try {
                        manager.connect(mac)
                    } catch (e: Exception) {
                        Log.d(TAG, "Direktverbindung fehlgeschlagen, versuche Scan: ${e.message}")
                        manager.disconnect()
                        manager = ShellyBleManager(getApplication())
                        bleManagers[mac] = manager
                        manager.connectByScan(device.macSuffix)
                    }
                }

                updateDeviceState(mac, DeviceUiState(
                    connectionState = DeviceConnectionState.CONNECTED,
                    statusText = "Verbunden"
                ))

                Log.d(TAG, "Sende Switch.Set an ${device.name}")
                val success = manager.triggerSwitch(
                    switchId = device.switchId,
                    password = device.password.ifEmpty { null }
                )

                if (success) {
                    updateDeviceState(mac, DeviceUiState(
                        connectionState = DeviceConnectionState.TRIGGERED,
                        statusText = "Ausgeloest!"
                    ))
                    showToast("${device.name} ausgeloest!")
                } else {
                    updateDeviceState(mac, DeviceUiState(
                        connectionState = DeviceConnectionState.ERROR,
                        statusText = "Fehler",
                        errorMessage = "Keine Antwort"
                    ))
                    showToast("Fehler bei ${device.name}", isError = true)
                }

                // Status nach 3 Sekunden zuruecksetzen
                kotlinx.coroutines.delay(3000)
                updateDeviceState(mac, DeviceUiState(
                    connectionState = DeviceConnectionState.CONNECTED,
                    statusText = "Verbunden"
                ))

            } catch (e: Exception) {
                Log.e(TAG, "Trigger fehlgeschlagen: ${e.message}")
                updateDeviceState(mac, DeviceUiState(
                    connectionState = DeviceConnectionState.ERROR,
                    statusText = "Fehler",
                    errorMessage = e.message
                ))
                showToast("Fehler: ${e.message}", isError = true)

                bleManagers[mac]?.disconnect()
                bleManagers.remove(mac)

                // Status nach 3 Sekunden zuruecksetzen
                kotlinx.coroutines.delay(3000)
                updateDeviceState(mac, DeviceUiState())
            }
        }
    }

    private fun updateDeviceState(mac: String, state: DeviceUiState) {
        _uiState.update { current ->
            current.copy(
                deviceStates = current.deviceStates + (mac to state)
            )
        }
    }

    private fun showToast(message: String, isError: Boolean = false) {
        _uiState.update { it.copy(toastMessage = message, toastIsError = isError) }
    }

    fun clearToast() {
        _uiState.update { it.copy(toastMessage = null) }
    }

    private fun normalizeMac(input: String): String? {
        val cleaned = input.replace(Regex("[^a-fA-F0-9]"), "").lowercase()
        if (cleaned.length != 12) return null
        return cleaned.chunked(2).joinToString(":")
    }

    fun importDevices(configJson: String): Int {
        try {
            val gson = com.google.gson.Gson()
            val config = gson.fromJson(configJson, com.google.gson.JsonObject::class.java)

            if (!config.has("v") || !config.has("d")) return 0

            val devicesArray = config.getAsJsonArray("d")
            val currentMacs = _uiState.value.devices.map { it.mac }.toSet()
            var added = 0

            for (element in devicesArray) {
                val obj = element.asJsonObject
                val name = obj.get("n")?.asString ?: continue
                val mac = obj.get("m")?.asString ?: continue
                val normalizedMac = normalizeMac(mac) ?: continue

                if (normalizedMac !in currentMacs) {
                    repository.addDevice(Device(name = name, mac = normalizedMac))
                    added++
                }
            }

            if (added > 0) {
                loadDevices()
                showToast("$added Geraet(e) importiert")
            } else {
                showToast("Alle Geraete bereits vorhanden")
            }
            return added
        } catch (e: Exception) {
            showToast("Import fehlgeschlagen: ${e.message}", isError = true)
            return 0
        }
    }

    fun getExportConfig(): String {
        val devices = _uiState.value.devices
        val configDevices = devices.map { mapOf("n" to it.name, "m" to it.mac) }
        val config = mapOf("v" to 1, "d" to configDevices)
        return com.google.gson.Gson().toJson(config)
    }

    override fun onCleared() {
        super.onCleared()
        bleManagers.values.forEach { it.disconnect() }
        bleManagers.clear()
    }
}
