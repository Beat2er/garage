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

## Home Screen Widget (Jetpack Glance)

> **Hinweis:** Die Implementierung nutzt Jetpack Glance (1.1.1) statt RemoteViews.
> Glance bietet eine Compose-Style API, DataStore-basierten State pro Widget-Instanz
> und suspend-fähige ActionCallbacks.

### Widget-Typ

Ein resizable Single-Device Widget (1x1 Standard, resizable bis 4x4).
User platziert es, wählt ein Gerät, tippt zum Auslösen.

| Widget | Größe | Funktion |
|--------|-------|----------|
| **Garage Widget** | 1x1 (resizable) | Ein Gerät triggern mit Status-Anzeige |

### Architektur

```
widget/
├── WidgetKeys.kt           # DataStore Preference Keys + Status-Konstanten
├── GarageWidget.kt         # GlanceAppWidget UI + Auto-Config
├── GarageWidgetReceiver.kt # GlanceAppWidgetReceiver Bridge
├── TriggerAction.kt        # ActionCallback → startet ForegroundService
├── WidgetTriggerService.kt # ForegroundService für BLE-Operationen
└── WidgetConfigActivity.kt # Compose Activity für Gerätewahl
```

### Widget-Info (res/xml/garage_widget_info.xml)

```xml
<appwidget-provider xmlns:android="http://schemas.android.com/apk/res/android"
    android:minWidth="57dp"
    android:minHeight="57dp"
    android:minResizeWidth="57dp"
    android:minResizeHeight="57dp"
    android:targetCellWidth="1"
    android:targetCellHeight="1"
    android:resizeMode="horizontal|vertical"
    android:updatePeriodMillis="0"
    android:previewLayout="@layout/garage_widget_preview"
    android:configure="de.beat2er.garage.widget.WidgetConfigActivity"
    android:widgetCategory="home_screen"
    android:widgetFeatures="reconfigurable"
    android:description="@string/widget_description" />
```

### State Management (Glance DataStore)

Jede Widget-Instanz hat eigenen State via `PreferencesGlanceStateDefinition`:

```kotlin
object WidgetKeys {
    val DEVICE_ID = stringPreferencesKey("device_id")
    val DEVICE_NAME = stringPreferencesKey("device_name")
    val DEVICE_MAC = stringPreferencesKey("device_mac")
    val DEVICE_PASSWORD = stringPreferencesKey("device_password")
    val DEVICE_SWITCH_ID = stringPreferencesKey("device_switch_id")
    val APP_WIDGET_ID = stringPreferencesKey("app_widget_id")
    val STATUS = stringPreferencesKey("status")       // idle|connecting|triggered|error
    val STATUS_TEXT = stringPreferencesKey("status_text")
}
```

### BLE-Trigger via ForegroundService

**Problem:** `ActionCallback.onAction()` läuft intern in einem `BroadcastReceiver`.
BLE-Operationen (15-25s) dauern länger als das ~10s Zeitfenster. Der Prozess wird
deprioritisiert/gekillt bevor GATT-Callbacks feuern.

**Lösung:** Widget-Tap startet einen `ForegroundService` mit `connectedDevice`-Typ.
Widget-Interaktionen sind von Androids FG-Service-Startrestriktionen (Android 12+) ausgenommen.

```
Widget-Tap
  → TriggerAction.onAction()           [ActionCallback, ~ms]
    → Status → "Verbinde..."
    → context.startForegroundService()
      → WidgetTriggerService            [ForegroundService, bis 25s]
        → Notification anzeigen
        → ShellyBleManager.connect()    [Direktverbindung oder Scan-Fallback]
        → triggerSwitch()
        → Status → "Ausgelöst!" / Fehler
        → 3s warten → Status → "Tippen zum Auslösen"
        → stopSelf()
```

### Auto-Config (Pin aus App-Settings)

Zwei Pfade für zuverlässige Auto-Konfiguration:

1. **`GarageWidget.provideGlance()`** prüft beim Rendern ob `pending_widget_device`
   in SharedPreferences existiert und das Widget noch unkonfiguriert ist → auto-konfiguriert
2. **`WidgetConfigActivity`** prüft ob das Widget bereits konfiguriert ist → schließt
   sich sofort mit `RESULT_OK`

