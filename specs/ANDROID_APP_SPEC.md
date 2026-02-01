# Android App Spezifikation - Shelly BLE Garage Opener

## Übersicht

Native Android-App zur Steuerung von Shelly-Geräten via Bluetooth Low Energy. 
Vorteile gegenüber PWA:
- **Direktverbindung via MAC** - Kein Geräte-Picker nötig
- **Hintergrund-Verbindung** - Kann offen gehalten werden
- **Schnellerer Verbindungsaufbau**
- **Widget-Support** möglich

---

## Technische Anforderungen

### Minimum SDK
- **minSdk:** 23 (Android 6.0) - BLE GATT Support
- **targetSdk:** 34 (Android 14)
- **Empfohlen:** 26+ (Android 8.0) für bessere BLE-Stabilität

### Permissions

```xml
<!-- AndroidManifest.xml -->
<uses-permission android:name="android.permission.BLUETOOTH" />
<uses-permission android:name="android.permission.BLUETOOTH_ADMIN" />
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
<uses-permission android:name="android.permission.BLUETOOTH_SCAN" 
    android:usesPermissionFlags="neverForLocation" />

<!-- Für Android 11 und älter -->
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" 
    android:maxSdkVersion="30" />

<uses-feature android:name="android.hardware.bluetooth_le" android:required="true" />
```

### Dependencies (build.gradle)

```kotlin
dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("com.google.code.gson:gson:2.10.1")
    
    // QR Code scanning
    implementation("com.google.mlkit:barcode-scanning:17.2.0")
    
    // QR Code generation
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
}
```

---

## Architektur

### Package-Struktur

```
de.beat2er.garage/
├── MainActivity.kt              # Hauptbildschirm mit Geräteliste
├── data/
│   ├── Device.kt                # Datenmodell
│   ├── DeviceRepository.kt      # SharedPreferences Storage
│   └── Config.kt                # Import/Export Konfiguration
├── ble/
│   ├── ShellyBleManager.kt      # BLE Verbindungsmanagement
│   ├── ShellyGattCallback.kt    # GATT Callbacks
│   ├── RpcClient.kt             # JSON-RPC Kommunikation
│   └── ShellyAuth.kt            # SHA-256 Authentication
├── ui/
│   ├── DeviceAdapter.kt         # RecyclerView Adapter
│   ├── AddDeviceDialog.kt       # Gerät hinzufügen
│   ├── EditDeviceDialog.kt      # Gerät bearbeiten
│   ├── QrScanActivity.kt        # QR Scanner
│   └── QrShareDialog.kt         # QR Code anzeigen
└── util/
    └── Extensions.kt            # Helper Functions
```

---

## Datenmodell

### Device.kt

```kotlin
data class Device(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val mac: String,  // Format: "CC:DB:A7:CF:EB:00"
    val password: String = "",
    val switchId: Int = 0
) {
    // MAC-Suffix für BLE Name-Matching (uppercase, ohne Doppelpunkte)
    val macSuffix: String
        get() = mac.replace(":", "").uppercase()
}
```

### DeviceRepository.kt

```kotlin
class DeviceRepository(context: Context) {
    private val prefs = context.getSharedPreferences("garage_devices", Context.MODE_PRIVATE)
    private val gson = Gson()
    
    fun getDevices(): List<Device> {
        val json = prefs.getString("devices", "[]")
        return gson.fromJson(json, Array<Device>::class.java).toList()
    }
    
    fun saveDevices(devices: List<Device>) {
        prefs.edit().putString("devices", gson.toJson(devices)).apply()
    }
    
    fun addDevice(device: Device) {
        val devices = getDevices().toMutableList()
        devices.add(device)
        saveDevices(devices)
    }
    
    fun removeDevice(id: String) {
        val devices = getDevices().filter { it.id != id }
        saveDevices(devices)
    }
}
```

---

## BLE Implementation

### Konstanten

```kotlin
object ShellyBleConstants {
    val SERVICE_UUID: UUID = UUID.fromString("5f6d4f53-5f52-5043-5f53-56435f49445f")
    val RPC_DATA_UUID: UUID = UUID.fromString("5f6d4f53-5f52-5043-5f64-6174615f5f5f")
    val TX_CTL_UUID: UUID = UUID.fromString("5f6d4f53-5f52-5043-5f74-785f63746c5f")
    val RX_CTL_UUID: UUID = UUID.fromString("5f6d4f53-5f52-5043-5f72-785f63746c5f")
    
    const val CHUNK_SIZE = 512
}
```

