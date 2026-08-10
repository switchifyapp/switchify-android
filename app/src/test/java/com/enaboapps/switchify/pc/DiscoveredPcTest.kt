package com.enaboapps.switchify.pc

import org.junit.Assert.assertEquals
import org.junit.Test

class DiscoveredPcTest {
    @Test
    fun controlDeviceNamePrefersBluetoothDeviceName() {
        val pc = pc(
            serviceName = "Switchify PC",
            deviceName = "Oliver Laptop",
            endpointDisplayName = "Switchify PC",
            platform = PcPlatform.Windows
        )

        assertEquals("Oliver Laptop", pc.controlDeviceName)
    }

    @Test
    fun macOSControlDeviceNamePrefersStatusDisplayName() {
        val pc = pc(
            serviceName = "Owen’s Mac Studio",
            deviceName = "Mac",
            endpointDisplayName = "Owen’s Mac Studio",
            platform = PcPlatform.MacOS
        )

        assertEquals("Owen’s Mac Studio", pc.controlDeviceName)
        assertEquals("AA:BB:CC:DD:EE:FF", pc.primaryAddress)
    }

    @Test
    fun macOSControlDeviceNameFallsBackToProductName() {
        val pc = pc(
            serviceName = "",
            deviceName = "Mac",
            endpointDisplayName = "",
            platform = PcPlatform.MacOS
        )

        assertEquals("Switchify PC", pc.controlDeviceName)
    }

    @Test
    fun missingPlatformPreservesBluetoothDeviceNamePreference() {
        val pc = pc(
            serviceName = "Owen’s Mac Studio",
            deviceName = "Legacy device name",
            endpointDisplayName = "Owen’s Mac Studio"
        )

        assertEquals("Legacy device name", pc.controlDeviceName)
    }

    @Test
    fun resolvedNamesTrimSurroundingWhitespace() {
        val pc = pc(
            serviceName = " Switchify PC ",
            deviceName = "  Oliver Laptop  ",
            endpointDisplayName = " Office PC ",
            platform = PcPlatform.Windows
        )

        assertEquals("Oliver Laptop", pc.controlDeviceName)
        assertEquals("Oliver Laptop", pc.primaryAddress)
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
        endpointDisplayName: String,
        platform: PcPlatform? = null
    ): DiscoveredPc {
        return DiscoveredPc(
            serviceName = serviceName,
            desktopId = "desktop-1",
            bluetoothEndpoint = PcBluetoothEndpoint(
                deviceAddress = "AA:BB:CC:DD:EE:FF",
                deviceName = deviceName,
                desktopId = "desktop-1",
                displayName = endpointDisplayName,
                platform = platform
            )
        )
    }
}
