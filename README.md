# 🚗 Shelly BLE Garage Door Opener

Steuere Shelly-Geräte via Bluetooth Low Energy - ohne WLAN!

## Projektübersicht

```
garage-complete/
├── pwa/                    # Progressive Web App (Browser)
│   ├── index.html          # Hauptdatei
│   ├── sw.js               # Service Worker
│   ├── manifest.json       # PWA Manifest
│   ├── version.json        # Auto-Update Version
│   └── icon-*.png          # App Icons
│
├── shelly-script/          # Shelly-seitiges Script
│   ├── shelly-garage-script.js
│   └── README.md
│
└── specs/                  # Spezifikationen
    ├── PROTOCOL_SPEC.md    # BLE RPC Protokoll
    └── ANDROID_APP_SPEC.md # Android App Spezifikation
```

## Quick Start

### 1. Shelly vorbereiten

1. Webinterface öffnen
2. **Settings → Bluetooth:**
   - Enable Bluetooth ✓
   - Enable RPC ✓
3. **Scripts → Add Script:**
   - `shelly-script/shelly-garage-script.js` einfügen
   - Run on startup ✓

### 2. PWA installieren

1. Alle Dateien aus `pwa/` auf HTTPS-Server hochladen
2. **Android:** Chrome → Menü → "Zum Startbildschirm"
3. **iOS:** Bluefy App → Website öffnen → "Zum Home-Bildschirm"

### 3. Gerät hinzufügen

1. App öffnen → "Hinzufügen"
2. Name eingeben (z.B. "Hauptgarage")
3. WiFi-MAC eingeben (steht im Gerätenamen, z.B. `CC:DB:A7:CF:EB:00`)
4. Optional: Passwort

## Komponenten

### PWA (pwa/)

Progressive Web App für Browser:
- Multi-Device Support
- QR-Code Sharing (URL im QR)
- Auto-Update
- Auto-Reconnect (Chrome)
- Offline-fähig

**Unterstützte Browser:**
- Android: Chrome, Edge, Brave
- iOS: Bluefy App (kostenlos)

### Shelly Script (shelly-script/)

Optionales Script für Auto-Off Impuls:
- 500ms Impuls
- Cooldown gegen Doppelauslösung
- Konfigurierbarer Switch-Kanal

### Spezifikationen (specs/)

- **PROTOCOL_SPEC.md** - BLE RPC Protokoll, GATT UUIDs, JSON-RPC Format
- **ANDROID_APP_SPEC.md** - Native Android App Architektur

## Android App

Eine native Android App ermöglicht:
- Direktverbindung via MAC (kein Picker!)
- Schnellerer Verbindungsaufbau
- Home Screen Widget (optional)

Siehe `specs/ANDROID_APP_SPEC.md` für Details.

## Sicherheit

⚠️ **BLE ist unverschlüsselt!**

- Immer Passwort setzen
- Vergleichbar mit Funk-Fernbedienung
- Für Garagentor akzeptables Risiko

## Support

- Shelly Gen2+ erforderlich (Plus, Pro, Gen3, Gen4)
- Gen1 wird NICHT unterstützt (kein BLE RPC)

## Lizenz

MIT License - Nutzung auf eigene Gefahr