```
App-Settings → "Widget hinzufügen"
  → pending_widget_device in SharedPreferences speichern
  → requestPinAppWidget()
  → Launcher platziert Widget
  → provideGlance() → auto-konfiguriert aus SharedPreferences
  → Falls Config-Activity öffnet → erkennt bereits konfiguriert → RESULT_OK + finish()
```

### GarageWidgetReceiver.kt

```kotlin
class GarageWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = GarageWidget()
}
```

### GarageWidget.kt

```kotlin
class GarageWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // Auto-config: check for pending device from pin-from-settings
        tryAutoConfig(context, id)
        provideContent { GarageWidgetContent() }
    }

    private suspend fun tryAutoConfig(context: Context, glanceId: GlanceId) {
        val settingsPrefs = context.getSharedPreferences("garage_settings", Context.MODE_PRIVATE)
        val pendingDeviceId = settingsPrefs.getString("pending_widget_device", null) ?: return

        val state = getAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId)
        if (state[WidgetKeys.DEVICE_NAME] != null) return  // Already configured

        settingsPrefs.edit().remove("pending_widget_device").commit()
        val device = DeviceRepository(context).getDevices()
            .find { it.id == pendingDeviceId } ?: return

        // Resolve appWidgetId for this glanceId
        val manager = GlanceAppWidgetManager(context)
        val appWidgetIds = AppWidgetManager.getInstance(context)
            .getAppWidgetIds(ComponentName(context, GarageWidgetReceiver::class.java))
        var appWidgetId = -1
        for (wId in appWidgetIds) {
            if (manager.getGlanceIdBy(wId) == glanceId) { appWidgetId = wId; break }
        }

        updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
            prefs.toMutablePreferences().apply {
                this[WidgetKeys.DEVICE_ID] = device.id
                this[WidgetKeys.DEVICE_NAME] = device.name
                this[WidgetKeys.DEVICE_MAC] = device.mac
                this[WidgetKeys.DEVICE_PASSWORD] = device.password
                this[WidgetKeys.DEVICE_SWITCH_ID] = device.switchId.toString()
                if (appWidgetId != -1) this[WidgetKeys.APP_WIDGET_ID] = appWidgetId.toString()
                this[WidgetKeys.STATUS] = WidgetStatus.IDLE
                this[WidgetKeys.STATUS_TEXT] = "Tippen zum Auslösen"
            }
        }
    }

    @Composable
    private fun GarageWidgetContent() {
        val state = currentState<Preferences>()
        val deviceName = state[WidgetKeys.DEVICE_NAME]
        val status = state[WidgetKeys.STATUS] ?: WidgetStatus.IDLE
        val statusText = state[WidgetKeys.STATUS_TEXT] ?: "Tippen zum Auslösen"

        val statusColor = when (status) {
            WidgetStatus.CONNECTING -> ColorProvider(Warning)
            WidgetStatus.TRIGGERED -> ColorProvider(Success)
            WidgetStatus.ERROR -> ColorProvider(Accent)
            else -> ColorProvider(TextDim)
        }

        Box(
            modifier = GlanceModifier.fillMaxSize().cornerRadius(16.dp)
                .background(BgCard).clickable(actionRunCallback<TriggerAction>()).padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            if (deviceName != null) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = deviceName, style = TextStyle(
                        color = ColorProvider(TextPrimary), fontSize = 14.sp,
                        fontWeight = FontWeight.Bold), maxLines = 1)
                    Spacer(modifier = GlanceModifier.height(4.dp))
                    Text(text = statusText, style = TextStyle(
                        color = statusColor, fontSize = 11.sp), maxLines = 2)
                }
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "Kein Gerät", style = TextStyle(
                        color = ColorProvider(TextDim), fontSize = 14.sp))
                    Text(text = "Lange drücken\nzum Einrichten", style = TextStyle(
                        color = ColorProvider(TextDim), fontSize = 11.sp))
                }
            }
        }
    }
}
```

### TriggerAction.kt

