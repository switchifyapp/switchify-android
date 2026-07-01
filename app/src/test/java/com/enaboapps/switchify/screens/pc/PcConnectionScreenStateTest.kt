package com.enaboapps.switchify.screens.pc

import com.enaboapps.switchify.pc.PcConnectionRowSource
import com.enaboapps.switchify.pc.PcConnectionRowState
import com.enaboapps.switchify.pc.PcConnectionUiState
import com.enaboapps.switchify.pc.PcDiscoveryStatus
import com.enaboapps.switchify.pc.PcRowStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class PcConnectionScreenStateTest {
    @Test
    fun permissionRequiredUsesPermissionOverview() {
        val mode = pcConnectionOverviewMode(
            PcConnectionUiState(permissionRequired = true)
        )

        assertEquals(PcConnectionOverviewMode.PermissionRequired, mode)
    }

    @Test
    fun connectedUsesConnectedOverview() {
        val mode = pcConnectionOverviewMode(
            PcConnectionUiState(
                connectedDesktopId = "desktop-1",
                pcRows = listOf(row())
            )
        )

        assertEquals(PcConnectionOverviewMode.Connected, mode)
    }

    @Test
    fun searchingUsesSearchingOverview() {
        val mode = pcConnectionOverviewMode(
            PcConnectionUiState(isDiscovering = true)
        )

        assertEquals(PcConnectionOverviewMode.Searching, mode)
    }

    @Test
    fun failedDiscoveryUsesFailedOverview() {
        val mode = pcConnectionOverviewMode(
            PcConnectionUiState(discoveryStatus = PcDiscoveryStatus.Failed)
        )

        assertEquals(PcConnectionOverviewMode.Failed, mode)
    }

    @Test
    fun pcRowsUseReadyOverview() {
        val mode = pcConnectionOverviewMode(
            PcConnectionUiState(pcRows = listOf(row()))
        )

        assertEquals(PcConnectionOverviewMode.Ready, mode)
    }

    @Test
    fun savedOnlyRowsUseReadyOverview() {
        val mode = pcConnectionOverviewMode(
            PcConnectionUiState(pcRows = listOf(row(source = PcConnectionRowSource.SavedOnly)))
        )

        assertEquals(PcConnectionOverviewMode.Ready, mode)
    }

    @Test
    fun noPcsUsesEmptyOverview() {
        val mode = pcConnectionOverviewMode(PcConnectionUiState())

        assertEquals(PcConnectionOverviewMode.Empty, mode)
    }

    private fun row(source: PcConnectionRowSource = PcConnectionRowSource.Discovered): PcConnectionRowState {
        return PcConnectionRowState(
            desktopId = "desktop-1",
            title = "Switchify PC",
            summary = "AA:BB:CC:DD:EE:FF",
            source = source,
            status = PcRowStatus.Idle,
            actionText = "Connect",
            enabled = true,
            canRequestAccess = false,
            canConnect = true,
            canUnpair = true,
            canSetDefault = true,
            isDefault = false,
            discoveredPc = null
        )
    }
}
