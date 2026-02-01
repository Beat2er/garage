package de.beat2er.garage.data

import java.util.UUID

data class Device(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val mac: String,
    val password: String = "",
    val switchId: Int = 0
) {
    val macSuffix: String
        get() = mac.replace(":", "").uppercase()
}
