package de.beat2er.garage.widget

import androidx.datastore.preferences.core.stringPreferencesKey

object WidgetKeys {
    val DEVICE_ID = stringPreferencesKey("device_id")
    val DEVICE_NAME = stringPreferencesKey("device_name")
    val DEVICE_MAC = stringPreferencesKey("device_mac")
    val DEVICE_PASSWORD = stringPreferencesKey("device_password")
    val DEVICE_SWITCH_ID = stringPreferencesKey("device_switch_id")
    val STATUS = stringPreferencesKey("status")
    val STATUS_TEXT = stringPreferencesKey("status_text")
}

object WidgetStatus {
    const val IDLE = "idle"
    const val CONNECTING = "connecting"
    const val TRIGGERED = "triggered"
    const val ERROR = "error"
}