```kotlin
class TriggerAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val prefs = getAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId)

        if (prefs[WidgetKeys.STATUS] == WidgetStatus.CONNECTING) return  // Debounce
        val deviceMac = prefs[WidgetKeys.DEVICE_MAC] ?: return
        val appWidgetId = prefs[WidgetKeys.APP_WIDGET_ID]?.toIntOrNull() ?: return

        // Visual feedback immediately
        updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { p ->
            p.toMutablePreferences().apply {
                this[WidgetKeys.STATUS] = WidgetStatus.CONNECTING
                this[WidgetKeys.STATUS_TEXT] = "Verbinde..."
            }
        }
        GarageWidget().update(context, glanceId)

        // Start ForegroundService for BLE (keeps process alive)
        val intent = Intent(context, WidgetTriggerService::class.java).apply {
            putExtra(WidgetTriggerService.EXTRA_APP_WIDGET_ID, appWidgetId)
            putExtra(WidgetTriggerService.EXTRA_DEVICE_NAME, prefs[WidgetKeys.DEVICE_NAME])
            putExtra(WidgetTriggerService.EXTRA_DEVICE_MAC, deviceMac)
            putExtra(WidgetTriggerService.EXTRA_DEVICE_PASSWORD, prefs[WidgetKeys.DEVICE_PASSWORD])
            putExtra(WidgetTriggerService.EXTRA_DEVICE_SWITCH_ID,
                prefs[WidgetKeys.DEVICE_SWITCH_ID]?.toIntOrNull() ?: 0)
        }
        context.startForegroundService(intent)
    }
}
```

### WidgetTriggerService.kt

```kotlin
@SuppressLint("MissingPermission")
class WidgetTriggerService : Service() {
    companion object {
        private const val CHANNEL_ID = "widget_trigger"
        private const val NOTIFICATION_ID = 1001
        private const val TRIGGER_TIMEOUT_MS = 25_000L
        private const val RESET_DELAY_MS = 3_000L
        const val EXTRA_APP_WIDGET_ID = "app_widget_id"
        const val EXTRA_DEVICE_NAME = "device_name"
        const val EXTRA_DEVICE_MAC = "device_mac"
        const val EXTRA_DEVICE_PASSWORD = "device_password"
        const val EXTRA_DEVICE_SWITCH_ID = "device_switch_id"
    }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val appWidgetId = intent?.getIntExtra(EXTRA_APP_WIDGET_ID, -1) ?: -1
        val deviceMac = intent?.getStringExtra(EXTRA_DEVICE_MAC) ?: ""
        val deviceName = intent?.getStringExtra(EXTRA_DEVICE_NAME) ?: "?"
        val password = intent?.getStringExtra(EXTRA_DEVICE_PASSWORD)
        val switchId = intent?.getIntExtra(EXTRA_DEVICE_SWITCH_ID, 0) ?: 0

        // Start foreground immediately (connectedDevice type for Android 10+)
        createChannel(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, buildNotification("Verbinde mit $deviceName..."),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        } else {
            startForeground(NOTIFICATION_ID, buildNotification("Verbinde mit $deviceName..."))
        }

        scope.launch {
            val glanceId = GlanceAppWidgetManager(this@WidgetTriggerService)
                .getGlanceIdBy(appWidgetId)
            var bleManager: ShellyBleManager? = null
            try {
                withTimeout(TRIGGER_TIMEOUT_MS) {
                    bleManager = ShellyBleManager(this@WidgetTriggerService)
                    // Direct MAC connection, fallback to BLE scan
                    try {
                        bleManager!!.connect(deviceMac)
                    } catch (e: Exception) {
                        updateStatus(glanceId, WidgetStatus.CONNECTING, "Scan...")
                        bleManager!!.disconnect()
                        bleManager = ShellyBleManager(this@WidgetTriggerService)
                        bleManager!!.connectByScan(deviceMac.replace(":", "").uppercase())
                    }
                    val success = bleManager!!.triggerSwitch(switchId, password?.ifEmpty { null })
                    if (success) updateStatus(glanceId, WidgetStatus.TRIGGERED, "Ausgelöst!")
                    else updateStatus(glanceId, WidgetStatus.ERROR, "Keine Antwort")
                }
            } catch (e: SecurityException) {
                updateStatus(glanceId, WidgetStatus.ERROR, "Berechtigung fehlt")
            } catch (e: Exception) {
                updateStatus(glanceId, WidgetStatus.ERROR,
                    e.message?.take(40) ?: e.javaClass.simpleName)
            } finally {
                try { bleManager?.disconnect() } catch (_: Exception) {}
            }
            delay(RESET_DELAY_MS)
            updateStatus(glanceId, WidgetStatus.IDLE, "Tippen zum Auslösen")
            stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    private suspend fun updateStatus(glanceId: GlanceId, status: String, text: String) {
        updateAppWidgetState(this, PreferencesGlanceStateDefinition, glanceId) { prefs ->
            prefs.toMutablePreferences().apply {
                this[WidgetKeys.STATUS] = status
                this[WidgetKeys.STATUS_TEXT] = text
            }
        }
        GarageWidget().update(this, glanceId)
    }

    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() { scope.cancel(); super.onDestroy() }
}
```