### ShellyBleManager.kt (Kern-Logik)

```kotlin
class ShellyBleManager(private val context: Context) {
    private val bluetoothAdapter: BluetoothAdapter? = 
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    
    private var bluetoothGatt: BluetoothGatt? = null
    private var dataChar: BluetoothGattCharacteristic? = null
    private var txCtlChar: BluetoothGattCharacteristic? = null
    private var rxCtlChar: BluetoothGattCharacteristic? = null
    
    private var authRealm: String? = null
    private var authNonce: Long? = null
    
    // Verbindung via MAC-Adresse (KEIN PICKER!)
    @SuppressLint("MissingPermission")
    fun connect(macAddress: String, callback: ConnectionCallback) {
        val device = bluetoothAdapter?.getRemoteDevice(macAddress)
        if (device == null) {
            callback.onError("Gerät nicht gefunden")
            return
        }
        
        bluetoothGatt = device.connectGatt(
            context,
            false,  // autoConnect
            object : BluetoothGattCallback() {
                override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                    when (newState) {
                        BluetoothProfile.STATE_CONNECTED -> {
                            gatt.discoverServices()
                        }
                        BluetoothProfile.STATE_DISCONNECTED -> {
                            callback.onDisconnected()
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
                            callback.onConnected()
                        } else {
                            callback.onError("Shelly Service nicht gefunden")
                        }
                    }
                }
                
                // ... weitere Callbacks für Read/Write
            },
            BluetoothDevice.TRANSPORT_LE
        )
    }
    
    // Direktverbindung via Gerätenamen (Scan + Filter)
    @SuppressLint("MissingPermission")
    fun connectByName(macSuffix: String, callback: ConnectionCallback) {
        val scanner = bluetoothAdapter?.bluetoothLeScanner
        val filter = ScanFilter.Builder()
            .setDeviceName(null)  // Wir filtern manuell
            .build()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        
        val scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val name = result.device.name ?: return
                if (name.uppercase().contains(macSuffix)) {
                    scanner?.stopScan(this)
                    connect(result.device.address, callback)
                }
            }
        }
        
        scanner?.startScan(listOf(filter), settings, scanCallback)
        
        // Timeout nach 10 Sekunden
        Handler(Looper.getMainLooper()).postDelayed({
            scanner?.stopScan(scanCallback)
            callback.onError("Gerät nicht gefunden")
        }, 10000)
    }
    
    // RPC Befehl senden
    suspend fun sendRpc(method: String, params: Map<String, Any>, password: String? = null): RpcResponse {
        val request = buildRpcRequest(method, params, password)
        val requestBytes = request.toByteArray(Charsets.UTF_8)
        
        // 1. Länge senden (4 Bytes Big-Endian)
        val lengthBytes = ByteBuffer.allocate(4).putInt(requestBytes.size).array()
        writeCharacteristic(txCtlChar!!, lengthBytes)
        
        // 2. Daten in Chunks senden
        requestBytes.asSequence()
            .chunked(ShellyBleConstants.CHUNK_SIZE)
            .forEach { chunk ->
                writeCharacteristic(dataChar!!, chunk.toByteArray())
            }
        
        // 3. Response-Länge lesen
        val rxLength = readCharacteristic(rxCtlChar!!)
        val responseLength = ByteBuffer.wrap(rxLength).int
        
        if (responseLength == 0) {
            return RpcResponse.Empty
        }
        
        // 4. Response-Daten lesen
        val responseBytes = ByteArrayOutputStream()
        while (responseBytes.size() < responseLength) {
            val chunk = readCharacteristic(dataChar!!)
            responseBytes.write(chunk)
        }
        
        val responseJson = responseBytes.toString(Charsets.UTF_8.name())
        return parseRpcResponse(responseJson, method, params, password)
    }
    
    // Switch auslösen
    suspend fun triggerSwitch(switchId: Int = 0, password: String? = null): Boolean {
        val response = sendRpc("Switch.Set", mapOf("id" to switchId, "on" to true), password)
        return response is RpcResponse.Success
    }
    
    fun disconnect() {
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null
    }
    
    interface ConnectionCallback {
        fun onConnected()
        fun onDisconnected()
        fun onError(message: String)
    }
}
```

