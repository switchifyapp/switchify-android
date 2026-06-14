package com.enaboapps.switchify.pc.bluetooth

import com.enaboapps.switchify.pc.PcBluetoothEndpoint
import org.json.JSONObject

object PcBleStatusParser {
    private const val protocolVersion = 1
    private const val defaultDisplayName = "Switchify PC"

    fun parse(deviceAddress: String, deviceName: String?, rawStatus: String): PcBluetoothEndpoint? {
        return runCatching {
            val json = JSONObject(rawStatus)
            if (json.optInt("protocolVersion") != protocolVersion) return null
            val desktopId = json.optString("desktopId").trim()
            if (desktopId.isBlank()) return null
            val displayName = json.optString("displayName").trim().ifBlank { defaultDisplayName }
            PcBluetoothEndpoint(
                deviceAddress = deviceAddress,
                deviceName = deviceName,
                desktopId = desktopId,
                displayName = displayName
            )
        }.getOrNull()
    }
}
