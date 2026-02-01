package de.beat2er.garage.ble

import java.util.UUID

object ShellyBleConstants {
    val SERVICE_UUID: UUID = UUID.fromString("5f6d4f53-5f52-5043-5f53-56435f49445f")
    val RPC_DATA_UUID: UUID = UUID.fromString("5f6d4f53-5f52-5043-5f64-6174615f5f5f")
    val TX_CTL_UUID: UUID = UUID.fromString("5f6d4f53-5f52-5043-5f74-785f63746c5f")
    val RX_CTL_UUID: UUID = UUID.fromString("5f6d4f53-5f52-5043-5f72-785f63746c5f")

    const val CHUNK_SIZE = 512
    const val CONNECTION_TIMEOUT_MS = 15_000L
    const val SCAN_TIMEOUT_MS = 10_000L
}