### ShellyAuth.kt

```kotlin
object ShellyAuth {
    fun calculateResponse(
        password: String,
        realm: String,
        nonce: Long,
        cnonce: Long = System.currentTimeMillis() / 1000,
        nc: Int = 1
    ): Map<String, Any> {
        val ha1 = sha256("admin:$realm:$password")
        val ha2 = sha256("dummy_method:dummy_uri")
        val response = sha256("$ha1:$nonce:$nc:$cnonce:auth:$ha2")
        
        return mapOf(
            "realm" to realm,
            "username" to "admin",
            "nonce" to nonce,
            "cnonce" to cnonce,
            "response" to response,
            "nc" to nc,
            "algorithm" to "SHA-256"
        )
    }
    
    private fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(input.toByteArray(Charsets.UTF_8))
        return hash.joinToString("") { "%02x".format(it) }
    }
}
```

---

## UI Design

### MainActivity Layout

```xml
<?xml version="1.0" encoding="utf-8"?>
<androidx.coordinatorlayout.widget.CoordinatorLayout 
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="#0d0d14">

    <com.google.android.material.appbar.AppBarLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content">

        <com.google.android.material.appbar.MaterialToolbar
            android:id="@+id/toolbar"
            android:layout_width="match_parent"
            android:layout_height="?attr/actionBarSize"
            android:background="#1a1a2e"
            app:title="🚗 Garage"
            app:titleTextColor="#eaeaea"
            app:menu="@menu/main_menu" />

    </com.google.android.material.appbar.AppBarLayout>

    <androidx.recyclerview.widget.RecyclerView
        android:id="@+id/deviceList"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:padding="16dp"
        android:clipToPadding="false"
        app:layout_behavior="@string/appbar_scrolling_view_behavior" />

    <TextView
        android:id="@+id/emptyState"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:gravity="center"
        android:text="Noch keine Geräte\n\nTippe + um ein Garagentor hinzuzufügen"
        android:textColor="#6b6b7b"
        android:textSize="16sp"
        android:visibility="gone" />

    <com.google.android.material.floatingactionbutton.FloatingActionButton
        android:id="@+id/fabAdd"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_gravity="bottom|end"
        android:layout_margin="16dp"
        android:backgroundTint="#e94560"
        android:src="@drawable/ic_add"
        app:tint="@android:color/white" />

</androidx.coordinatorlayout.widget.CoordinatorLayout>
```

### Device Card Layout

```xml
<?xml version="1.0" encoding="utf-8"?>
<com.google.android.material.card.MaterialCardView
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_marginBottom="12dp"
    app:cardBackgroundColor="#1a1a2e"
    app:cardCornerRadius="16dp"
    app:cardElevation="0dp"
    app:strokeWidth="2dp"
    app:strokeColor="@color/device_card_stroke">

    <androidx.constraintlayout.widget.ConstraintLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:padding="16dp">

        <!-- Garage Icon -->
        <ImageView
            android:id="@+id/icon"
            android:layout_width="56dp"
            android:layout_height="56dp"
            android:background="@drawable/icon_background"
            android:padding="14dp"
            android:src="@drawable/ic_garage"
            app:layout_constraintStart_toStartOf="parent"
            app:layout_constraintTop_toTopOf="parent"
            app:tint="#6b6b7b" />

        <!-- Device Name -->
        <TextView
            android:id="@+id/deviceName"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_marginStart="16dp"
            android:ellipsize="end"
            android:maxLines="1"
            android:textColor="#eaeaea"
            android:textSize="16sp"
            android:textStyle="bold"
            app:layout_constraintEnd_toStartOf="@id/btnTrigger"
            app:layout_constraintStart_toEndOf="@id/icon"
            app:layout_constraintTop_toTopOf="@id/icon" />

        <!-- MAC Address -->
        <TextView
            android:id="@+id/deviceMac"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_marginStart="16dp"
            android:fontFamily="monospace"
            android:textColor="#6b6b7b"
            android:textSize="12sp"
            app:layout_constraintEnd_toStartOf="@id/btnTrigger"
            app:layout_constraintStart_toEndOf="@id/icon"
            app:layout_constraintTop_toBottomOf="@id/deviceName" />

        <!-- Status -->
        <TextView
            android:id="@+id/deviceStatus"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginStart="16dp"
            android:layout_marginTop="4dp"
            android:text="Nicht verbunden"
            android:textColor="#6b6b7b"
            android:textSize="11sp"
            app:layout_constraintStart_toEndOf="@id/icon"
            app:layout_constraintTop_toBottomOf="@id/deviceMac" />

        <!-- Trigger Button -->
        <com.google.android.material.button.MaterialButton
            android:id="@+id/btnTrigger"
            android:layout_width="56dp"
            android:layout_height="56dp"
            android:insetTop="0dp"
            android:insetBottom="0dp"
            app:backgroundTint="#e94560"
            app:cornerRadius="12dp"
            app:icon="@drawable/ic_bolt"
            app:iconGravity="textStart"
            app:iconPadding="0dp"
            app:iconTint="@android:color/white"
            app:layout_constraintBottom_toBottomOf="@id/icon"
            app:layout_constraintEnd_toEndOf="parent"
            app:layout_constraintTop_toTopOf="@id/icon" />

    </androidx.constraintlayout.widget.ConstraintLayout>

</com.google.android.material.card.MaterialCardView>
```

