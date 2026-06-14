package com.enaboapps.switchify.pc.bluetooth

import java.util.UUID

object PcBleConstants {
    val serviceUuid: UUID = UUID.fromString("7a78f7e8-1d6d-4d92-9ef0-1f89d3db21f4")
    val rxCharacteristicUuid: UUID = UUID.fromString("7a78f7e9-1d6d-4d92-9ef0-1f89d3db21f4")
    val txCharacteristicUuid: UUID = UUID.fromString("7a78f7ea-1d6d-4d92-9ef0-1f89d3db21f4")
    val statusCharacteristicUuid: UUID = UUID.fromString("7a78f7eb-1d6d-4d92-9ef0-1f89d3db21f4")
    val clientCharacteristicConfigUuid: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    const val bluetoothFrameVersion = 1
    const val defaultBluetoothFramePayloadBytes = 160
    const val defaultBluetoothMaxMessageBytes = 16 * 1024
    const val defaultBluetoothPartialTimeoutMs = 10_000L
}
