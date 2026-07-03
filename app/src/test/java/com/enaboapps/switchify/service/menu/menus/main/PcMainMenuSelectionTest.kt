package com.enaboapps.switchify.service.menu.menus.main

import com.enaboapps.switchify.pc.DiscoveredPc
import com.enaboapps.switchify.pc.PcBluetoothEndpoint
import com.enaboapps.switchify.pc.PcDefaultPcPreference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PcMainMenuSelectionTest {
    private val pc = discoveredPc("desktop-1", "Switchify PC")
    private val secondPc = discoveredPc("desktop-2", "Office PC")

    @Test
    fun emptyDiscoveredListReturnsNoPcFound() {
        val selection = selectPcForMainMenu(
            emptyList(),
            PcDefaultPcPreference.SpecificPc("desktop-1"),
            null
        )

        assertEquals(PcMainMenuSelection.NoPcFound, selection)
    }

    @Test
    fun singleDiscoveredPcConnectsToSinglePc() {
        val selection = selectPcForMainMenu(
            listOf(pc),
            PcDefaultPcPreference.LastConnection,
            null
        )

        assertEquals(PcMainMenuSelection.Connect(pc), selection)
    }

    @Test
    fun multipleDiscoveredPcsWithSpecificDefaultPresentConnectsToDefault() {
        val selection = selectPcForMainMenu(
            listOf(pc, secondPc),
            PcDefaultPcPreference.SpecificPc("desktop-2"),
            null
        )

        assertEquals(PcMainMenuSelection.Connect(secondPc), selection)
    }

    @Test
    fun multipleDiscoveredPcsWithSpecificDefaultAbsentShowsChooser() {
        val selection = selectPcForMainMenu(
            listOf(pc, secondPc),
            PcDefaultPcPreference.SpecificPc("desktop-3"),
            null
        )

        assertTrue(selection is PcMainMenuSelection.ShowChooser)
        assertEquals(listOf(pc, secondPc), (selection as PcMainMenuSelection.ShowChooser).pcs)
    }

    @Test
    fun multipleDiscoveredPcsWithLastConnectionPresentConnectsToLastPc() {
        val selection = selectPcForMainMenu(
            listOf(pc, secondPc),
            PcDefaultPcPreference.LastConnection,
            "desktop-2"
        )

        assertEquals(PcMainMenuSelection.Connect(secondPc), selection)
    }

    @Test
    fun multipleDiscoveredPcsWithLastConnectionAbsentShowsChooser() {
        val selection = selectPcForMainMenu(
            listOf(pc, secondPc),
            PcDefaultPcPreference.LastConnection,
            "desktop-3"
        )

        assertTrue(selection is PcMainMenuSelection.ShowChooser)
        assertEquals(listOf(pc, secondPc), (selection as PcMainMenuSelection.ShowChooser).pcs)
    }

    @Test
    fun multipleDiscoveredPcsWithNullLastConnectionShowsChooser() {
        val selection = selectPcForMainMenu(
            listOf(pc, secondPc),
            PcDefaultPcPreference.LastConnection,
            null
        )

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