---

## Features

### Kernfunktionen

1. **Geräteliste**
   - Alle konfigurierten Geräte anzeigen
   - Status (verbunden/nicht verbunden)
   - Trigger-Button pro Gerät

2. **Gerät hinzufügen**
   - Name eingeben
   - MAC-Adresse eingeben (manuell oder via Scan)
   - Optional: Passwort

3. **Gerät bearbeiten**
   - Name ändern
   - Passwort ändern
   - Gerät löschen

4. **QR-Code Import/Export**
   - Kompatibel mit PWA-Format
   - URL mit Base64-encoded Config

### Erweiterte Features (optional)

1. **Home Screen Widget**
   - 1-Tap Trigger ohne App öffnen
   
2. **Benachrichtigungen**
   - Persistent Notification für schnellen Zugriff
   
3. **Tasker/Automate Integration**
   - Intent-basierte Steuerung

---

## Home Screen Widgets

### Widget-Typen

| Widget | Größe | Funktion |
|--------|-------|----------|
| **Single Device** | 1x1 | Ein Gerät triggern |
| **Single Device Large** | 2x2 | Mit Status-Anzeige |
| **Multi Device** | 4x2 | Liste aller Geräte |

### Widget Provider

#### widget_info.xml (res/xml/)

```xml
<!-- Single Device Widget 1x1 -->
<appwidget-provider xmlns:android="http://schemas.android.com/apk/res/android"
    android:minWidth="40dp"
    android:minHeight="40dp"
    android:targetCellWidth="1"
    android:targetCellHeight="1"
    android:updatePeriodMillis="0"
    android:initialLayout="@layout/widget_single"
    android:configure="de.beat2er.garage.widget.WidgetConfigActivity"
    android:widgetCategory="home_screen"
    android:resizeMode="none"
    android:widgetFeatures="reconfigurable"
    android:previewImage="@drawable/widget_preview_single"
    android:description="@string/widget_single_description" />

<!-- Single Device Widget 2x2 with status -->
<appwidget-provider xmlns:android="http://schemas.android.com/apk/res/android"
    android:minWidth="110dp"
    android:minHeight="110dp"
    android:targetCellWidth="2"
    android:targetCellHeight="2"
    android:updatePeriodMillis="1800000"
    android:initialLayout="@layout/widget_single_large"
    android:configure="de.beat2er.garage.widget.WidgetConfigActivity"
    android:widgetCategory="home_screen"
    android:resizeMode="horizontal|vertical"
    android:previewImage="@drawable/widget_preview_large" />

<!-- Multi Device Widget 4x2 -->
<appwidget-provider xmlns:android="http://schemas.android.com/apk/res/android"
    android:minWidth="250dp"
    android:minHeight="110dp"
    android:targetCellWidth="4"
    android:targetCellHeight="2"
    android:updatePeriodMillis="1800000"
    android:initialLayout="@layout/widget_multi"
    android:widgetCategory="home_screen"
    android:resizeMode="horizontal|vertical"
    android:previewImage="@drawable/widget_preview_multi" />
```

### Widget Layouts

#### widget_single.xml (1x1 Quick Trigger)