### WidgetConfigActivity.kt

```kotlin
class WidgetConfigActivity : ComponentActivity() {
    private val scope = MainScope()
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)
        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return }

        val devices = DeviceRepository(this).getDevices()

        // Auto-close if already configured (by provideGlance auto-config)
        scope.launch {
            val glanceId = GlanceAppWidgetManager(this@WidgetConfigActivity)
                .getGlanceIdBy(appWidgetId)
            val state = getAppWidgetState(this@WidgetConfigActivity,
                PreferencesGlanceStateDefinition, glanceId)
            if (state[WidgetKeys.DEVICE_NAME] != null) {
                setResult(RESULT_OK, Intent().putExtra(
                    AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
                finish()
            }
        }

        setContent {
            GarageTheme {
                ConfigScreen(devices = devices, onDeviceSelected = { onDeviceChosen(it) })
            }
        }
    }

    private fun onDeviceChosen(device: Device) {
        scope.launch {
            val glanceId = GlanceAppWidgetManager(this@WidgetConfigActivity)
                .getGlanceIdBy(appWidgetId)
            updateAppWidgetState(this@WidgetConfigActivity,
                PreferencesGlanceStateDefinition, glanceId) { prefs ->
                prefs.toMutablePreferences().apply {
                    this[WidgetKeys.DEVICE_ID] = device.id
                    this[WidgetKeys.DEVICE_NAME] = device.name
                    this[WidgetKeys.DEVICE_MAC] = device.mac
                    this[WidgetKeys.DEVICE_PASSWORD] = device.password
                    this[WidgetKeys.DEVICE_SWITCH_ID] = device.switchId.toString()
                    this[WidgetKeys.APP_WIDGET_ID] = appWidgetId.toString()
                    this[WidgetKeys.STATUS] = WidgetStatus.IDLE
                    this[WidgetKeys.STATUS_TEXT] = "Tippen zum Auslösen"
                }
            }
            GarageWidget().update(this@WidgetConfigActivity, glanceId)
            setResult(RESULT_OK, Intent().putExtra(
                AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
            finish()
        }
    }
}
```

### Multi-Device Widget (Alle Garagen)

Zweiter Widget-Typ der ALLE konfigurierten Geräte zeigt (4x2 Standard, resizable).
Keine Konfiguration nötig — zeigt automatisch alle Geräte aus DeviceRepository.

| Widget | Größe | Funktion |
|--------|-------|----------|
| **Alle Garagen** | 4x2 (resizable ab 180x57dp) | Alle Geräte mit Status + Trigger-Button |

#### Architektur

```
widget/
├── MultiWidgetKeys.kt              # Per-device DataStore Keys (status_$id, status_text_$id)
├── MultiGarageWidget.kt            # GlanceAppWidget: LazyColumn mit allen Geräten
├── MultiGarageWidgetReceiver.kt    # GlanceAppWidgetReceiver Bridge
└── MultiTriggerAction.kt           # ActionCallback mit per-device ActionParameters
```

#### Per-Device ActionParameters

Jeder Trigger-Button bekommt eigene ActionParameters mit allen Gerätedaten:
- `device_mac`, `device_name`, `device_password`, `device_switch_id`, `device_id`

MultiTriggerAction liest diese direkt aus den ActionParameters (kein DataStore-Lookup).

#### Status-Feedback

Per-device Status in multi-widget DataStore:
- `stringPreferencesKey("status_${device.id}")` → idle/connecting/triggered/error
- `stringPreferencesKey("status_text_${device.id}")` → "Bereit"/"Verbinde..."/"Ausgelöst!"/Fehler

Service aktualisiert ALLE MultiGarageWidget-Instanzen via `GlanceAppWidgetManager.getGlanceIds()`.

