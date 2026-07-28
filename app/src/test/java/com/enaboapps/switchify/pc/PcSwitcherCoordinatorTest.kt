package com.enaboapps.switchify.pc

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PcSwitcherCoordinatorTest {
    private val firstPc = pc("first", "First PC")
    private val secondPc = pc("second", "Second PC")

    @Test
    fun openPreparesBeforeShowingAndDiscoversPairedPcs() = runTest {
        val preparation = CompletableDeferred<Unit>()
        val host = FakeHost(listOf(firstPc, secondPc), connectedPc = firstPc)
        val coordinator = PcSwitcherCoordinator(
            host = host,
            scope = backgroundScope,
            beforeOpen = { preparation.await() }
        )

        coordinator.open()
        runCurrent()

        assertTrue(coordinator.state.value.isPreparing)
        assertFalse(coordinator.state.value.visible)

        preparation.complete(Unit)
        runCurrent()

        assertTrue(coordinator.state.value.visible)
        assertEquals(listOf("first", "second"), coordinator.state.value.rows.map { it.desktopId })
        assertTrue(coordinator.state.value.rows.first().connected)
    }

    @Test
    fun successfulSwitchRunsLifecycleInOrderAndClosesChooser() = runTest {
        val events = mutableListOf<String>()
        val host = FakeHost(listOf(firstPc, secondPc), connectedPc = firstPc) {
            events += "connect"
        }
        val coordinator = PcSwitcherCoordinator(
            host = host,
            scope = backgroundScope,
            beforeSwitch = { events += "prepare" },
            afterSwitch = { events += "connected" }
        )
        coordinator.open()
        runCurrent()

        coordinator.switchTo("second")
        runCurrent()

        assertEquals(listOf("prepare", "connect", "connected"), events)
        assertFalse(coordinator.state.value.visible)
        assertEquals("Second PC", coordinator.state.value.connectedDisplayName)
        assertNull(coordinator.state.value.switchingDesktopId)
    }

    @Test
    fun selectingCurrentPcDismissesWithoutConnecting() = runTest {
        val host = FakeHost(listOf(firstPc), connectedPc = firstPc)
        val coordinator = PcSwitcherCoordinator(host, backgroundScope)
        coordinator.open()
        runCurrent()

        coordinator.switchTo("first")
        runCurrent()

        assertFalse(coordinator.state.value.visible)
        assertEquals(0, host.connectCount)
    }

    @Test
    fun failedSwitchKeepsChooserOpenAndReportsFailure() = runTest {
        val host = FakeHost(
            pcs = listOf(firstPc, secondPc),
            connectedPc = firstPc,
            result = PcServiceConnectResult.Failed(PcErrorReason.Failed, "Could not connect.")
        )
        val coordinator = PcSwitcherCoordinator(host, backgroundScope)
        coordinator.open()
        runCurrent()

        coordinator.switchTo("second")
        runCurrent()

        assertTrue(coordinator.state.value.visible)
        assertEquals("Could not connect.", coordinator.state.value.message)
        assertNull(coordinator.state.value.switchingDesktopId)
    }

    @Test
    fun cancellingPairingInvalidatesLateCompletion() = runTest {
        val connection = CompletableDeferred<PcServiceConnectResult>()
        val host = FakeHost(
            pcs = listOf(firstPc, secondPc),
            connectedPc = firstPc,
            deferredResult = connection
        )
        val coordinator = PcSwitcherCoordinator(host, backgroundScope)
        coordinator.open()
        runCurrent()
        coordinator.switchTo("second")
        runCurrent()

        coordinator.cancelPairing()
        connection.complete(PcServiceConnectResult.Connected(session("second"), "Second PC"))
        runCurrent()

        assertEquals(1, host.cancelCount)
        assertTrue(coordinator.state.value.visible)
        assertNull(coordinator.state.value.switchingDesktopId)
        assertEquals("First PC", coordinator.state.value.connectedDisplayName)
    }

    private inner class FakeHost(
        private val pcs: List<DiscoveredPc>,
        connectedPc: DiscoveredPc?,
        private val result: PcServiceConnectResult? = null,
        private val deferredResult: CompletableDeferred<PcServiceConnectResult>? = null,
        private val onConnect: () -> Unit = {}
    ) : PcSwitcherConnectionHost {
        override val connectionState =
            MutableStateFlow<PcServiceConnectionState>(PcServiceConnectionState.Disconnected)
        private var connected = connectedPc
        var connectCount = 0
        var cancelCount = 0

        override fun currentDesktopId(): String? = connected?.desktopId

        override fun currentDisplayName(): String? = connected?.controlDeviceName

        override suspend fun discoverPairedPcs(): List<DiscoveredPc> = pcs

        override suspend fun connectTo(
            pc: DiscoveredPc,
            onWaitingForApproval: (PcApprovalCodeState) -> Unit
        ): PcServiceConnectResult {
            connectCount++
            onConnect()
            val resolved = deferredResult?.await()
                ?: result
                ?: PcServiceConnectResult.Connected(session(pc.desktopId), pc.controlDeviceName)
            if (resolved is PcServiceConnectResult.Connected) {
                connected = pc
                connectionState.value = PcServiceConnectionState.Connected(
                    resolved.session,
                    resolved.displayName,
                    pointerProfile = null
                )
            }
            return resolved
        }

        override fun cancelConnectionAttempt() {
            cancelCount++
        }
    }

    private fun pc(desktopId: String, name: String) = DiscoveredPc(
        serviceName = name,
        desktopId = desktopId,
        bluetoothEndpoint = PcBluetoothEndpoint(
            deviceAddress = desktopId,
            deviceName = name,
            desktopId = desktopId,
            displayName = name
        )
    )

    private fun session(desktopId: String) =
        PcAuthenticatedSession(desktopId, "android", desktopId)
}