```xml
<?xml version="1.0" encoding="utf-8"?>
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:padding="4dp">

    <ImageButton
        android:id="@+id/btnTrigger"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:background="@drawable/widget_button_background"
        android:src="@drawable/ic_garage"
        android:scaleType="centerInside"
        android:padding="12dp"
        android:contentDescription="@string/trigger_garage" />

    <!-- Status indicator dot -->
    <View
        android:id="@+id/statusDot"
        android:layout_width="8dp"
        android:layout_height="8dp"
        android:layout_gravity="top|end"
        android:layout_margin="8dp"
        android:background="@drawable/status_dot_disconnected" />

</FrameLayout>
```

#### widget_single_large.xml (2x2 with Status)

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:padding="8dp"
    android:background="@drawable/widget_background"
    android:gravity="center">

    <!-- Device Icon -->
    <ImageView
        android:id="@+id/imgIcon"
        android:layout_width="48dp"
        android:layout_height="48dp"
        android:src="@drawable/ic_garage"
        android:tint="#e94560" />

    <!-- Device Name -->
    <TextView
        android:id="@+id/txtName"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="8dp"
        android:gravity="center"
        android:textColor="#eaeaea"
        android:textSize="14sp"
        android:textStyle="bold"
        android:maxLines="1"
        android:ellipsize="end"
        android:text="Garage" />

    <!-- Status -->
    <TextView
        android:id="@+id/txtStatus"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:gravity="center"
        android:textColor="#6b6b7b"
        android:textSize="11sp"
        android:text="Tippen zum Auslösen" />

    <!-- Trigger Button -->
    <Button
        android:id="@+id/btnTrigger"
        android:layout_width="match_parent"
        android:layout_height="40dp"
        android:layout_marginTop="8dp"
        android:background="@drawable/widget_button_accent"
        android:text="⚡ Auslösen"
        android:textColor="@android:color/white"
        android:textSize="12sp" />

</LinearLayout>
```

#### widget_multi.xml (4x2 Multi Device)

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:padding="8dp"
    android:background="@drawable/widget_background">

    <!-- Header -->
    <TextView
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:text="🚗 Garage"
        android:textColor="#eaeaea"
        android:textSize="14sp"
        android:textStyle="bold"
        android:paddingBottom="8dp" />

    <!-- Device Buttons Container -->
    <LinearLayout
        android:id="@+id/deviceContainer"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:orientation="horizontal"
        android:gravity="center">

        <!-- Dynamically filled with device buttons -->
        <!-- Each button: 64dp x match_parent -->

    </LinearLayout>

</LinearLayout>
```

#### widget_device_button.xml (für Multi-Widget)

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="0dp"
    android:layout_height="match_parent"
    android:layout_weight="1"
    android:layout_margin="4dp"
    android:orientation="vertical"
    android:gravity="center"
    android:background="@drawable/widget_device_button"
    android:padding="8dp">

    <ImageView
        android:id="@+id/imgIcon"
        android:layout_width="32dp"
        android:layout_height="32dp"
        android:src="@drawable/ic_garage"
        android:tint="#e94560" />

    <TextView
        android:id="@+id/txtName"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="4dp"
        android:gravity="center"
        android:textColor="#eaeaea"
        android:textSize="10sp"
        android:maxLines="1"
        android:ellipsize="end" />

</LinearLayout>
```

### Drawables

#### widget_background.xml

```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <solid android:color="#CC1a1a2e" />
    <corners android:radius="16dp" />
</shape>
```

#### widget_button_background.xml

```xml
<?xml version="1.0" encoding="utf-8"?>
<selector xmlns:android="http://schemas.android.com/apk/res/android">
    <item android:state_pressed="true">
        <shape android:shape="rectangle">
            <solid android:color="#d63d56" />
            <corners android:radius="16dp" />
        </shape>
    </item>
    <item>
        <shape android:shape="rectangle">
            <solid android:color="#e94560" />
            <corners android:radius="16dp" />
        </shape>
    </item>
</selector>
```

#### widget_button_accent.xml

```xml
<?xml version="1.0" encoding="utf-8"?>
<selector xmlns:android="http://schemas.android.com/apk/res/android">
    <item android:state_pressed="true">
        <shape android:shape="rectangle">
            <solid android:color="#d63d56" />
            <corners android:radius="8dp" />
        </shape>
    </item>
    <item>
        <shape android:shape="rectangle">
            <solid android:color="#e94560" />
            <corners android:radius="8dp" />
        </shape>
    </item>
