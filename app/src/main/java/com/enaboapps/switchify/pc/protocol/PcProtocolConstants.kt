package com.enaboapps.switchify.pc.protocol

import java.util.UUID

object PcProtocolConstants {
    const val PROTOCOL_VERSION = 1
    const val FRAME_VERSION = 1
    const val DEFAULT_FRAME_PAYLOAD_BYTES = 160
    const val MAX_MESSAGE_BYTES = 16 * 1024
    const val MAX_PARTIAL_MESSAGES = 8
    const val PARTIAL_MESSAGE_TIMEOUT_MS = 10_000L
    const val DEFAULT_PRODUCT_NAME = "Switchify PC"
}

object PcBleUuids {
    val service: UUID = UUID.fromString("7a78f7e8-1d6d-4d92-9ef0-1f89d3db21f4")
    val receive: UUID = UUID.fromString("7a78f7e9-1d6d-4d92-9ef0-1f89d3db21f4")
    val transmit: UUID = UUID.fromString("7a78f7ea-1d6d-4d92-9ef0-1f89d3db21f4")
    val status: UUID = UUID.fromString("7a78f7eb-1d6d-4d92-9ef0-1f89d3db21f4")
    val response: UUID = UUID.fromString("7a78f7ec-1d6d-4d92-9ef0-1f89d3db21f4")
    val clientCharacteristicConfiguration: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
}

enum class PcResponseMode(val protocolValue: String) {
    Ack("ack"),
    None("none")
}
