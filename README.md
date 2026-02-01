# Shelly BLE Garage Door Opener

Steuere Shelly-Geräte via Bluetooth Low Energy - ohne WLAN!

## Projektstruktur

```
garage-complete/
├── pwa/                    # Progressive Web App (Quelle)
│   ├── index.html          # Hauptdatei
│   ├── sw.js               # Service Worker
│   ├── manifest.json       # PWA Manifest
│   ├── version.json        # Auto-Update Version
│   └── icon-*.png          # App Icons
│
├── android/                # Native Android App (Kotlin/Compose)
│   └── app/src/main/java/de/beat2er/garage/
│       ├── ble/            # BLE-Kommunikation
│       ├── data/           # Datenmodelle + Repository
│       ├── ui/             # Compose UI (Screens, Components, Theme)
│       ├── update/         # Auto-Update Prüfung
│       ├── viewmodel/      # ViewModel + State
│       └── widget/         # Home Screen Widget (Jetpack Glance)
│
├── docs/                   # GitHub Pages Deployment
│   ├── index.html          # PWA (deployed)
│   ├── sw.js               # Service Worker
│   ├── manifest.json       # PWA Manifest
│   └── app/                # Android APK Downloads
│       ├── index.html      # Download-Seite
│       └── version.json    # Aktuelle Version + APK-URL
│
├── shelly-script/          # Shelly-seitiges Script
│   ├── shelly-garage-script.js
│   └── README.md
│
└── specs/                  # Spezifikationen
    ├── PROTOCOL_SPEC.md    # BLE RPC Protokoll
    └── ANDROID_APP_SPEC.md # Android App Spezifikation
```

## Feature-Parität

PWA und Android-App werden feature-gleich gehalten:

| Feature | PWA | Android |
|---------|-----|---------|
| BLE-Verbindung via MAC | Ja | Ja |
| Multi-Device Support | Ja | Ja |
| QR-Code Sharing | Ja | Ja |
| QR-Code Scanner | Ja | Ja |
| Bluetooth-Scan | Ja | Ja |
| Auto-Update Erkennung | Ja | Ja |
| Debug-Modus | Ja | Ja |
| Offline-fähig | Ja | Ja |
| Passwort-Authentifizierung | Ja | Ja |
| Home Screen Widget | - | Ja |
| BLE-Berechtigungen dauerhaft | - | Ja |

## Quick Start

### 1. Shelly vorbereiten

1. Webinterface öffnen
2. **Settings → Bluetooth:**
   - Enable Bluetooth
   - Enable RPC
3. **Scripts → Add Script:**
   - `shelly-script/shelly-garage-script.js` einfügen
   - Run on startup aktivieren

### 2a. PWA nutzen (alle Plattformen)

Die PWA ist unter **https://beat2er.github.io/garage/** verfügbar.

- **Android:** Chrome → Menü → "Zum Startbildschirm"
- **iOS:** Bluefy App → Website öffnen → "Zum Home-Bildschirm"

### 2b. Android App installieren

APK herunterladen: **https://beat2er.github.io/garage/app/**

Die App prüft beim Start automatisch auf Updates.

### 3. Gerät hinzufügen

1. App öffnen → "Hinzufügen"
2. Name eingeben (z.B. "Hauptgarage")
3. WiFi-MAC eingeben (steht im Gerätenamen, z.B. `CC:DB:A7:CF:EB:00`)
4. Optional: Passwort

Alternativ: Bluetooth-Scan nutzen (findet Shelly-Geräte automatisch)

## GitHub Pages Deployment

Die Seite wird über `docs/` auf dem `master`-Branch gehostet.

### Einrichtung

1. GitHub Repository → Settings → Pages
2. Source: **Deploy from a branch**
3. Branch: `master`, Ordner: `/docs`
4. Speichern

### PWA aktualisieren

Bei Änderungen an der PWA (`pwa/`):
1. Änderungen in `pwa/` vornehmen
2. Geänderte Dateien nach `docs/` kopieren
3. `docs/version.json` Version erhöhen (für Auto-Update)

### APK veröffentlichen

1. APK bauen (Android Studio → Build → Build APK)
2. APK nach `docs/app/garage-vX.Y.Z.apk` kopieren
3. `docs/app/version.json` aktualisieren (versionName, apkUrl, changelog)
4. Commit + Push

## Komponenten

### PWA (pwa/ → docs/)

Progressive Web App für Browser:
- Multi-Device Support
- QR-Code Sharing (URL im QR)
- Auto-Update
- Auto-Reconnect (Chrome)
- Offline-fähig

**Unterstützte Browser:**
- Android: Chrome, Edge, Brave
- iOS: Bluefy App (kostenlos)

### Android App (android/)

Native Android App mit Jetpack Compose:
- Direktverbindung via MAC (kein Picker)
- BLE-Scan zum Finden von Geräten
- QR-Code Import/Export
- Auto-Update Erkennung
- Debug-Logging
- Home Screen Widget (1-Tap Trigger via ForegroundService)

### Shelly Script (shelly-script/)

Optionales Script für Auto-Off Impuls:
- 500ms Impuls
- Cooldown gegen Doppelauslösung
- Konfigurierbarer Switch-Kanal

### Spezifikationen (specs/)

- **PROTOCOL_SPEC.md** - BLE RPC Protokoll, GATT UUIDs, JSON-RPC Format
- **ANDROID_APP_SPEC.md** - Native Android App Architektur

## Sicherheit

**BLE ist unverschlüsselt!**

- Immer Passwort setzen
- Vergleichbar mit Funk-Fernbedienung
- Für Garagentor akzeptables Risiko

## Support

- Shelly Gen2+ erforderlich (Plus, Pro, Gen3, Gen4)
- Gen1 wird NICHT unterstützt (kein BLE RPC)

## Lizenz

MIT License - Nutzung auf eigene Gefahr
