package de.beat2er.garage.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class DeviceRepository(context: Context) {

    private val prefs = context.getSharedPreferences("garage_devices", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun getDevices(): List<Device> {
        val json = prefs.getString("devices", "[]") ?: "[]"
        val type = object : TypeToken<List<Device>>() {}.type
        return gson.fromJson(json, type)
    }

    fun saveDevices(devices: List<Device>) {
        prefs.edit().putString("devices", gson.toJson(devices)).apply()
    }

    fun addDevice(device: Device) {
        val devices = getDevices().toMutableList()
        devices.add(device)
        saveDevices(devices)
    }

    fun updateDevice(device: Device) {
        val devices = getDevices().toMutableList()
        val index = devices.indexOfFirst { it.id == device.id }
        if (index != -1) {
            devices[index] = device
            saveDevices(devices)
        }
    }

    fun removeDevice(id: String) {
        saveDevices(getDevices().filter { it.id != id })
    }
}
