package com.enaboapps.switchify.service.menu.menus.pc

import com.enaboapps.switchify.pc.DiscoveredPc
import com.enaboapps.switchify.pc.PcBluetoothEndpoint
import com.enaboapps.switchify.pc.PcPlatform
import org.junit.Assert.assertEquals
import org.junit.Test

class ChoosePcMenuStructureTest {
    @Test
    fun usesBluetoothDeviceNameWhenAvailable() {
        val pc = discoveredPc(deviceName = "  Office Laptop  ")

        val item = ChoosePcMenuStructure().getMenuItems(listOf(pc)) {}.single()

        assertEquals("Office Laptop", item.userProvidedText)
    }

    @Test
    fun fallsBackToDisplayNameWhenBluetoothDeviceNameIsNull() {
        val pc = discoveredPc(deviceName = null)

        val item = ChoosePcMenuStructure().getMenuItems(listOf(pc)) {}.single()

        assertEquals("Switchify PC", item.userProvidedText)
    }

    @Test
    fun fallsBackToDisplayNameWhenBluetoothDeviceNameIsBlank() {
        val pc = discoveredPc(deviceName = " ")

        val item = ChoosePcMenuStructure().getMenuItems(listOf(pc)) {}.single()

        assertEquals("Switchify PC", item.userProvidedText)
    }

    @Test
    fun usesStatusDisplayNameForMacOSInsteadOfGenericBluetoothName() {
        val pc = discoveredPc(
            deviceName = "Mac",
            displayName = "Owen’s Mac Studio",
            platform = PcPlatform.MacOS
        )

        val item = ChoosePcMenuStructure().getMenuItems(listOf(pc)) {}.single()

        assertEquals("Owen’s Mac Studio", item.userProvidedText)
    }

    private fun discoveredPc(
        deviceName: String?,
        displayName: String = "Switchify PC",
        platform: PcPlatform? = null
    ): DiscoveredPc {
        return DiscoveredPc(
            serviceName = displayName,
            desktopId = "desktop-1",
            bluetoothEndpoint = PcBluetoothEndpoint(
                deviceAddress = "AA:BB:CC:DD:EE:FF",
                deviceName = deviceName,
                desktopId = "desktop-1",
                displayName = displayName,
                platform = platform
            )
        )
    }
}
