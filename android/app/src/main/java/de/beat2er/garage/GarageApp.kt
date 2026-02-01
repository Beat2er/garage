package de.beat2er.garage

import android.app.Application
import de.beat2er.garage.widget.WidgetTriggerService

class GarageApp : Application() {
    override fun onCreate() {
        super.onCreate()
        WidgetTriggerService.createChannel(this)
    }
}