</selector>
```

#### status_dot_connected.xml

```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="oval">
    <solid android:color="#4ade80" />
</shape>
```

#### status_dot_disconnected.xml

```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="oval">
    <solid android:color="#6b6b7b" />
</shape>
```

### Widget Provider Implementation

#### GarageWidgetProvider.kt

```kotlin
class GarageWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        
        when (intent.action) {
            ACTION_TRIGGER -> {
                val deviceMac = intent.getStringExtra(EXTRA_DEVICE_MAC) ?: return
                val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
                
                // Trigger in background
                CoroutineScope(Dispatchers.IO).launch {
                    triggerDevice(context, deviceMac, widgetId)
                }
            }
            ACTION_UPDATE_STATUS -> {
                val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
                if (widgetId != -1) {
                    val appWidgetManager = AppWidgetManager.getInstance(context)
                    updateWidget(context, appWidgetManager, widgetId)
                }
            }
        }
    }

    private fun updateWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        val prefs = context.getSharedPreferences("widget_$appWidgetId", Context.MODE_PRIVATE)
        val deviceMac = prefs.getString("device_mac", null) ?: return
        val deviceName = prefs.getString("device_name", "Garage") ?: "Garage"

        val views = RemoteViews(context.packageName, R.layout.widget_single_large)
        
        // Set device name
        views.setTextViewText(R.id.txtName, deviceName)
        
        // Set click action
        val triggerIntent = Intent(context, GarageWidgetProvider::class.java).apply {
            action = ACTION_TRIGGER
            putExtra(EXTRA_DEVICE_MAC, deviceMac)
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }
        val triggerPendingIntent = PendingIntent.getBroadcast(
            context,
            appWidgetId,
            triggerIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.btnTrigger, triggerPendingIntent)
        
        // Also make whole widget clickable
        views.setOnClickPendingIntent(R.id.imgIcon, triggerPendingIntent)

        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    private suspend fun triggerDevice(context: Context, mac: String, widgetId: Int) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val views = RemoteViews(context.packageName, R.layout.widget_single_large)
        
        // Show "Verbinde..." status
        views.setTextViewText(R.id.txtStatus, "Verbinde...")
        appWidgetManager.partiallyUpdateAppWidget(widgetId, views)
        
        try {
            val bleManager = ShellyBleManager(context)
            val prefs = context.getSharedPreferences("widget_$widgetId", Context.MODE_PRIVATE)
            val password = prefs.getString("device_password", null)
            
            // Connect and trigger
            val result = withTimeout(15000) {
                suspendCancellableCoroutine<Boolean> { continuation ->
                    bleManager.connect(mac, object : ShellyBleManager.ConnectionCallback {
                        override fun onConnected() {
                            CoroutineScope(Dispatchers.IO).launch {
                                try {
                                    val success = bleManager.triggerSwitch(0, password)
                                    bleManager.disconnect()
                                    continuation.resume(success) {}
                                } catch (e: Exception) {
                                    continuation.resume(false) {}
                                }
                            }
                        }
                        
                        override fun onDisconnected() {}
                        
                        override fun onError(message: String) {
                            continuation.resume(false) {}
                        }
                    })
                }
            }
            
            // Update status
            if (result) {
                views.setTextViewText(R.id.txtStatus, "✓ Ausgelöst!")
                views.setInt(R.id.txtStatus, "setTextColor", Color.parseColor("#4ade80"))
            } else {
                views.setTextViewText(R.id.txtStatus, "✗ Fehler")
                views.setInt(R.id.txtStatus, "setTextColor", Color.parseColor("#e94560"))
            }
            
        } catch (e: TimeoutCancellationException) {
            views.setTextViewText(R.id.txtStatus, "✗ Timeout")
            views.setInt(R.id.txtStatus, "setTextColor", Color.parseColor("#e94560"))
        } catch (e: Exception) {
            views.setTextViewText(R.id.txtStatus, "✗ ${e.message}")
            views.setInt(R.id.txtStatus, "setTextColor", Color.parseColor("#e94560"))
        }
        
        appWidgetManager.partiallyUpdateAppWidget(widgetId, views)
        
        // Reset status after 3 seconds
        delay(3000)
        views.setTextViewText(R.id.txtStatus, "Tippen zum Auslösen")
        views.setInt(R.id.txtStatus, "setTextColor", Color.parseColor("#6b6b7b"))
        appWidgetManager.partiallyUpdateAppWidget(widgetId, views)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        // Clean up preferences
        for (appWidgetId in appWidgetIds) {
            context.getSharedPreferences("widget_$appWidgetId", Context.MODE_PRIVATE)
                .edit().clear().apply()
        }
    }

    companion object {
        const val ACTION_TRIGGER = "de.beat2er.garage.ACTION_TRIGGER"
        const val ACTION_UPDATE_STATUS = "de.beat2er.garage.ACTION_UPDATE_STATUS"
        const val EXTRA_DEVICE_MAC = "device_mac"
    }
}
```

### Widget Configuration Activity

#### WidgetConfigActivity.kt

```kotlin
class WidgetConfigActivity : AppCompatActivity() {
    
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private lateinit var deviceAdapter: WidgetDeviceAdapter
    private lateinit var repository: DeviceRepository
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_widget_config)
        
        // Set result to CANCELED in case user backs out
        setResult(RESULT_CANCELED)
        
        // Get widget ID
        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        
        repository = DeviceRepository(this)
        
        // Setup RecyclerView
        val recyclerView = findViewById<RecyclerView>(R.id.deviceList)
        deviceAdapter = WidgetDeviceAdapter { device ->
            selectDevice(device)
        }
        recyclerView.adapter = deviceAdapter
        recyclerView.layoutManager = LinearLayoutManager(this)
        
        // Load devices
        deviceAdapter.submitList(repository.getDevices())
        
        // Empty state
        val emptyState = findViewById<TextView>(R.id.emptyState)
        emptyState.visibility = if (repository.getDevices().isEmpty()) View.VISIBLE else View.GONE
    }
    
    private fun selectDevice(device: Device) {
        // Save widget config
        getSharedPreferences("widget_$appWidgetId", Context.MODE_PRIVATE)
            .edit()
            .putString("device_mac", device.mac)
            .putString("device_name", device.name)
            .putString("device_password", device.password)
            .apply()
        
        // Update widget
        val appWidgetManager = AppWidgetManager.getInstance(this)
        GarageWidgetProvider().onUpdate(this, appWidgetManager, intArrayOf(appWidgetId))
        
        // Return success
        val resultValue = Intent().apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }
        setResult(RESULT_OK, resultValue)
        finish()
    }
}
```

#### activity_widget_config.xml

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:background="#0d0d14">

    <TextView
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:padding="20dp"
        android:text="Gerät für Widget auswählen"
        android:textColor="#eaeaea"
        android:textSize="18sp"
        android:textStyle="bold" />

    <androidx.recyclerview.widget.RecyclerView
        android:id="@+id/deviceList"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:padding="16dp"
        android:clipToPadding="false" />

    <TextView
        android:id="@+id/emptyState"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:gravity="center"
        android:text="Keine Geräte konfiguriert.\n\nÖffne die App um Geräte hinzuzufügen."
        android:textColor="#6b6b7b"
        android:textSize="16sp"
        android:visibility="gone" />

</LinearLayout>
```

