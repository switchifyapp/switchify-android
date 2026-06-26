package com.enaboapps.switchify.pc

import org.junit.Assert.assertEquals
import org.junit.Test

class DiscoveredPcTest {
    @Test
    fun controlDeviceNamePrefersBluetoothDeviceName() {
        val pc = pc(
            serviceName = "Switchify PC",
            deviceName = "Oliver Laptop",
            endpointDisplayName = "Switchify PC"
        )

        assertEquals("Oliver Laptop", pc.controlDeviceName)
    }

    @Test
    fun controlDeviceNameFallsBackToEndpointDisplayName() {
        val pc = pc(
            serviceName = "Switchify PC",
            deviceName = null,
            endpointDisplayName = "Office PC"
        )

        assertEquals("Office PC", pc.controlDeviceName)
    }

    @Test
    fun controlDeviceNameFallsBackToServiceName() {
        val pc = DiscoveredPc(
            serviceName = "Saved PC",
            desktopId = "desktop-1"
        )

        assertEquals("Saved PC", pc.controlDeviceName)
    }

    @Test
    fun existingDisplayNameBehaviorIsUnchanged() {
        val pc = pc(
            serviceName = "Switchify PC",
            deviceName = "Oliver Laptop",
            endpointDisplayName = "Office PC"
        )

        assertEquals("Switchify PC", pc.displayName)
    }

    private fun pc(
        serviceName: String,
        deviceName: String?,
        endpointDisplayName: String
    ): DiscoveredPc {
        return DiscoveredPc(
            serviceName = serviceName,
            desktopId = "desktop-1",
            bluetoothEndpoint = PcBluetoothEndpoint(
                deviceAddress = "AA:BB:CC:DD:EE:FF",
                deviceName = deviceName,
                desktopId = "desktop-1",
                displayName = endpointDisplayName
            )
        )
    }
}
