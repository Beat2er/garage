package de.beat2er.garage.viewmodel

import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.beat2er.garage.ble.ShellyBleManager
import de.beat2er.garage.data.Device
import de.beat2er.garage.data.DeviceRepository
import de.beat2er.garage.update.UpdateChecker
import de.beat2er.garage.update.UpdateInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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

data class BleScanDevice(
    val name: String,
    val mac: String?,
    val bleAddress: String
)

data class GarageUiState(
    val devices: List<Device> = emptyList(),
    val deviceStates: Map<String, DeviceUiState> = emptyMap(),
    val toastMessage: String? = null,
    val toastIsError: Boolean = false,
    val isScanning: Boolean = false,
    val scanResults: List<BleScanDevice> = emptyList(),
    val debugMode: Boolean = false,
    val debugLogs: List<String> = emptyList(),
    val updateInfo: UpdateInfo? = null
)

class GarageViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "GarageVM"
        private const val MAX_LOG_ENTRIES = 100
    }

    private val repository = DeviceRepository(application)
    private val bleManagers = mutableMapOf<String, ShellyBleManager>()
    private val prefs = application.getSharedPreferences("garage_settings", Context.MODE_PRIVATE)
    private var scanCallback: ScanCallback? = null

    private val _uiState = MutableStateFlow(GarageUiState())
    val uiState: StateFlow<GarageUiState> = _uiState.asStateFlow()

    init {
        _uiState.update { it.copy(debugMode = prefs.getBoolean("debug_mode", false)) }
        loadDevices()
        checkForUpdate()
    }

    private fun loadDevices() {
        val devices = repository.getDevices()
        _uiState.update { it.copy(devices = devices) }
    }

    // ========== Update ==========

    private fun checkForUpdate() {
        viewModelScope.launch {
            val info = UpdateChecker.check()
            if (info != null) {
                _uiState.update { it.copy(updateInfo = info) }
                addDebugLog("Update verfuegbar: ${info.versionName}")
            }
        }
    }

    fun dismissUpdate() {
        _uiState.update { it.copy(updateInfo = null) }
    }

    // ========== Debug ==========

    fun toggleDebugMode() {
        val newMode = !_uiState.value.debugMode
        prefs.edit().putBoolean("debug_mode", newMode).apply()
        _uiState.update { it.copy(debugMode = newMode) }
        addDebugLog(if (newMode) "Debug-Modus aktiviert" else "Debug-Modus deaktiviert")
    }

    fun clearDebugLogs() {
        _uiState.update { it.copy(debugLogs = emptyList()) }
    }

    private fun addDebugLog(message: String) {
        if (!_uiState.value.debugMode) return
        val time = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.GERMANY)
            .format(java.util.Date())
        val entry = "[$time] $message"
        Log.d(TAG, message)
        _uiState.update { state ->
            val logs = (listOf(entry) + state.debugLogs).take(MAX_LOG_ENTRIES)
            state.copy(debugLogs = logs)
        }
    }

    // ========== Device Management ==========

    fun addDevice(name: String, mac: String, password: String = "") {
        val normalizedMac = normalizeMac(mac) ?: run {
            showToast("Ungueltige MAC-Adresse", isError = true)
            return
        }
        val currentDevices = _uiState.value.devices

        if (currentDevices.any { it.mac == normalizedMac }) {
            showToast("Geraet bereits vorhanden", isError = true)
            return
        }

        val device = Device(name = name, mac = normalizedMac, password = password)
        repository.addDevice(device)
        loadDevices()
        showToast("$name hinzugefuegt")
        addDebugLog("Geraet hinzugefuegt: $name ($normalizedMac)")
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
            state.copy(deviceStates = state.deviceStates - device.mac)
        }
        loadDevices()
        showToast("Geloescht")
        addDebugLog("Geraet geloescht: ${device.name}")
    }

    // ========== BLE Scan ==========

    @SuppressLint("MissingPermission")
    fun startBleScan() {
        val bluetoothManager = getApplication<Application>()
            .getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val scanner = bluetoothManager?.adapter?.bluetoothLeScanner

        if (scanner == null) {
            showToast("Bluetooth nicht verfuegbar", isError = true)
            return
        }

        _uiState.update { it.copy(isScanning = true, scanResults = emptyList()) }
        addDebugLog("BLE-Scan gestartet")

        val foundDevices = mutableSetOf<String>()

        scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val name = result.device.name ?: return
                if (!name.startsWith("Shelly")) return
                if (name in foundDevices) return
                foundDevices.add(name)

                val extractedMac = extractMacFromName(name)
                val scanDevice = BleScanDevice(
                    name = name,
                    mac = extractedMac,
                    bleAddress = result.device.address
                )

                addDebugLog("Gefunden: $name (MAC: ${extractedMac ?: "unbekannt"})")

                _uiState.update { state ->
                    state.copy(scanResults = state.scanResults + scanDevice)
                }
            }

            override fun onScanFailed(errorCode: Int) {
                addDebugLog("Scan fehlgeschlagen: $errorCode")
                _uiState.update { it.copy(isScanning = false) }
                showToast("Scan fehlgeschlagen", isError = true)
            }
        }

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        scanner.startScan(emptyList(), settings, scanCallback)

        // Auto-Stop nach 10 Sekunden
        viewModelScope.launch {
            delay(10_000)
            stopBleScan()
        }
    }

    @SuppressLint("MissingPermission")
    fun stopBleScan() {
        val bluetoothManager = getApplication<Application>()
            .getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val scanner = bluetoothManager?.adapter?.bluetoothLeScanner

        scanCallback?.let {
            try {
                scanner?.stopScan(it)
            } catch (_: Exception) {}
            scanCallback = null
        }
        _uiState.update { it.copy(isScanning = false) }
        addDebugLog("BLE-Scan gestoppt")
    }

    private fun extractMacFromName(name: String): String? {
        val match = Regex("-([A-Fa-f0-9]{12})$").find(name) ?: return null
        val hex = match.groupValues[1].uppercase()
        return hex.chunked(2).joinToString(":")
    }

    // ========== BLE Trigger ==========

    fun triggerDevice(device: Device) {
        viewModelScope.launch(Dispatchers.IO) {
            val mac = device.mac
            updateDeviceState(mac, DeviceUiState(
                connectionState = DeviceConnectionState.CONNECTING,
                statusText = "Verbinde..."
            ))
            addDebugLog("Verbinde mit ${device.name} ($mac)")

            try {
                var manager = bleManagers[mac]
                if (manager == null || !manager.isConnected) {
                    manager?.disconnect()
                    manager = ShellyBleManager(getApplication())
                    bleManagers[mac] = manager

                    try {
                        manager.connect(mac)
                        addDebugLog("Direktverbindung erfolgreich")
                    } catch (e: Exception) {
                        addDebugLog("Direktverbindung fehlgeschlagen: ${e.message}")
                        manager.disconnect()
                        manager = ShellyBleManager(getApplication())
                        bleManagers[mac] = manager
                        manager.connectByScan(device.macSuffix)
                        addDebugLog("Scan-Verbindung erfolgreich")
                    }
                }

                updateDeviceState(mac, DeviceUiState(
                    connectionState = DeviceConnectionState.CONNECTED,
                    statusText = "Verbunden"
                ))

                addDebugLog("Sende Switch.Set an ${device.name}")
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
                    addDebugLog("Erfolgreich ausgeloest!")
                } else {
                    updateDeviceState(mac, DeviceUiState(
                        connectionState = DeviceConnectionState.ERROR,
                        statusText = "Fehler",
                        errorMessage = "Keine Antwort"
                    ))
                    showToast("Fehler bei ${device.name}", isError = true)
                    addDebugLog("Fehler: Keine Antwort")
                }

                delay(3000)
                updateDeviceState(mac, DeviceUiState(
                    connectionState = DeviceConnectionState.CONNECTED,
                    statusText = "Verbunden"
                ))

            } catch (e: Exception) {
                Log.e(TAG, "Trigger fehlgeschlagen: ${e.message}")
                addDebugLog("FEHLER: ${e.message}")
                updateDeviceState(mac, DeviceUiState(
                    connectionState = DeviceConnectionState.ERROR,
                    statusText = "Fehler",
                    errorMessage = e.message
                ))
                showToast("Fehler: ${e.message}", isError = true)

                bleManagers[mac]?.disconnect()
                bleManagers.remove(mac)

                delay(3000)
                updateDeviceState(mac, DeviceUiState())
            }
        }
    }

    // ========== Import/Export ==========

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
                addDebugLog("$added Geraet(e) importiert")
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

    // ========== Helpers ==========

    private fun updateDeviceState(mac: String, state: DeviceUiState) {
        _uiState.update { current ->
            current.copy(deviceStates = current.deviceStates + (mac to state))
        }
    }

    fun showToast(message: String, isError: Boolean = false) {
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

    override fun onCleared() {
        super.onCleared()
        stopBleScan()
        bleManagers.values.forEach { it.disconnect() }
        bleManagers.clear()
    }
}
