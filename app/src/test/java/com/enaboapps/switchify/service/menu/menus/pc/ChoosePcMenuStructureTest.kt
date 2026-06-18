package com.enaboapps.switchify.service.menu.menus.pc

import com.enaboapps.switchify.pc.DiscoveredPc
import com.enaboapps.switchify.pc.PcBluetoothEndpoint
import org.junit.Assert.assertEquals
import org.junit.Test

class ChoosePcMenuStructureTest {
    @Test
    fun usesBluetoothDeviceNameWhenAvailable() {
        val pc = discoveredPc(deviceName = "Office Laptop")

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

    private fun discoveredPc(deviceName: String?): DiscoveredPc {
        return DiscoveredPc(
            serviceName = "Switchify PC",
            desktopId = "desktop-1",
            bluetoothEndpoint = PcBluetoothEndpoint(
                deviceAddress = "AA:BB:CC:DD:EE:FF",
                deviceName = deviceName,
                desktopId = "desktop-1",
                displayName = "Switchify PC"
            )
        )
    }
}