#### Widget-Info (res/xml/multi_garage_widget_info.xml)

```xml
<appwidget-provider
    android:minWidth="250dp"
    android:minHeight="110dp"
    android:minResizeWidth="180dp"
    android:minResizeHeight="57dp"
    android:targetCellWidth="4"
    android:targetCellHeight="2"
    android:resizeMode="horizontal|vertical"
    android:updatePeriodMillis="0"
    android:previewLayout="@layout/multi_garage_widget_preview"
    android:widgetCategory="home_screen"
    android:description="@string/multi_widget_description" />
```

#### WidgetTriggerService Erweiterung

- Neue Extras: `EXTRA_WIDGET_TYPE` ("single"/"multi"), `EXTRA_DEVICE_ID`
- `onStartCommand()`: Wenn `widgetType == "multi"`, kein appWidgetId nötig
- `updateMultiStatus()`: ALLE MultiGarageWidget-Instanzen via GlanceAppWidgetManager
  aktualisieren mit per-device Status-Keys

#### Auto-Refresh

GarageViewModel ruft nach `addDevice()`, `updateDevice()`, `deleteDevice()`, `importDevices()`
automatisch `MultiGarageWidget().updateAll(context)` auf.

### Pin Widget aus App-Settings (MainActivity)

```kotlin
private fun requestPinWidget(device: Device, viewModel: GarageViewModel) {
    val appWidgetManager = AppWidgetManager.getInstance(this)
    if (!appWidgetManager.isRequestPinAppWidgetSupported) {
        viewModel.showToast("Launcher unterstützt keine Widgets", isError = true)
        return
    }
    getSharedPreferences("garage_settings", MODE_PRIVATE)
        .edit().putString("pending_widget_device", device.id).commit()
    val provider = ComponentName(this, GarageWidgetReceiver::class.java)
    appWidgetManager.requestPinAppWidget(provider, null, null)
}
```

### AndroidManifest.xml

```xml
<!-- Permissions -->
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE" />

<!-- Widget Receiver -->
<receiver android:name=".widget.GarageWidgetReceiver" android:exported="true">
    <intent-filter>
        <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />
    </intent-filter>
    <meta-data android:name="android.appwidget.provider"
               android:resource="@xml/garage_widget_info" />
</receiver>

<!-- Config Activity -->
<activity android:name=".widget.WidgetConfigActivity" android:exported="true">
    <intent-filter>
        <action android:name="android.appwidget.action.APPWIDGET_CONFIGURE" />
    </intent-filter>
</activity>

<!-- BLE Trigger Service -->
<service android:name=".widget.WidgetTriggerService"
         android:foregroundServiceType="connectedDevice"
         android:exported="false" />
```

### Widget User Flow

```
1. HOME SCREEN → WIDGET PICKER
              ↓
2. "Garage Widget" auswählen (1x1 Standard, resizable)
              ↓
3. WidgetConfigActivity öffnet
   ┌─────────────────────────────┐
   │ Widget einrichten           │
   │ Wähle ein Gerät             │
   │                             │
   │ ┌─────────────────────────┐ │
   │ │ Hauptgarage             │ │
   │ │ CC:DB:A7:CF:EB:00       │ │
   │ └─────────────────────────┘ │
   └─────────────────────────────┘
              ↓
4. Gerät tippen → Config in Glance DataStore → Widget platziert
              ↓
5. Widget tippen → ForegroundService → BLE → Ausgelöst!
   ┌──────────┐
   │ Hauptgar.│    Status-Farben:
   │Ausgelöst!│    Gelb=Verbinde, Grün=OK, Rot=Fehler
   └──────────┘
```

---

## Verbindungslogik

### Ablauf bei Trigger

```
1. User tippt Trigger-Button / Widget
        ↓
2. Versuche Direktverbindung via MAC
   bluetoothAdapter.getRemoteDevice(mac).connectGatt(...)
        ↓
3. Falls fehlgeschlagen: Scan nach Gerätenamen
   Suche "Shelly*{MAC_SUFFIX}" in BLE Advertisements
        ↓
4. Sende "Switch.Set" RPC
        ↓
5. Bei 401: Auth hinzufügen und wiederholen
        ↓
6. UI Update: "Ausgelöst!" / Fehler anzeigen
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
