package com.enaboapps.switchify.service.menu.menus.main

import com.enaboapps.switchify.pc.DiscoveredPc
import com.enaboapps.switchify.pc.PcBluetoothEndpoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PcMainMenuSelectionTest {
    private val pc = discoveredPc("desktop-1", "Switchify PC")
    private val secondPc = discoveredPc("desktop-2", "Office PC")

    @Test
    fun emptyDiscoveredListReturnsNoPcFound() {
        val selection = selectPcForMainMenu(emptyList(), "desktop-1")

        assertEquals(PcMainMenuSelection.NoPcFound, selection)
    }

    @Test
    fun singleDiscoveredPcConnectsToSinglePc() {
        val selection = selectPcForMainMenu(listOf(pc), null)

        assertEquals(PcMainMenuSelection.Connect(pc), selection)
    }

    @Test
    fun multipleDiscoveredPcsWithDefaultPresentConnectsToDefault() {
        val selection = selectPcForMainMenu(listOf(pc, secondPc), "desktop-2")

        assertEquals(PcMainMenuSelection.Connect(secondPc), selection)
    }

    @Test
    fun multipleDiscoveredPcsWithDefaultAbsentShowsChooser() {
        val selection = selectPcForMainMenu(listOf(pc, secondPc), "desktop-3")

        assertTrue(selection is PcMainMenuSelection.ShowChooser)
        assertEquals(listOf(pc, secondPc), (selection as PcMainMenuSelection.ShowChooser).pcs)
    }

    @Test
    fun multipleDiscoveredPcsWithNullDefaultShowsChooser() {
        val selection = selectPcForMainMenu(listOf(pc, secondPc), null)

        assertTrue(selection is PcMainMenuSelection.ShowChooser)
        assertEquals(listOf(pc, secondPc), (selection as PcMainMenuSelection.ShowChooser).pcs)
    }

    private fun discoveredPc(desktopId: String, displayName: String): DiscoveredPc {
        return DiscoveredPc(
            serviceName = displayName,
            desktopId = desktopId,
            bluetoothEndpoint = PcBluetoothEndpoint(
                deviceAddress = "AA:BB:CC:DD:EE:FF",
                deviceName = displayName,
                desktopId = desktopId,
                displayName = displayName
            )
        )
    }
}
