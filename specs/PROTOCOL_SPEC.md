# Shelly BLE Garage Door Opener - Spezifikation

## Übersicht

Ein System zur Steuerung von Shelly-Geräten (z.B. Garagentore) via Bluetooth Low Energy (BLE) ohne WLAN-Verbindung. Besteht aus:

1. **PWA** - Progressive Web App für Browser (Android Chrome / iOS Bluefy)
2. **Shelly Script** - Auto-Off Impuls-Logik auf dem Shelly
3. **Android App** (geplant) - Native App für direkten BLE-Zugriff

---

## Shelly BLE RPC Protokoll

### GATT Service & Characteristics

| Name | UUID | Beschreibung |
|------|------|--------------|
| Service | `5f6d4f53-5f52-5043-5f53-56435f49445f` | Shelly RPC Service |
| RPC Data | `5f6d4f53-5f52-5043-5f64-6174615f5f5f` | Daten senden/empfangen |
| TX Control | `5f6d4f53-5f52-5043-5f74-785f63746c5f` | Sendelänge übermitteln |
| RX Control | `5f6d4f53-5f52-5043-5f72-785f63746c5f` | Empfangslänge lesen |

### Kommunikationsablauf

```
1. SENDEN
   ┌─────────────────────────────────────────────────────────────┐
   │ a) Länge als 4-Byte Big-Endian an TX Control schreiben      │
   │ b) JSON-RPC Payload an RPC Data schreiben (max 512B Chunks) │
   └─────────────────────────────────────────────────────────────┘

2. EMPFANGEN
   ┌─────────────────────────────────────────────────────────────┐
   │ a) 4-Byte Länge von RX Control lesen                        │
   │ b) Response-Daten von RPC Data lesen (ggf. mehrere Chunks)  │
   └─────────────────────────────────────────────────────────────┘
```

### JSON-RPC Format

**Request:**
```json
{
  "id": 1234567890,
  "src": "garage_app",
  "method": "Switch.Set",
  "params": {
    "id": 0,
    "on": true
  }
}
```

**Response (Erfolg):**
```json
{
  "id": 1234567890,
  "src": "shellyplus2pm-xxxxxxxxxxxx",
  "dst": "garage_app",
  "result": {
    "was_on": false
  }
}
```

**Response (Auth erforderlich):**
```json
{
  "id": 1234567890,
  "src": "shellyplus2pm-xxxxxxxxxxxx",
  "dst": "garage_app",
  "error": {
    "code": 401,
    "message": "{\"realm\":\"shellyplus2pm-xxxx\",\"nonce\":1234567890,\"nc\":1,\"algorithm\":\"SHA-256\"}"
  }
}
```

### SHA-256 Digest Authentication

Bei aktivierter Authentifizierung:

```
HA1 = SHA256("admin:" + realm + ":" + password)
HA2 = SHA256("dummy_method:dummy_uri")
response = SHA256(HA1 + ":" + nonce + ":" + nc + ":" + cnonce + ":auth:" + HA2)
```

**Request mit Auth:**
```json
{
  "id": 1234567890,
  "src": "garage_app",
  "method": "Switch.Set",
  "params": { "id": 0, "on": true },
  "auth": {
    "realm": "shellyplus2pm-xxxx",
    "username": "admin",
    "nonce": 1234567890,
    "cnonce": 9876543210,
    "response": "abc123...",
    "nc": 1,
    "algorithm": "SHA-256"
  }
}
```

---

## Shelly Konfiguration

### Voraussetzungen

1. **Bluetooth aktivieren:** Settings → Bluetooth → Enable Bluetooth ✓
2. **BLE RPC aktivieren:** Settings → Bluetooth → Enable RPC ✓
3. **Optional:** Authentifizierung setzen (empfohlen!)

### MAC-Adresse

**Wichtig:** Der Shelly hat zwei MAC-Adressen:

| Typ | Verwendung |
|-----|------------|
| WiFi-MAC | Im Gerätenamen (z.B. `ShellyPlus2PM-CCDBA7CFEB00`) |
| Bluetooth-MAC | Interne BLE-Adresse (nicht im Namen) |

→ **Immer WiFi-MAC verwenden** da diese im BLE-Advertisement-Namen steht!

### Gerätenamen-Schema

```
{Modell}-{WiFiMAC_uppercase}

Beispiele:
- ShellyPlus1-441793CD2E20
- ShellyPlus2PM-CCDBA7CFEB00
- ShellyPro4PM-A1B2C3D4E5F6
```

---

## Datenmodell

### Gerätekonfiguration (JSON)

```json
{
  "v": 1,
  "d": [
    {
      "n": "Garage Vorne",
      "m": "cc:db:a7:cf:eb:00"
    },
    {
      "n": "Garage Hinten", 
      "m": "44:17:93:cd:2e:20"
    }
  ]
}
```

| Feld | Beschreibung |
|------|--------------|
| `v` | Schema-Version (aktuell: 1) |
| `d` | Array von Geräten |
| `d[].n` | Anzeigename |
| `d[].m` | WiFi-MAC-Adresse (lowercase, mit Doppelpunkten) |

### QR-Code / Share-URL Format

```
https://example.com/garage/#import={base64_encoded_config}
```

Beispiel:
```
https://homeassistant.beat2er.de/local/garage/#import=eyJ2IjoxLCJkIjpbeyJuIjoiR2FyYWdlIiwiTSI6ImNjOmRiOmE3OmNmOmViOjAwIn1dfQ==
```

---

## Sicherheit

### Empfohlene Maßnahmen

1. **Immer Authentifizierung aktivieren** - Sonst kann jeder in BLE-Reichweite steuern
2. **Starkes Passwort verwenden** - Wird als SHA-256 Hash übertragen
3. **BLE ist unverschlüsselt** - Traffic kann abgehört werden (wie Funk-Fernbedienung)

### Passwort setzen (Shelly API)

```bash
# Hash generieren
echo -n "admin:shelly:DEIN_PASSWORT" | sha256sum

# Im Shelly setzen
curl "http://SHELLY_IP/rpc/Shelly.SetAuth" \
  -d '{"user":"admin","realm":"shelly","ha1":"HASH_VON_OBEN"}'
```

---

## Referenzen

- [Shelly Gen2 API Documentation](https://shelly-api-docs.shelly.cloud/gen2/)
- [Shelly BLE RPC](https://shelly-api-docs.shelly.cloud/gen2/ComponentsAndServices/BLE/)
- [shellyctl (Go CLI)](https://github.com/jcodybaker/shellyctl)
- [shelly-smart-device (Python)](https://github.com/epicRE/shelly-smart-device)
