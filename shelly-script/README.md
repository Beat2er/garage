# Shelly Garage Door Impulse Script

## Übersicht

Dieses Script wandelt einen dauerhaften Ein-Befehl in einen kurzen Impuls um - 
perfekt für Garagentor-Antriebe die nur einen Taster-Impuls erwarten.

## Funktionen

- **Auto-Off**: Schaltet nach 500ms automatisch aus
- **Cooldown**: Verhindert Doppelauslösung (2 Sekunden Sperre)
- **Debug-Logging**: Optional aktivierbar

## Installation

1. **Shelly Webinterface öffnen** (z.B. `http://192.168.1.100`)
2. **Scripts** → **Add Script**
3. **Code einfügen** aus `shelly-garage-script.js`
4. **"Run on startup"** aktivieren ✓
5. **Save** und **Start**

## Konfiguration

```javascript
let CONFIG = {
    switch_id: 0,             // Switch-Kanal (0 oder 1 bei 2PM)
    impulse_duration_ms: 500, // Impulsdauer in Millisekunden
    cooldown_ms: 2000,        // Sperre gegen Doppelauslösung
    debug: true               // Debug-Ausgaben im Log
};
```

### Parameter

| Parameter | Default | Beschreibung |
|-----------|---------|--------------|
| `switch_id` | `0` | Welcher Ausgang (0 = erster, 1 = zweiter bei 2PM) |
| `impulse_duration_ms` | `500` | Wie lange der Impuls dauert (ms) |
| `cooldown_ms` | `2000` | Mindestzeit zwischen zwei Auslösungen |
| `debug` | `true` | Log-Ausgaben aktivieren |

## Alternative: Eingebauter Auto-Off Timer

Falls du kein Script verwenden möchtest, hat der Shelly einen eingebauten Timer:

1. **Settings** → **Switch 0**
2. **Auto Off** → `0.5` (Sekunden)
3. Speichern

**Nachteile gegenüber Script:**
- Kein Cooldown
- Weniger Kontrolle

## Verkabelung

```
Shelly 2PM                    Garagentor-Antrieb
═══════════                   ══════════════════

L  ←────────── 230V Phase
N  ←────────── 230V Neutral

SW (Output 0) ────────────────→ Taster-Eingang
     COM      ────────────────→ Taster-GND/COM
```

**Wichtig:** 
- Potentialfreier Kontakt verwenden!
- Prüfe Spannung am Taster-Eingang deines Antriebs

## Debug-Log lesen

Im Shelly Webinterface:
1. **Scripts**
2. Script auswählen
3. **Console** Tab

Beispielausgabe:
```
[Garage] Script gestartet - Switch 0
[Garage] Impuls gestartet
[Garage] Impuls beendet
[Garage] Cooldown aktiv, ignoriere
```

## Fehlersuche

| Problem | Lösung |
|---------|--------|
| Script startet nicht | "Run on startup" aktiviert? |
| Kein Impuls | Richtiger `switch_id`? Verkabelung OK? |
| Doppelauslösung | `cooldown_ms` erhöhen |
| Tor bleibt offen | `impulse_duration_ms` zu lang? |
