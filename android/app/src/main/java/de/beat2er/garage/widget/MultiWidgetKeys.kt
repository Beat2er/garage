package de.beat2er.garage.widget

import androidx.datastore.preferences.core.stringPreferencesKey

object MultiWidgetKeys {
    fun statusKey(deviceId: String) = stringPreferencesKey("status_$deviceId")
    fun statusTextKey(deviceId: String) = stringPreferencesKey("status_text_$deviceId")
}
