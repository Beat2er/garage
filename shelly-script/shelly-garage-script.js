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
    failsafe_ms: 1500,      // Maximale Ein-Zeit (Failsafe)
    failsafe_interval_ms: 3000, // Watchdog-Prüfintervall
    debug: true             // Debug-Ausgaben
};

let lastTrigger = 0;
let impulseTimer = null;
let failsafeTimer = null;

function log(msg) {
    if (CONFIG.debug) {
        print("[Garage] " + msg);
    }
}

function forceOff() {
    Shelly.call("Switch.Set", {
        id: CONFIG.switch_id,
        on: false
    }, function(result, error) {
        if (error) {
            log("Fehler beim Ausschalten: " + JSON.stringify(error));
            // Bei Fehler nochmal versuchen
            Timer.set(200, false, forceOff);
        } else {
            log("Switch ausgeschaltet");
        }
    });
}

Shelly.addEventHandler(function(event) {
    if (event.component !== "switch:" + JSON.stringify(CONFIG.switch_id)) {
        return;
    }

    if (!event.info || event.info.state !== true) {
        return;
    }

    let now = Date.now();

    // Cooldown-Check
    if (now - lastTrigger < CONFIG.cooldown_ms) {
        log("Cooldown aktiv – sofort ausschalten");
        forceOff();
        return;
    }

    lastTrigger = now;
    log("Impuls gestartet");

    // Vorherigen Timer aufräumen (falls noch aktiv)
    if (impulseTimer !== null) {
        Timer.clear(impulseTimer);
    }
    if (failsafeTimer !== null) {
        Timer.clear(failsafeTimer);
    }

    // Normaler Impuls-Timer
    impulseTimer = Timer.set(CONFIG.impulse_duration_ms, false, function() {
        impulseTimer = null;
        log("Impuls beendet");
        forceOff();
    });

    // Failsafe: Falls der Impuls-Timer nicht greift
    failsafeTimer = Timer.set(CONFIG.failsafe_ms, false, function() {
        failsafeTimer = null;
        log("FAILSAFE: Erzwinge Ausschalten!");
        forceOff();
    });
});

// Watchdog: Prüft periodisch ob Switch ungewollt an ist
Timer.set(CONFIG.failsafe_interval_ms, true, function() {
    Shelly.call("Switch.GetStatus", { id: CONFIG.switch_id }, function(res, err) {
        if (err) return;
        if (res.output === true) {
            let elapsed = Date.now() - lastTrigger;
            if (elapsed > CONFIG.failsafe_ms) {
                log("WATCHDOG: Switch zu lange an (" + JSON.stringify(elapsed) + "ms) – schalte aus!");
                forceOff();
            }
        }
    });
});

log("Script gestartet – Switch " + JSON.stringify(CONFIG.switch_id) + " mit Failsafe");