package com.enaboapps.switchify.pc

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PcServiceConnectionControllerTest {
    private val dispatcher = StandardTestDispatcher()
    private val pc = DiscoveredPc(
        serviceName = "Switchify PC",
        desktopId = "desktop-1",
        bluetoothEndpoint = PcBluetoothEndpoint(
            deviceAddress = "AA:BB:CC:DD:EE:FF",
            deviceName = "Switchify PC",
            desktopId = "desktop-1",
            displayName = "Switchify PC"
        )
    )
    private val secondPc = DiscoveredPc(
        serviceName = "Office PC",
        desktopId = "desktop-2",
        bluetoothEndpoint = PcBluetoothEndpoint(
            deviceAddress = "11:22:33:44:55:66",
            deviceName = "Office PC",
            desktopId = "desktop-2",
            displayName = "Office PC"
        )
    )

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        PcConnectionStateHolder.setDisconnected()
    }

    @After
    fun tearDown() {
        PcConnectionStateHolder.setDisconnected()
        Dispatchers.resetMain()
    }

    @Test
    fun savedTokenReconnectDiscoversPcAndPings() = runTest(dispatcher) {
        val tokens = FakeTokenStore(mutableMapOf("desktop-1" to "token"))
        val connector = FakeConnector(PcPingResult.Connected("AA:BB:CC:DD:EE:FF"))
        val controller = controller(tokens, connector)

        val result = controller.connectOrRequestAccess()

        assertTrue(result is PcServiceConnectResult.Connected)
        assertEquals(1, connector.pingCalls)
        assertEquals(1, connector.openControlSessionCalls)
        assertEquals(0, connector.pairingCalls)
        assertEquals("desktop-1", (PcConnectionStateHolder.connectionState.value as PcConnectionState.Connected).session.desktopId)
        assertEquals("AA:BB:CC:DD:EE:FF", tokens.getLastEndpointId("desktop-1"))
    }

    @Test
    fun disconnectStopsDiscoveryClosesConnectorAndClearsState() = runTest(dispatcher) {
        val discovery = FakeDiscovery(listOf(pc))
        val connector = FakeConnector(PcPingResult.Connected("AA:BB:CC:DD:EE:FF"))
        val controller = controller(FakeTokenStore(mutableMapOf("desktop-1" to "token")), connector, discovery)
        PcConnectionStateHolder.setConnected(PcAuthenticatedSession("desktop-1", "device-1", "AA:BB:CC:DD:EE:FF"), "Switchify PC")

        controller.disconnect()

        assertEquals(1, discovery.stopDiscoveryCalls)
        assertEquals(1, connector.closeCalls)
        assertTrue(PcConnectionStateHolder.connectionState.value is PcConnectionState.Disconnected)
        assertTrue(controller.state.value is PcServiceConnectionState.Disconnected)
    }

    @Test
    fun cleanupDelegatesToDisconnect() = runTest(dispatcher) {
        val discovery = FakeDiscovery(listOf(pc))
        val connector = FakeConnector(PcPingResult.Connected("AA:BB:CC:DD:EE:FF"))
        val controller = controller(FakeTokenStore(mutableMapOf("desktop-1" to "token")), connector, discovery)
        PcConnectionStateHolder.setConnected(PcAuthenticatedSession("desktop-1", "device-1", "AA:BB:CC:DD:EE:FF"), "Switchify PC")

        controller.cleanup()

        assertEquals(1, discovery.stopDiscoveryCalls)
        assertEquals(1, connector.closeCalls)
        assertTrue(PcConnectionStateHolder.connectionState.value is PcConnectionState.Disconnected)
        assertTrue(controller.state.value is PcServiceConnectionState.Disconnected)
    }

    @Test
    fun connectToDoesNotReturnConnectedWhenLiveSessionFails() = runTest(dispatcher) {
        val tokens = FakeTokenStore(mutableMapOf("desktop-1" to "token"))
        val connector = FakeConnector(
            pingResult = PcPingResult.Connected("AA:BB:CC:DD:EE:FF"),
            liveResults = listOf(PcLiveControlResult.Failed())
        )
        val controller = controller(tokens, connector)

        val result = controller.connectTo(pc)

        assertTrue(result is PcServiceConnectResult.Failed)
        assertTrue(PcConnectionStateHolder.connectionState.value !is PcConnectionState.Connected)
        assertTrue(controller.state.value is PcServiceConnectionState.Failed)
    }

    @Test
    fun connectToAuthFailureOpeningLiveSessionKeepsToken() = runTest(dispatcher) {
        val tokens = FakeTokenStore(mutableMapOf("desktop-1" to "token"))
        val connector = FakeConnector(
            pingResult = PcPingResult.Connected("AA:BB:CC:DD:EE:FF"),
            liveResults = List(PC_AUTH_RETRY_ATTEMPTS + 1) { PcLiveControlResult.AuthFailed() }
        )
        val controller = controller(tokens, connector)

        val result = controller.connectTo(pc)

        assertTrue(result is PcServiceConnectResult.Failed)
        assertEquals("token", tokens.getToken("desktop-1"))
        assertTrue(PcConnectionStateHolder.connectionState.value is PcConnectionState.Disconnected)
    }

    @Test
    fun existingLiveSessionReturnsWithoutOpeningAnotherSession() = runTest(dispatcher) {
        val tokens = FakeTokenStore(mutableMapOf("desktop-1" to "token"))
        val connector = FakeConnector(PcPingResult.Connected("AA:BB:CC:DD:EE:FF"))
        val controller = controller(tokens, connector)

        val first = controller.connectTo(pc)
        val second = controller.connectTo(pc)

        assertTrue(first is PcServiceConnectResult.Connected)
        assertTrue(second is PcServiceConnectResult.Connected)
        assertEquals(1, connector.openControlSessionCalls)
    }

    @Test
    fun sendControlCommandUsesLiveConnection() = runTest(dispatcher) {
        val tokens = FakeTokenStore(mutableMapOf("desktop-1" to "token"))
        val connector = FakeConnector(PcPingResult.Connected("AA:BB:CC:DD:EE:FF"))
        val controller = controller(tokens, connector)
        controller.connectTo(pc)

        val result = controller.sendControlCommand(PcControlCommand.LeftClick)

        assertEquals(PcCommandResult.Ack, result)
        assertEquals(listOf(PcControlCommand.LeftClick), connector.liveConnections.single().commands)
        assertTrue(connector.oneShotCommands.isEmpty())
    }

    @Test
    fun sendRealtimeControlCommandUsesLiveConnectionRealtimePath() = runTest(dispatcher) {
        val tokens = FakeTokenStore(mutableMapOf("desktop-1" to "token"))
        val connector = FakeConnector(PcPingResult.Connected("AA:BB:CC:DD:EE:FF"))
        val controller = controller(tokens, connector)
        controller.connectTo(pc)

        val result = controller.sendRealtimeControlCommand(PcControlCommand.Move(4, 5))

        assertEquals(PcCommandResult.Ack, result)
        assertEquals(listOf(PcControlCommand.Move(4, 5)), connector.liveConnections.single().realtimeCommands)
        assertTrue(connector.liveConnections.single().commands.isEmpty())
    }

    @Test
    fun sendControlCommandWithoutLiveConnectionFails() = runTest(dispatcher) {
        val controller = controller(FakeTokenStore(), FakeConnector(PcPingResult.Connected("AA:BB:CC:DD:EE:FF")))

        val result = controller.sendControlCommand(PcControlCommand.LeftClick)

        assertTrue(result is PcCommandResult.Failed)
    }

    @Test
    fun sendRealtimeControlCommandWithoutLiveConnectionFails() = runTest(dispatcher) {
        val controller = controller(FakeTokenStore(), FakeConnector(PcPingResult.Connected("AA:BB:CC:DD:EE:FF")))

        val result = controller.sendRealtimeControlCommand(PcControlCommand.Move(4, 5))

        assertTrue(result is PcCommandResult.Failed)
    }

    @Test
    fun commandAuthFailureKeepsToken() = runTest(dispatcher) {
        val tokens = FakeTokenStore(mutableMapOf("desktop-1" to "token"))
        val connector = FakeConnector(
            pingResult = PcPingResult.Connected("AA:BB:CC:DD:EE:FF"),
            commandResult = PcCommandResult.AuthFailed()
        )
        val controller = controller(tokens, connector)
        controller.connectTo(pc)

        val result = controller.sendControlCommand(PcControlCommand.LeftClick)

        assertTrue(result is PcCommandResult.AuthFailed)
        assertEquals("token", tokens.getToken("desktop-1"))
        assertTrue(PcConnectionStateHolder.connectionState.value is PcConnectionState.Disconnected)
    }

    @Test
    fun onPcUiPausedShutsDownAfterGrace() = runTest(dispatcher) {
        val tokens = FakeTokenStore(mutableMapOf("desktop-1" to "token"))
        val connector = FakeConnector(PcPingResult.Connected("AA:BB:CC:DD:EE:FF"))
        val controller = controller(tokens, connector)
        controller.connectTo(pc)

        controller.onPcUiPaused()
        advanceTimeBy(7_999)
        runCurrent()

        assertEquals(0, connector.liveConnections.single().closeCalls)
        assertTrue(PcConnectionStateHolder.connectionState.value is PcConnectionState.Connected)

        advanceTimeBy(2)
        runCurrent()

        assertEquals(1, connector.liveConnections.single().closeCalls)
        assertEquals(1, connector.closeCalls)
        assertTrue(PcConnectionStateHolder.connectionState.value is PcConnectionState.Disconnected)
    }

    @Test
    fun onPcUiResumedCancelsPauseGrace() = runTest(dispatcher) {
        val tokens = FakeTokenStore(mutableMapOf("desktop-1" to "token"))
        val connector = FakeConnector(PcPingResult.Connected("AA:BB:CC:DD:EE:FF"))
        val controller = controller(tokens, connector)
        controller.connectTo(pc)

        controller.onPcUiPaused()
        advanceTimeBy(4_000)
        controller.onPcUiResumed()
        advanceTimeBy(5_000)
        runCurrent()

        assertEquals(0, connector.liveConnections.single().closeCalls)
        assertTrue(PcConnectionStateHolder.connectionState.value is PcConnectionState.Connected)
        controller.disconnect()
    }

    @Test
    fun heartbeatFailureStartsReconnectInController() = runTest(dispatcher) {
        val tokens = FakeTokenStore(mutableMapOf("desktop-1" to "token"))
        val connector = FakeConnector(
            pingResult = PcPingResult.Connected("AA:BB:CC:DD:EE:FF"),
            healthResults = listOf(PcCommandResult.Failed(), PcCommandResult.Ack)
        )
        val controller = controller(tokens, connector)
        controller.connectTo(pc)
        controller.onPcUiResumed()

        advanceTimeBy(5_001)
        runCurrent()

        assertEquals(2, connector.openControlSessionCalls)
        assertTrue(PcConnectionStateHolder.connectionState.value is PcConnectionState.Connected)
        controller.disconnect()
    }

    @Test
    fun realtimeCommandFailureStartsReconnectInController() = runTest(dispatcher) {
        val tokens = FakeTokenStore(mutableMapOf("desktop-1" to "token"))
        val connector = FakeConnector(
            pingResult = PcPingResult.Connected("AA:BB:CC:DD:EE:FF"),
            realtimeCommandResults = listOf(PcCommandResult.Failed(), PcCommandResult.Ack)
        )
        val controller = controller(tokens, connector)
        controller.connectTo(pc)
        controller.onPcUiResumed()

        val result = controller.sendRealtimeControlCommand(PcControlCommand.Move(4, 5))
        runCurrent()

        assertTrue(result is PcCommandResult.Failed)
        assertEquals(2, connector.openControlSessionCalls)
        assertTrue(PcConnectionStateHolder.connectionState.value is PcConnectionState.Connected)
        assertEquals(listOf(PcControlCloseReason.UnexpectedDisconnect), connector.liveConnections.first().closeReasons)
        controller.disconnect()
    }

    @Test
    fun heartbeatAuthFailureKeepsToken() = runTest(dispatcher) {
        val tokens = FakeTokenStore(mutableMapOf("desktop-1" to "token"))
        val connector = FakeConnector(
            pingResult = PcPingResult.Connected("AA:BB:CC:DD:EE:FF"),
            healthResults = listOf(PcCommandResult.AuthFailed())
        )
        val controller = controller(tokens, connector)
        controller.connectTo(pc)
        controller.onPcUiResumed()

        advanceTimeBy(5_001)
        runCurrent()

        assertEquals("token", tokens.getToken("desktop-1"))
        assertTrue(PcConnectionStateHolder.connectionState.value is PcConnectionState.Disconnected)
        assertTrue(controller.state.value is PcServiceConnectionState.Failed)
    }

    @Test
    fun reconnectAuthFailureKeepsToken() = runTest(dispatcher) {
        val tokens = FakeTokenStore(mutableMapOf("desktop-1" to "token"))
        val connector = FakeConnector(
            pingResult = PcPingResult.Connected("AA:BB:CC:DD:EE:FF"),
            liveResults = listOf(
                PcLiveControlResult.Connected(FakeLiveConnection()),
                PcLiveControlResult.AuthFailed()
            )
        )
        val controller = controller(tokens, connector)
        controller.connectTo(pc)
        controller.onPcUiResumed()
        runCurrent()

        connector.liveConnections.single().eventsFlow.tryEmit(PcControlConnectionEvent.Disconnected)
        runCurrent()

        assertEquals("token", tokens.getToken("desktop-1"))
        assertTrue(PcConnectionStateHolder.connectionState.value is PcConnectionState.Disconnected)
        assertTrue(controller.state.value is PcServiceConnectionState.Failed)
    }

    @Test
    fun bluetoothSavedTokenReconnectUsesBluetoothSession() = runTest(dispatcher) {
        val tokens = FakeTokenStore(mutableMapOf("desktop-1" to "token"))
        val connector = FakeConnector(PcPingResult.Connected("AA:BB:CC:DD:EE:FF"))
        val controller = controller(tokens, connector, FakeDiscovery(listOf(pc)))

        val result = controller.connectOrRequestAccess()

        assertTrue(result is PcServiceConnectResult.Connected)
        val session = (PcConnectionStateHolder.connectionState.value as PcConnectionState.Connected).session
        assertEquals(PcTransport.Bluetooth, session.transport)
        assertEquals("AA:BB:CC:DD:EE:FF", session.endpointId)
        assertEquals("AA:BB:CC:DD:EE:FF", tokens.getLastEndpointId("desktop-1"))
    }

    @Test
    fun missingTokenRequestsApprovalThenPings() = runTest(dispatcher) {
        val tokens = FakeTokenStore()
        val connector = FakeConnector(
            pingResult = PcPingResult.Connected("AA:BB:CC:DD:EE:FF"),
            pairingResult = PcPairingResult.Paired("desktop-1", "new-token", "AA:BB:CC:DD:EE:FF")
        )
        val controller = controller(tokens, connector)
        var approvalCode: PcApprovalCodeState? = null

        val result = controller.connectOrRequestAccess { approvalCode = it }

        assertTrue(result is PcServiceConnectResult.Connected)
        assertEquals(PcApprovalCodeState("Switchify PC", "215918"), approvalCode)
        assertEquals("nonce-1", connector.requestNonces.single())
        assertEquals(1, connector.pairingCalls)
        assertEquals(0, connector.pingCalls)
        assertEquals(1, connector.openControlSessionCalls)
        assertEquals("new-token", tokens.getToken("desktop-1"))
        assertTrue(PcConnectionStateHolder.connectionState.value is PcConnectionState.Connected)
    }

    @Test
    fun invalidSavedTokenKeepsTokenAndDisconnects() = runTest(dispatcher) {
        val tokens = FakeTokenStore(mutableMapOf("desktop-1" to "token"))
        val connector = FakeConnector(PcPingResult.AuthFailed())
        val controller = controller(tokens, connector)

        val result = controller.connectOrRequestAccess()

        assertTrue(result is PcServiceConnectResult.Failed)
        assertEquals(PcErrorReason.AuthExpired, (result as PcServiceConnectResult.Failed).reason)
        assertEquals(PC_AUTH_RETRY_ATTEMPTS, connector.pingCalls)
        assertEquals("token", tokens.getToken("desktop-1"))
        assertTrue(PcConnectionStateHolder.connectionState.value is PcConnectionState.Disconnected)
    }

    @Test
    fun savedTokenAuthFailureThenSuccessDoesNotClearToken() = runTest(dispatcher) {
        val tokens = FakeTokenStore(mutableMapOf("desktop-1" to "token"))
        val connector = FakeConnector(
            pingResult = PcPingResult.AuthFailed(),
            pingResults = List(PC_AUTH_RETRY_ATTEMPTS - 1) { PcPingResult.AuthFailed() } +
                PcPingResult.Connected("AA:BB:CC:DD:EE:FF")
        )
        val controller = controller(tokens, connector)

        val result = controller.connectOrRequestAccess()

        assertTrue(result is PcServiceConnectResult.Connected)
        assertEquals(PC_AUTH_RETRY_ATTEMPTS, connector.pingCalls)
        assertEquals(0, connector.pairingCalls)
        assertEquals("token", tokens.getToken("desktop-1"))
    }

    @Test
    fun savedTokenNonAuthFailureDoesNotClearToken() = runTest(dispatcher) {
        val tokens = FakeTokenStore(mutableMapOf("desktop-1" to "token"))
        val connector = FakeConnector(PcPingResult.Failed(PcErrorReason.Unreachable, "Found PC, but could not connect."))
        val controller = controller(tokens, connector)

        val result = controller.connectOrRequestAccess()

        assertTrue(result is PcServiceConnectResult.Failed)
        assertEquals(1, connector.pingCalls)
        assertEquals("token", tokens.getToken("desktop-1"))
    }

    @Test
    fun discoverPcsGathersLateResolvingPc() = runTest(dispatcher) {
        val discovery = FakeDiscovery(listOf(pc))
        val controller = controller(FakeTokenStore(), FakeConnector(PcPingResult.AuthFailed()), discovery)
        launch {
            delay(500)
            discovery.pcs.value = listOf(pc, secondPc)
        }

        val discovered = controller.discoverPcs()

        assertEquals(listOf(pc, secondPc), discovered)
    }

    @Test
    fun discoverPcsReturnsEmptyWhenNothingResolves() = runTest(dispatcher) {
        val discovery = FakeDiscovery(emptyList())
        val controller = controller(FakeTokenStore(), FakeConnector(PcPingResult.AuthFailed()), discovery)

        val discovered = controller.discoverPcs()

        assertTrue(discovered.isEmpty())
    }

    @Test
    fun discoverPairedPcsReturnsOnlyDiscoveredPcsWithToken() = runTest(dispatcher) {
        val tokens = FakeTokenStore(mutableMapOf("desktop-1" to "token"))
        val controller = controller(
            tokens,
            FakeConnector(PcPingResult.AuthFailed()),
            FakeDiscovery(listOf(pc, secondPc))
        )

        val discovered = controller.discoverPairedPcs()

        assertEquals(listOf(pc), discovered)
    }

    @Test
    fun discoverPairedPcsReturnsEmptyWhenNoDiscoveredPcHasToken() = runTest(dispatcher) {
        val controller = controller(
            FakeTokenStore(),
            FakeConnector(PcPingResult.AuthFailed()),
            FakeDiscovery(listOf(pc, secondPc))
        )

        val discovered = controller.discoverPairedPcs()

        assertTrue(discovered.isEmpty())
    }

    @Test
    fun discoverPairedPcsIgnoresSavedPairingsThatAreNotDiscovered() = runTest(dispatcher) {
        val tokens = FakeTokenStore(
            mutableMapOf(
                "desktop-1" to "token",
                "desktop-2" to "token-2"
            )
        )
        val controller = controller(
            tokens,
            FakeConnector(PcPingResult.AuthFailed()),
            FakeDiscovery(listOf(pc))
        )

        val discovered = controller.discoverPairedPcs()

        assertEquals(listOf(pc), discovered)
    }

    @Test
    fun connectToUsesSavedTokenWithoutPairing() = runTest(dispatcher) {
        val tokens = FakeTokenStore(mutableMapOf("desktop-1" to "token"))
        val connector = FakeConnector(PcPingResult.Connected("AA:BB:CC:DD:EE:FF"))
        val controller = controller(tokens, connector)

        val result = controller.connectTo(pc)

        assertTrue(result is PcServiceConnectResult.Connected)
        assertEquals(1, connector.pingCalls)
        assertEquals(0, connector.pairingCalls)
        assertEquals("desktop-1", (PcConnectionStateHolder.connectionState.value as PcConnectionState.Connected).session.desktopId)
    }

    @Test
    fun connectToWithSavedTokenRecordsLastConnectedPc() = runTest(dispatcher) {
        val tokens = FakeTokenStore(mutableMapOf("desktop-1" to "token"))
        val connector = FakeConnector(PcPingResult.Connected("AA:BB:CC:DD:EE:FF"))
        val controller = controller(tokens, connector)

        controller.connectTo(pc)

        assertEquals("desktop-1", tokens.getLastConnectedDesktopId())
    }

    @Test
    fun connectToPairingSuccessRecordsLastConnectedPc() = runTest(dispatcher) {
        val tokens = FakeTokenStore()
        val connector = FakeConnector(
            pingResult = PcPingResult.Connected("AA:BB:CC:DD:EE:FF"),
            pairingResult = PcPairingResult.Paired("desktop-1", "token", "AA:BB:CC:DD:EE:FF")
        )
        val controller = controller(tokens, connector)

        controller.connectTo(pc)

        assertEquals("desktop-1", tokens.getLastConnectedDesktopId())
    }

    @Test
    fun failedConnectionDoesNotRecordLastConnectedPc() = runTest(dispatcher) {
        val tokens = FakeTokenStore(mutableMapOf("desktop-1" to "token"))
        val connector = FakeConnector(PcPingResult.Failed(PcErrorReason.Unreachable, "Found PC, but could not connect."))
        val controller = controller(tokens, connector)

        controller.connectTo(pc)

        assertNull(tokens.getLastConnectedDesktopId())
    }

    @Test
    fun connectToStoresControlDeviceName() = runTest(dispatcher) {
        val pcWithDeviceName = pc.copy(
            serviceName = "Switchify PC",
            bluetoothEndpoint = pc.bluetoothEndpoint?.copy(deviceName = "Oliver Laptop")
        )
        val tokens = FakeTokenStore(mutableMapOf("desktop-1" to "token"))
        val connector = FakeConnector(PcPingResult.Connected("AA:BB:CC:DD:EE:FF"))
        val controller = controller(tokens, connector, FakeDiscovery(listOf(pcWithDeviceName)))

        val result = controller.connectTo(pcWithDeviceName)

        assertEquals("Oliver Laptop", controller.currentControlDeviceName())
        assertEquals("Oliver Laptop", (result as PcServiceConnectResult.Connected).displayName)
        assertEquals("Oliver Laptop", tokens.getServiceName("desktop-1"))
    }

    @Test
    fun pairingUsesFriendlyControlDeviceName() = runTest(dispatcher) {
        val pcWithDeviceName = pc.copy(
            serviceName = "Switchify PC",
            bluetoothEndpoint = pc.bluetoothEndpoint?.copy(deviceName = "Oliver Laptop")
        )
        val tokens = FakeTokenStore()
        val connector = FakeConnector(
            pingResult = PcPingResult.Connected("AA:BB:CC:DD:EE:FF"),
            pairingResult = PcPairingResult.Paired("desktop-1", "token", "AA:BB:CC:DD:EE:FF")
        )
        val controller = controller(tokens, connector, FakeDiscovery(listOf(pcWithDeviceName)))
        var approvalCode: PcApprovalCodeState? = null

        val result = controller.connectTo(pcWithDeviceName) { approvalCode = it }

        assertEquals("Oliver Laptop", approvalCode?.pcName)
        assertEquals("Oliver Laptop", (result as PcServiceConnectResult.Connected).displayName)
        assertEquals("Oliver Laptop", tokens.getServiceName("desktop-1"))
    }

    @Test
    fun disconnectClearsControlDeviceName() = runTest(dispatcher) {
        val pcWithDeviceName = pc.copy(
            serviceName = "Switchify PC",
            bluetoothEndpoint = pc.bluetoothEndpoint?.copy(deviceName = "Oliver Laptop")
        )
        val tokens = FakeTokenStore(mutableMapOf("desktop-1" to "token"))
        val connector = FakeConnector(PcPingResult.Connected("AA:BB:CC:DD:EE:FF"))
        val controller = controller(tokens, connector, FakeDiscovery(listOf(pcWithDeviceName)))

        controller.connectTo(pcWithDeviceName)
        controller.disconnect()

        assertNull(controller.currentControlDeviceName())
    }

    @Test
    fun connectToExpiredTokenFallsThroughToPairing() = runTest(dispatcher) {
        val tokens = FakeTokenStore(mutableMapOf("desktop-1" to "old-token"))
        val connector = FakeConnector(
            pingResult = PcPingResult.AuthFailed(),
            pairingResult = PcPairingResult.Paired("desktop-1", "new-token", "AA:BB:CC:DD:EE:FF"),
            pingResultsByToken = mapOf(
                "old-token" to PcPingResult.AuthFailed(),
                "new-token" to PcPingResult.Connected("AA:BB:CC:DD:EE:FF")
            )
        )
        val controller = controller(tokens, connector)
        var approvalCode: PcApprovalCodeState? = null

        val result = controller.connectTo(pc) { approvalCode = it }

        assertTrue(result is PcServiceConnectResult.Connected)
        assertEquals("Switchify PC", approvalCode?.pcName)
        assertEquals(1, connector.pairingCalls)
        assertEquals(PC_AUTH_RETRY_ATTEMPTS, connector.pingCalls)
        assertEquals(1, connector.openControlSessionCalls)
        assertEquals("new-token", tokens.getToken("desktop-1"))
        assertTrue(PcConnectionStateHolder.connectionState.value is PcConnectionState.Connected)
    }

    @Test
    fun connectToMissingTokenPairsDirectly() = runTest(dispatcher) {
        val tokens = FakeTokenStore()
        val connector = FakeConnector(
            pingResult = PcPingResult.Connected("11:22:33:44:55:66"),
            pairingResult = PcPairingResult.Paired("desktop-2", "token-2", "11:22:33:44:55:66")
        )
        val controller = controller(tokens, connector)

        val result = controller.connectTo(secondPc)

        assertTrue(result is PcServiceConnectResult.Connected)
        assertEquals("Office PC", (result as PcServiceConnectResult.Connected).displayName)
        assertEquals(1, connector.pairingCalls)
        assertEquals("token-2", tokens.getToken("desktop-2"))
    }

    @Test
    fun pairingFallsBackToNextPcWhenUnreachable() = runTest(dispatcher) {
        val tokens = FakeTokenStore()
        val connector = FakeConnector(
            pingResult = PcPingResult.Connected("11:22:33:44:55:66"),
            pairingResultsByDesktop = mapOf(
                "desktop-1" to PcPairingResult.Failed(PcErrorReason.Unreachable, "Could not reach this PC."),
                "desktop-2" to PcPairingResult.Paired("desktop-2", "token-2", "11:22:33:44:55:66")
            )
        )
        val controller = controller(tokens, connector, FakeDiscovery(listOf(pc, secondPc)))

        val result = controller.connectOrRequestAccess()

        assertTrue(result is PcServiceConnectResult.Connected)
        assertEquals("Office PC", (result as PcServiceConnectResult.Connected).displayName)
        assertEquals(2, connector.pairingCalls)
        assertEquals("token-2", tokens.getToken("desktop-2"))
    }

    @Test
    fun pairingRejectionDoesNotFallBackToNextPc() = runTest(dispatcher) {
        val tokens = FakeTokenStore()
        val connector = FakeConnector(
            pingResult = PcPingResult.Connected("11:22:33:44:55:66"),
            pairingResultsByDesktop = mapOf(
                "desktop-1" to PcPairingResult.Failed(PcErrorReason.PairingRejected, "Request rejected."),
                "desktop-2" to PcPairingResult.Paired("desktop-2", "token-2", "11:22:33:44:55:66")
            )
        )
        val controller = controller(tokens, connector, FakeDiscovery(listOf(pc, secondPc)))

        val result = controller.connectOrRequestAccess()

        assertTrue(result is PcServiceConnectResult.Failed)
        assertEquals(PcErrorReason.PairingRejected, (result as PcServiceConnectResult.Failed).reason)
        assertEquals(1, connector.pairingCalls)
        assertNull(tokens.getToken("desktop-2"))
    }

    private fun controller(
        tokens: FakeTokenStore,
        connector: FakeConnector,
        discovery: FakeDiscovery = FakeDiscovery(listOf(pc))
    ): PcServiceConnectionController {
        return PcServiceConnectionController(
            context = null,
            scope = kotlinx.coroutines.CoroutineScope(dispatcher),
            discovery = discovery,
            tokenStore = tokens,
            identityRepository = FakeIdentity,
            connector = connector,
            requestNonceProvider = { "nonce-1" }
        )
    }

    private class FakeDiscovery(initialPcs: List<DiscoveredPc>) : PcDiscovery {
        override val pcs = MutableStateFlow(initialPcs)
        override val status = MutableStateFlow(PcDiscoveryStatus.Found)
        var stopDiscoveryCalls = 0
        override fun startDiscovery() = Unit
        override fun stopDiscovery() {
            stopDiscoveryCalls++
        }
    }

    private class FakeTokenStore(
        private val tokens: MutableMap<String, String> = mutableMapOf()
    ) : PcPairingTokenStore {
        private val lastEndpointIds = mutableMapOf<String, String>()
        private val serviceNames = mutableMapOf<String, String>()
        private var defaultDesktopId: String? = null
        private var lastConnectedDesktopId: String? = null

        override fun getToken(desktopId: String): String? = tokens[desktopId]

        override fun saveToken(desktopId: String, token: String, lastEndpointId: String, serviceName: String?) {
            tokens[desktopId] = token
            lastEndpointIds[desktopId] = lastEndpointId
            if (!serviceName.isNullOrBlank()) serviceNames[desktopId] = serviceName
        }

        override fun clearToken(desktopId: String) {
            tokens.remove(desktopId)
            lastEndpointIds.remove(desktopId)
            serviceNames.remove(desktopId)
            if (defaultDesktopId == desktopId) defaultDesktopId = null
            if (lastConnectedDesktopId == desktopId) lastConnectedDesktopId = null
        }

        override fun listPairings(): List<PcStoredPairing> {
            return tokens.keys.map { desktopId ->
                PcStoredPairing(
                    desktopId = desktopId,
                    serviceName = serviceNames[desktopId],
                    lastEndpointId = lastEndpointIds[desktopId]
                )
            }
        }

        override fun getLastEndpointId(desktopId: String): String? = lastEndpointIds[desktopId]
        override fun getServiceName(desktopId: String): String? = serviceNames[desktopId]
        override fun getDefaultDesktopId(): String? {
            val desktopId = defaultDesktopId ?: return null
            if (tokens.containsKey(desktopId)) return desktopId
            defaultDesktopId = null
            return null
        }

        override fun setDefaultDesktopId(desktopId: String) {
            if (tokens.containsKey(desktopId)) defaultDesktopId = desktopId
        }

        override fun clearDefaultDesktopId() {
            defaultDesktopId = null
        }

        override fun getLastConnectedDesktopId(): String? {
            val desktopId = lastConnectedDesktopId ?: return null
            if (tokens.containsKey(desktopId)) return desktopId
            lastConnectedDesktopId = null
            return null
        }

        override fun recordSuccessfulConnection(desktopId: String) {
            if (tokens.containsKey(desktopId)) lastConnectedDesktopId = desktopId
        }
    }

    private object FakeIdentity : PcDeviceIdentity {
        override fun getDeviceId(): String = "device-1"
        override fun getDeviceName(): String = "Android phone"
    }

    private class FakeConnector(
        private val pingResult: PcPingResult,
        private val pairingResult: PcPairingResult = PcPairingResult.Failed(PcErrorReason.Failed, "unused"),
        private val pingResultsByToken: Map<String, PcPingResult> = emptyMap(),
        private val pairingResultsByDesktop: Map<String, PcPairingResult> = emptyMap(),
        private val healthResults: List<PcCommandResult> = emptyList(),
        private val liveResults: List<PcLiveControlResult> = emptyList(),
        private val commandResult: PcCommandResult = PcCommandResult.Ack,
        private val realtimeCommandResults: List<PcCommandResult> = emptyList(),
        pingResults: List<PcPingResult> = emptyList()
    ) : PcConnector {
        private val queuedPingResults = ArrayDeque(pingResults)
        private val queuedHealthResults = ArrayDeque(healthResults)
        private val queuedLiveResults = ArrayDeque(liveResults)
        private val queuedRealtimeCommandResults = ArrayDeque(realtimeCommandResults)
        var pingCalls = 0
        var pairingCalls = 0
        var openControlSessionCalls = 0
        var closeCalls = 0
        val requestNonces = mutableListOf<String>()
        val liveConnections = mutableListOf<FakeLiveConnection>()
        val oneShotCommands = mutableListOf<PcControlCommand>()

        override suspend fun requestApproval(pc: DiscoveredPc, requestNonce: String): PcPairingResult {
            pairingCalls++
            requestNonces.add(requestNonce)
            return pairingResultsByDesktop[pc.desktopId] ?: pairingResult
        }

        override suspend fun authenticatedPing(pc: DiscoveredPc, token: String): PcPingResult {
            pingCalls++
            queuedPingResults.removeFirstOrNull()?.let { return it }
            return pingResultsByToken[token] ?: pingResult
        }

        override suspend fun openControlSession(session: PcAuthenticatedSession): PcLiveControlResult {
            openControlSessionCalls++
            queuedLiveResults.removeFirstOrNull()?.let {
                if (it is PcLiveControlResult.Connected && it.connection is FakeLiveConnection) {
                    liveConnections += it.connection
                }
                return it
            }
            val connection = FakeLiveConnection(
                onHealth = { queuedHealthResults.removeFirstOrNull() ?: PcCommandResult.Ack },
                onCommand = { commandResult },
                onRealtimeCommand = { queuedRealtimeCommandResults.removeFirstOrNull() ?: PcCommandResult.Ack }
            )
            liveConnections += connection
            return PcLiveControlResult.Connected(connection)
        }

        override suspend fun sendCommand(session: PcAuthenticatedSession, command: PcControlCommand): PcCommandResult {
            oneShotCommands += command
            return PcCommandResult.Ack
        }

        override fun close() {
            closeCalls++
        }
    }

    private class FakeLiveConnection(
        private val onHealth: suspend () -> PcCommandResult = { PcCommandResult.Ack },
        private val onCommand: suspend () -> PcCommandResult = { PcCommandResult.Ack },
        private val onRealtimeCommand: suspend () -> PcCommandResult = { PcCommandResult.Ack }
    ) : PcControlConnection {
        override val pointerProfile: PcPointerMovementProfile? = null
        val eventsFlow = MutableSharedFlow<PcControlConnectionEvent>(extraBufferCapacity = 8)
        override val connectionEvents: Flow<PcControlConnectionEvent> = eventsFlow
        var closeCalls = 0
        val commands = mutableListOf<PcControlCommand>()
        val realtimeCommands = mutableListOf<PcControlCommand>()
        val closeReasons = mutableListOf<PcControlCloseReason>()

        override suspend fun checkHealth(): PcCommandResult {
            return onHealth()
        }

        override suspend fun sendCommand(command: PcControlCommand): PcCommandResult {
            commands += command
            return onCommand()
        }

        override suspend fun sendRealtimeCommand(command: PcControlCommand): PcCommandResult {
            realtimeCommands += command
            return onRealtimeCommand()
        }

        override fun close(reason: PcControlCloseReason) {
            closeCalls++
            closeReasons += reason
        }
    }
}
