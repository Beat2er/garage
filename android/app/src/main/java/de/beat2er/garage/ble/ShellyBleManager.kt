package de.beat2er.garage.ble

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@SuppressLint("MissingPermission")
class ShellyBleManager(private val context: Context) {

    companion object {
        private const val TAG = "ShellyBLE"
    }

    private val bluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private val gson = Gson()
    private val writeMutex = Mutex()

    private var bluetoothGatt: BluetoothGatt? = null
    private var dataChar: BluetoothGattCharacteristic? = null
    private var txCtlChar: BluetoothGattCharacteristic? = null
    private var rxCtlChar: BluetoothGattCharacteristic? = null

    private var authRealm: String? = null
    private var authNonce: Long? = null

    private var onWriteComplete: CompletableDeferred<Unit>? = null
    private var onReadComplete: CompletableDeferred<ByteArray>? = null
    private var onServicesReady: CompletableDeferred<Unit>? = null

    val isConnected: Boolean
        get() = bluetoothGatt != null && dataChar != null

    private val gattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            Log.d(TAG, "Verbindungsstatus: $newState (Status: $status)")
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.d(TAG, "Verbunden, suche Services...")
                    gatt.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.d(TAG, "Getrennt")
                    onServicesReady?.completeExceptionally(
                        Exception("Verbindung verloren")
                    )
                    cleanup()
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val service = gatt.getService(ShellyBleConstants.SERVICE_UUID)
                if (service != null) {
                    dataChar = service.getCharacteristic(ShellyBleConstants.RPC_DATA_UUID)
                    txCtlChar = service.getCharacteristic(ShellyBleConstants.TX_CTL_UUID)
                    rxCtlChar = service.getCharacteristic(ShellyBleConstants.RX_CTL_UUID)
                    Log.d(TAG, "Shelly-Service gefunden")
                    onServicesReady?.complete(Unit)
                } else {
                    onServicesReady?.completeExceptionally(
                        Exception("Shelly-Service nicht gefunden")
                    )
                }
            } else {
                onServicesReady?.completeExceptionally(
                    Exception("Service-Suche fehlgeschlagen: $status")
                )
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                onWriteComplete?.complete(Unit)
            } else {
                onWriteComplete?.completeExceptionally(
                    Exception("Schreiben fehlgeschlagen: $status")
                )
            }
        }

        @Deprecated("Deprecated in API 33")
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                @Suppress("DEPRECATION")
                onReadComplete?.complete(characteristic.value ?: byteArrayOf())
            } else {
                onReadComplete?.completeExceptionally(
                    Exception("Lesen fehlgeschlagen: $status")
                )
            }
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                onReadComplete?.complete(value)
            } else {
                onReadComplete?.completeExceptionally(
                    Exception("Lesen fehlgeschlagen: $status")
                )
            }
        }
    }

    suspend fun connect(macAddress: String) {
        val device = bluetoothAdapter?.getRemoteDevice(macAddress)
            ?: throw Exception("Bluetooth nicht verfuegbar")

        Log.d(TAG, "Verbinde mit $macAddress...")

        onServicesReady = CompletableDeferred()

        bluetoothGatt = device.connectGatt(
            context,
            false,
            gattCallback,
            BluetoothDevice.TRANSPORT_LE
        )

        withTimeout(ShellyBleConstants.CONNECTION_TIMEOUT_MS) {
            onServicesReady!!.await()
        }

        Log.d(TAG, "Verbindung hergestellt")
    }

    suspend fun connectByScan(macSuffix: String) {
        val scanner = bluetoothAdapter?.bluetoothLeScanner
            ?: throw Exception("BLE-Scanner nicht verfuegbar")

        Log.d(TAG, "Scanne nach Geraet mit MAC-Suffix: $macSuffix...")

        val deviceFound = CompletableDeferred<BluetoothDevice>()

        val scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val name = result.device.name ?: return
                if (name.uppercase().contains(macSuffix)) {
                    Log.d(TAG, "Geraet gefunden: $name")
                    scanner.stopScan(this)
                    deviceFound.complete(result.device)
                }
            }

            override fun onScanFailed(errorCode: Int) {
                deviceFound.completeExceptionally(Exception("Scan fehlgeschlagen: $errorCode"))
            }
        }

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        scanner.startScan(emptyList<ScanFilter>(), settings, scanCallback)

        try {
            val device = withTimeout(ShellyBleConstants.SCAN_TIMEOUT_MS) {
                deviceFound.await()
            }

            onServicesReady = CompletableDeferred()
            bluetoothGatt = device.connectGatt(
                context,
                false,
                gattCallback,
                BluetoothDevice.TRANSPORT_LE
            )

            withTimeout(ShellyBleConstants.CONNECTION_TIMEOUT_MS) {
                onServicesReady!!.await()
            }
        } catch (e: TimeoutCancellationException) {
            scanner.stopScan(scanCallback)
            throw Exception("Geraet nicht gefunden (Timeout)")
        }
    }

    suspend fun triggerSwitch(switchId: Int = 0, password: String? = null): Boolean {
        val response = sendRpc(
            method = "Switch.Set",
            params = mapOf("id" to switchId, "on" to true),
            password = password
        )
        return response?.has("result") == true
    }

    suspend fun sendRpc(
        method: String,
        params: Map<String, Any>,
        password: String? = null,
        withAuth: Boolean = false
    ): JsonObject? = writeMutex.withLock {
        val request = JsonObject().apply {
            addProperty("id", System.currentTimeMillis())
            addProperty("src", "garage_app")
            addProperty("method", method)
            add("params", gson.toJsonTree(params))

            if (withAuth && authRealm != null && authNonce != null && !password.isNullOrEmpty()) {
                val authMap = ShellyAuth.calculateResponse(password, authRealm!!, authNonce!!)
                add("auth", gson.toJsonTree(authMap))
            }
        }

        val requestBytes = request.toString().toByteArray(Charsets.UTF_8)
        Log.d(TAG, "TX: $method (${requestBytes.size} Bytes)")

        // 1. Laenge senden (4 Bytes Big-Endian)
        val lengthBytes = ByteBuffer.allocate(4).putInt(requestBytes.size).array()
        writeCharacteristic(txCtlChar!!, lengthBytes)

        // 2. Daten in Chunks senden
        requestBytes.asSequence()
            .chunked(ShellyBleConstants.CHUNK_SIZE)
            .forEach { chunk ->
                writeCharacteristic(dataChar!!, chunk.toByteArray())
            }

        // 3. Kurz warten, dann Response-Laenge lesen
        delay(100)
        val rxLength = readCharacteristic(rxCtlChar!!)
        val responseLength = ByteBuffer.wrap(rxLength).int

        Log.d(TAG, "Response-Laenge: $responseLength Bytes")

        if (responseLength == 0) return@withLock null

        // 4. Response-Daten lesen
        val responseBuffer = ByteArrayOutputStream()
        while (responseBuffer.size() < responseLength) {
            val chunk = readCharacteristic(dataChar!!)
            responseBuffer.write(chunk)
        }

        val responseJson = responseBuffer.toString(Charsets.UTF_8.name())
        val response = gson.fromJson(responseJson, JsonObject::class.java)
        Log.d(TAG, "RX: $responseJson")

        // Auth-Fehler behandeln (401)
        if (response.has("error")) {
            val error = response.getAsJsonObject("error")
            if (error.get("code")?.asInt == 401 && !password.isNullOrEmpty()) {
                val authInfo = gson.fromJson(error.get("message").asString, JsonObject::class.java)
                authRealm = authInfo.get("realm")?.asString
                authNonce = authInfo.get("nonce")?.asLong
                Log.d(TAG, "Auth erforderlich, wiederhole mit Credentials...")
                return@withLock sendRpcWithAuth(method, params, password)
            }
        }

        response
    }

    private suspend fun sendRpcWithAuth(
        method: String,
        params: Map<String, Any>,
        password: String
    ): JsonObject? {
        // Erneut senden mit Auth - Mutex ist bereits gehalten
        val request = JsonObject().apply {
            addProperty("id", System.currentTimeMillis())
            addProperty("src", "garage_app")
            addProperty("method", method)
            add("params", gson.toJsonTree(params))
            val authMap = ShellyAuth.calculateResponse(password, authRealm!!, authNonce!!)
            add("auth", gson.toJsonTree(authMap))
        }

        val requestBytes = request.toString().toByteArray(Charsets.UTF_8)
        val lengthBytes = ByteBuffer.allocate(4).putInt(requestBytes.size).array()
        writeCharacteristic(txCtlChar!!, lengthBytes)

        requestBytes.asSequence()
            .chunked(ShellyBleConstants.CHUNK_SIZE)
            .forEach { chunk ->
                writeCharacteristic(dataChar!!, chunk.toByteArray())
            }

        delay(100)
        val rxLength = readCharacteristic(rxCtlChar!!)
        val responseLength = ByteBuffer.wrap(rxLength).int

        if (responseLength == 0) return null

        val responseBuffer = ByteArrayOutputStream()
        while (responseBuffer.size() < responseLength) {
            val chunk = readCharacteristic(dataChar!!)
            responseBuffer.write(chunk)
        }

        val responseJson = responseBuffer.toString(Charsets.UTF_8.name())
        return gson.fromJson(responseJson, JsonObject::class.java)
    }

    private suspend fun writeCharacteristic(
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray
    ) {
        onWriteComplete = CompletableDeferred()
        @Suppress("DEPRECATION")
        characteristic.value = value
        @Suppress("DEPRECATION")
        bluetoothGatt?.writeCharacteristic(characteristic)
        withTimeout(5000) { onWriteComplete!!.await() }
    }

    private suspend fun readCharacteristic(
        characteristic: BluetoothGattCharacteristic
    ): ByteArray {
        onReadComplete = CompletableDeferred()
        @Suppress("DEPRECATION")
        bluetoothGatt?.readCharacteristic(characteristic)
        return withTimeout(5000) { onReadComplete!!.await() }
    }

    fun disconnect() {
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        cleanup()
    }

    private fun cleanup() {
        bluetoothGatt = null
        dataChar = null
        txCtlChar = null
        rxCtlChar = null
    }

    fun Sequence<Byte>.chunked(size: Int): Sequence<List<Byte>> = sequence {
        val buffer = mutableListOf<Byte>()
        for (element in this@chunked) {
            buffer.add(element)
            if (buffer.size == size) {
                yield(buffer.toList())
                buffer.clear()
            }
        }
        if (buffer.isNotEmpty()) yield(buffer.toList())
    }
}