### AndroidManifest.xml Widget Registrierung

```xml
<manifest ...>
    <application ...>
        
        <!-- Single Widget Provider -->
        <receiver
            android:name=".widget.GarageWidgetProvider"
            android:exported="true"
            android:label="Garage (Klein)">
            <intent-filter>
                <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />
                <action android:name="de.beat2er.garage.ACTION_TRIGGER" />
                <action android:name="de.beat2er.garage.ACTION_UPDATE_STATUS" />
            </intent-filter>
            <meta-data
                android:name="android.appwidget.provider"
                android:resource="@xml/widget_single_info" />
        </receiver>

        <!-- Large Widget Provider -->
        <receiver
            android:name=".widget.GarageWidgetLargeProvider"
            android:exported="true"
            android:label="Garage (Groß)">
            <intent-filter>
                <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />
                <action android:name="de.beat2er.garage.ACTION_TRIGGER" />
            </intent-filter>
            <meta-data
                android:name="android.appwidget.provider"
                android:resource="@xml/widget_large_info" />
        </receiver>

        <!-- Multi Widget Provider -->
        <receiver
            android:name=".widget.GarageWidgetMultiProvider"
            android:exported="true"
            android:label="Garage (Multi)">
            <intent-filter>
                <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />
                <action android:name="de.beat2er.garage.ACTION_TRIGGER" />
            </intent-filter>
            <meta-data
                android:name="android.appwidget.provider"
                android:resource="@xml/widget_multi_info" />
        </receiver>

        <!-- Widget Configuration Activity -->
        <activity
            android:name=".widget.WidgetConfigActivity"
            android:exported="true"
            android:theme="@style/Theme.Garage.Dialog">
            <intent-filter>
                <action android:name="android.appwidget.action.APPWIDGET_CONFIGURE" />
            </intent-filter>
        </activity>
        
    </application>
</manifest>
```

