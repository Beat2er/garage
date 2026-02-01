// ============================================
// Shelly Garage Door Impulse Script
// Für Shelly Plus 2PM / Pro 2PM / Gen4
// ============================================
// Schaltet automatisch nach kurzer Zeit aus
// um einen Taster-Impuls zu simulieren

let CONFIG = {
    switch_id: 0,           // Switch-Kanal (0 oder 1)
    impulse_duration_ms: 500, // Impulsdauer in ms
    cooldown_ms: 2000,      // Sperre gegen Doppelauslösung
    debug: true             // Debug-Ausgaben
};

let lastTrigger = 0;

function log(msg) {
    if (CONFIG.debug) {
        print("[Garage] " + msg);
    }
}

Shelly.addEventHandler(function(event) {
    // Nur auf Switch-Events reagieren
    if (event.component !== "switch:" + JSON.stringify(CONFIG.switch_id)) {
        return;
    }
    
    // Nur auf Einschalten reagieren
    if (!event.info || event.info.state !== true) {
        return;
    }
    
    let now = Date.now();
    
    // Cooldown-Check
    if (now - lastTrigger < CONFIG.cooldown_ms) {
        log("Cooldown aktiv, ignoriere");
        return;
    }
    
    lastTrigger = now;
    log("Impuls gestartet");
    
    // Nach Impulsdauer ausschalten
    Timer.set(CONFIG.impulse_duration_ms, false, function() {
        Shelly.call("Switch.Set", {
            id: CONFIG.switch_id,
            on: false
        }, function(result, error) {
            if (error) {
                log("Fehler beim Ausschalten: " + JSON.stringify(error));
            } else {
                log("Impuls beendet");
            }
        });
    });
});

log("Script gestartet - Switch " + JSON.stringify(CONFIG.switch_id));
