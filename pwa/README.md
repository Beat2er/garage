# 🚗 Garage Bluetooth Opener PWA v2

Steuere mehrere Shelly Garagentore direkt via Bluetooth - ohne WLAN!

## ✨ Features

- **Multi-Device Support** - Mehrere Garagentore verwalten
- **QR-Code Sharing** - URL im QR - funktioniert mit jeder Kamera-App  
- **Auto-Reconnect** - Verbindet ohne Picker (wenn möglich)
- **Auto-Update** - Benachrichtigung bei neuen Versionen
- **Offline-fähig** - PWA funktioniert nach Installation ohne Internet
- **iOS + Android** - Chrome (Android) oder Bluefy App (iOS)

## 📱 Schnellstart

### 1. Shelly vorbereiten (einmalig)

1. Shelly ins WLAN bringen und Webinterface öffnen
2. **Settings → Bluetooth**:
   - ✅ Enable Bluetooth
   - ✅ Enable RPC over BLE
3. **Scripts → Add Script**:
   - `shelly-garage-script.js` einfügen
   - ✅ "Run on startup" aktivieren
   - Save & Start
4. MAC-Adresse notieren: **Settings → Device Info → MAC Address**

### 2. PWA hosten (HTTPS erforderlich!)

**Option A: GitHub Pages (empfohlen)**
1. Repository auf github.com erstellen
2. Alle Dateien hochladen
3. Settings → Pages → Source: main branch
4. URL: `https://username.github.io/repo-name/`

**Option B: Lokaler Test**
```bash
# Mit Python (selbstsigniertes Zertifikat)
python3 -m http.server 8443 --bind 0.0.0.0
# Dann im Browser: https://localhost:8443 (Warnung akzeptieren)
```

### 3. App installieren

**Android (Chrome):**
1. URL in Chrome öffnen
2. Menü (⋮) → "Zum Startbildschirm hinzufügen"

**iOS:**
1. [Bluefy App](https://apps.apple.com/app/id1492822055) installieren (kostenlos)
2. URL in Bluefy öffnen
3. Teilen (↑) → "Zum Home-Bildschirm"

## 🔧 Gerät hinzufügen

1. App öffnen → "Gerät hinzufügen"
2. **Name**: z.B. "Garage Vorne"
3. **MAC-Adresse**: z.B. `CC:DB:A7:CF:EB:02`
4. **Passwort**: Falls Shelly Auth aktiviert (empfohlen!)
5. "Hinzufügen"

## 📤 Konfiguration teilen

**QR-Code erstellen:**
1. "Teilen" → QR-Code wird angezeigt
2. Andere scannen den Code mit ihrer App

**QR-Code scannen:**
1. "Teilen" → Tab "QR scannen"
2. Kamera auf QR-Code halten
3. Geräte werden automatisch importiert

**Hinweis:** Passwörter werden aus Sicherheitsgründen NICHT im QR-Code übertragen! 
Nach dem Import muss jeder Nutzer das Passwort selbst eingeben.

## 🔐 Sicherheit

### Passwort setzen (dringend empfohlen!)

Im Shelly Webinterface oder per API:
```bash
# Passwort-Hash erstellen
echo -n "admin:shelly:dein_passwort" | sha256sum

# Dann im Shelly setzen (Settings → Auth oder per API):
curl "http://SHELLY_IP/rpc/Shelly.SetAuth" \
  -d '{"user":"admin","realm":"shelly","ha1":"HASH_VON_OBEN"}'
```

### Wichtige Hinweise

⚠️ **BLE ist unverschlüsselt** - Das Passwort kann theoretisch abgehört werden
- Für ein Garagentor akzeptables Risiko (wie bei Funk-Fernbedienung)
- Bei höheren Anforderungen: Zusätzlichen physischen Schalter verwenden
- Immer ein Passwort setzen!

## 📁 Dateien

```
├── index.html              # PWA Hauptdatei
├── manifest.json           # PWA Manifest
├── sw.js                   # Service Worker (Offline)
├── version.json            # Version für Auto-Update
├── icon-192.png            # App Icon (klein)
├── icon-512.png            # App Icon (groß)
├── shelly-garage-script.js # Shelly-seitiges Script
└── README.md               # Diese Datei
```

## 🔄 Auto-Update

Die App prüft automatisch auf Updates:
- 3 Sekunden nach dem Laden
- Alle 5 Minuten im Hintergrund
- Manuell in Einstellungen → "Nach Updates suchen"

**Für Entwickler:** Um ein Update auszurollen:
1. `version.json` bearbeiten und Version erhöhen
2. `CURRENT_VERSION` in `index.html` anpassen
3. Alle Dateien auf Server hochladen

Die App zeigt einen Banner wenn eine neue Version verfügbar ist.

## 🔍 Troubleshooting

| Problem | Lösung |
|---------|--------|
| "Web Bluetooth nicht unterstützt" | Chrome (Android) oder Bluefy (iOS) nutzen |
| Shelly wird nicht gefunden | BLE + RPC aktiviert? Näher rangehen (max. 10-30m) |
| Verbindung schlägt fehl | Richtiges Gerät ausgewählt? MAC überprüfen |
| Auth-Fehler | Passwort in Geräte-Einstellungen eingeben |
| Tor reagiert nicht | Script läuft? Verkabelung OK? |

## 🔌 Verkabelung

```
Shelly 2PM                    Garagentor-Antrieb
═══════════                   ══════════════════
L  ←────── 230V Phase
N  ←────── 230V Neutral

SW (Output 1) ───────────────→ Taster-Eingang
     COM      ───────────────→ Taster-GND/COM
```

## 🛠️ Technische Details

- **BLE GATT Service:** `5f6d4f53-5f52-5043-5f53-56435f49445f`
- **Protokoll:** JSON-RPC 2.0 über BLE
- **Auth:** SHA-256 Digest Authentication
- **QR-Format:** JSON mit `{v:1, d:[{n:"Name", m:"mac"}]}`

## 📚 Referenzen

- [Shelly Gen2 API Docs](https://shelly-api-docs.shelly.cloud/gen2/)
- [Shelly BLE RPC](https://shelly-api-docs.shelly.cloud/gen2/ComponentsAndServices/BLE/)
- [Web Bluetooth API](https://developer.mozilla.org/en-US/docs/Web/API/Web_Bluetooth_API)
- [Bluefy für iOS](https://apps.apple.com/app/id1492822055)

---

Made with ❤️ for Shelly enthusiasts