### Widget User Flow

```
1. USER LONG-PRESS HOME SCREEN
              ↓
2. SELECT "Garage" WIDGET
   ┌─────────────────────────────┐
   │ 🚗 Garage (Klein)    1x1   │
   │ 🚗 Garage (Groß)     2x2   │
   │ 🚗 Garage (Multi)    4x2   │
   └─────────────────────────────┘
              ↓
3. CONFIGURATION ACTIVITY OPENS
   ┌─────────────────────────────┐
   │ Gerät für Widget auswählen  │
   │                             │
   │ ┌─────────────────────────┐ │
   │ │ 🏠 Hauptgarage          │ │
   │ │    CC:DB:A7:CF:EB:00    │ │
   │ └─────────────────────────┘ │
   │ ┌─────────────────────────┐ │
   │ │ 🏠 Garage Hinten        │ │
   │ │    44:17:93:CD:2E:20    │ │
   │ └─────────────────────────┘ │
   └─────────────────────────────┘
              ↓
4. USER TAPS DEVICE → WIDGET PLACED
              ↓
5. USER TAPS WIDGET → GARAGE TRIGGERED!
   ┌──────┐
   │  🏠  │ → BLE Connect → Switch.Set → Done!
   │ ━━━━ │
   └──────┘
```

---

## Verbindungslogik

### Ablauf bei Trigger

```
1. User tippt Trigger-Button
        ↓
2. Check: Gerät bereits verbunden?
   ├── JA → Direkt zu Schritt 5
   └── NEIN → Weiter
        ↓
3. Versuche Direktverbindung via MAC
   bluetoothAdapter.getRemoteDevice(mac).connectGatt(...)
        ↓
4. Falls fehlgeschlagen: Scan nach Gerätenamen
   Suche "Shelly*{MAC_SUFFIX}" in BLE Advertisements
        ↓
5. Sende "Switch.Set" RPC
        ↓
6. Bei 401: Auth hinzufügen und wiederholen
        ↓
7. UI Update: "Ausgelöst!" / Fehler anzeigen
```

### Verbindung halten

```kotlin
// Optional: Verbindung im Hintergrund halten
class GarageForegroundService : Service() {
    private val bleManager = ShellyBleManager(this)
    private val connectedDevices = mutableMapOf<String, BluetoothGatt>()
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Foreground Notification
        startForeground(1, createNotification())
        
        // Alle Geräte verbinden
        devices.forEach { device ->
            bleManager.connect(device.mac, ...)
        }
        
        return START_STICKY
    }
}
```

---

## Build & Release

### Debug APK bauen

```bash
./gradlew assembleDebug
# Output: app/build/outputs/apk/debug/app-debug.apk
```

### Release APK bauen

```bash
# Keystore erstellen (einmalig)
keytool -genkey -v -keystore garage-release.keystore \
  -alias garage -keyalg RSA -keysize 2048 -validity 10000

# Release bauen
./gradlew assembleRelease

# Output: app/build/outputs/apk/release/app-release.apk
```

### ProGuard Rules

```proguard
# Gson
-keepattributes Signature
-keep class de.beat2er.garage.data.** { *; }

# BLE
-keep class android.bluetooth.** { *; }
```

---

## Testfälle

1. **Gerät hinzufügen** - Name + MAC eingeben, speichern
2. **Verbindung** - Trigger tippen, Verbindung aufbauen
3. **Auslösen** - Switch.Set senden, Response prüfen
4. **Auth** - Mit Passwort-geschütztem Gerät testen
5. **QR Import** - PWA QR-Code scannen
6. **QR Export** - QR erstellen, mit PWA scannen
7. **Reconnect** - App schließen, öffnen, erneut triggern
8. **Offline** - Ohne Internet testen (BLE only)
