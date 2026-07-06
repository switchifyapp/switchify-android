package com.enaboapps.switchify.pc

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
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
class PcConnectionViewModelTest {
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
    private val friendlyPc = pc.copy(
        bluetoothEndpoint = pc.bluetoothEndpoint?.copy(deviceName = "Oliver Laptop")
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
        dispatcher.scheduler.advanceUntilIdle()
        Dispatchers.resetMain()
    }

    @Test
    fun savedTokenPathSendsPingWithoutPairingRequest() = runTest(dispatcher) {
        val discovery = FakeDiscovery(listOf(pc))
        val tokens = FakeTokenStore(initialTokens = mutableMapOf("desktop-1" to "token"))
        val connector = FakeConnector(pingResult = PcPingResult.Connected("AA:BB:CC:DD:EE:FF"))
        val viewModel = viewModel(discovery, tokens, connector)
        advanceUntilIdle()

        viewModel.connectWithSavedToken(pc)
        advanceUntilIdle()

        assertEquals(0, connector.requestApprovalCalls)
        assertEquals(1, connector.pingCalls)
        assertEquals("desktop-1", viewModel.uiState.value.connectedDesktopId)
    }

    @Test
    fun bluetoothDiscoveredPcWithNoTokenRequestsAccess() = runTest(dispatcher) {
        val discovery = FakeDiscovery(listOf(pc))
        val tokens = FakeTokenStore()
        val connector = FakeConnector(
            pairingResult = PcPairingResult.Paired("desktop-1", "token", "AA:BB:CC:DD:EE:FF"),
            pingResult = PcPingResult.Connected("AA:BB:CC:DD:EE:FF")
        )
        val viewModel = viewModel(discovery, tokens, connector)
        advanceUntilIdle()

        viewModel.requestAccess(pc)
        advanceUntilIdle()

        assertEquals(1, connector.requestApprovalCalls)
        assertEquals(1, connector.pingCalls)
        assertEquals(PcTransport.Bluetooth, (PcConnectionStateHolder.connectionState.value as PcConnectionState.Connected).session.transport)
        assertEquals("AA:BB:CC:DD:EE:FF", tokens.getLastEndpointId("desktop-1"))
    }

    @Test
    fun bluetoothDiscoveredPcWithSavedTokenPingsWithoutPairing() = runTest(dispatcher) {
        val discovery = FakeDiscovery(listOf(pc))
        val tokens = FakeTokenStore(initialTokens = mutableMapOf("desktop-1" to "token"))
        val connector = FakeConnector(pingResult = PcPingResult.Connected("AA:BB:CC:DD:EE:FF"))
        val viewModel = viewModel(discovery, tokens, connector)
        advanceUntilIdle()

        viewModel.connectWithSavedToken(pc)
        advanceUntilIdle()

        assertEquals(0, connector.requestApprovalCalls)
        assertEquals(1, connector.pingCalls)
        assertEquals(PcTransport.Bluetooth, (PcConnectionStateHolder.connectionState.value as PcConnectionState.Connected).session.transport)
    }

    @Test
    fun missingTokenPathSendsPairingRequestThenPing() = runTest(dispatcher) {
        val discovery = FakeDiscovery(listOf(pc))
        val tokens = FakeTokenStore()
        val connector = FakeConnector(
            pairingResult = PcPairingResult.Paired("desktop-1", "token", "AA:BB:CC:DD:EE:FF"),
            pingResult = PcPingResult.Connected("AA:BB:CC:DD:EE:FF")
        )
        val viewModel = viewModel(discovery, tokens, connector)
        advanceUntilIdle()

        viewModel.requestAccess(pc)
        advanceUntilIdle()

        assertEquals(1, connector.requestApprovalCalls)
        assertEquals(1, connector.pingCalls)
        assertEquals("token", tokens.getToken("desktop-1"))
        assertEquals("desktop-1", viewModel.uiState.value.connectedDesktopId)
    }

    @Test
    fun requestAccessShowsApprovalCodeWhileWaiting() = runTest(dispatcher) {
        val discovery = FakeDiscovery(listOf(pc))
        val tokens = FakeTokenStore()
        val pairingDeferred = CompletableDeferred<PcPairingResult>()
        val connector = FakeConnector(
            pairingDeferred = pairingDeferred,
            pingResult = PcPingResult.Connected("AA:BB:CC:DD:EE:FF")
        )
        val viewModel = viewModel(discovery, tokens, connector, requestNonceProvider = { "nonce-1" })
        advanceUntilIdle()

        viewModel.requestAccess(pc)
        advanceUntilIdle()

        assertEquals("215918", viewModel.uiState.value.approvalCode?.verificationCode)
        assertEquals("Switchify PC", viewModel.uiState.value.approvalCode?.pcName)
        assertEquals("Waiting for approval on your PC...", viewModel.uiState.value.discoveredPcs.first().summary)
        assertEquals("nonce-1", connector.requestNonces.single())

        pairingDeferred.complete(PcPairingResult.Failed(PcErrorReason.PairingRejected, "Request rejected."))
        advanceUntilIdle()
    }

    @Test
    fun startingSecondRequestAccessCancelsFirstAndClearsFirstRow() = runTest(dispatcher) {
        val firstPairing = CompletableDeferred<PcPairingResult>()
        val secondPairing = CompletableDeferred<PcPairingResult>()
        val tokens = FakeTokenStore()
        val connector = FakeConnector(
            pairingDeferredsByDesktop = mapOf(
                "desktop-1" to firstPairing,
                "desktop-2" to secondPairing
            ),
            pingResultsByDesktop = mapOf(
                "desktop-1" to PcPingResult.Connected("AA:BB:CC:DD:EE:FF"),
                "desktop-2" to PcPingResult.Connected("11:22:33:44:55:66")
            )
        )
        val viewModel = viewModel(FakeDiscovery(listOf(pc, secondPc)), tokens, connector, requestNonceProvider = { "nonce-1" })
        advanceUntilIdle()

        viewModel.requestAccess(pc)
        advanceUntilIdle()
        viewModel.requestAccess(secondPc)
        advanceUntilIdle()

        assertEquals(1, connector.closeCalls)
        assertEquals(PcRowStatus.Idle, viewModel.uiState.value.pcRows.first { it.desktopId == "desktop-1" }.status)
        assertEquals(PcRowStatus.WaitingApproval, viewModel.uiState.value.pcRows.first { it.desktopId == "desktop-2" }.status)
        assertEquals("Office PC", viewModel.uiState.value.approvalCode?.pcName)

        firstPairing.complete(PcPairingResult.Paired("desktop-1", "token", "AA:BB:CC:DD:EE:FF"))
        advanceUntilIdle()

        assertNull(tokens.getToken("desktop-1"))
        assertNull(viewModel.uiState.value.connectedDesktopId)

        secondPairing.complete(PcPairingResult.Paired("desktop-2", "token-2", "11:22:33:44:55:66"))
        advanceUntilIdle()

        assertEquals("desktop-2", viewModel.uiState.value.connectedDesktopId)
        assertNull(tokens.getToken("desktop-1"))
        assertEquals("token-2", tokens.getToken("desktop-2"))
    }

    @Test
    fun connectWithSavedTokenTakesOverPendingRequestAccess() = runTest(dispatcher) {
        val firstPairing = CompletableDeferred<PcPairingResult>()
        val tokens = FakeTokenStore(
            initialTokens = mutableMapOf("desktop-2" to "token-2"),
            initialLastEndpointIds = mutableMapOf("desktop-2" to "11:22:33:44:55:66"),
            initialServiceNames = mutableMapOf("desktop-2" to "Office PC")
        )
        val connector = FakeConnector(
            pairingDeferredsByDesktop = mapOf("desktop-1" to firstPairing),
            pingResultsByDesktop = mapOf("desktop-2" to PcPingResult.Connected("11:22:33:44:55:66"))
        )
        val viewModel = viewModel(FakeDiscovery(listOf(pc, secondPc)), tokens, connector, requestNonceProvider = { "nonce-1" })
        advanceUntilIdle()

        viewModel.requestAccess(pc)
        advanceUntilIdle()
        viewModel.connectWithSavedToken(secondPc)
        advanceUntilIdle()

        assertEquals(1, connector.closeCalls)
        assertEquals("desktop-2", viewModel.uiState.value.connectedDesktopId)
        assertEquals(PcRowStatus.Idle, viewModel.uiState.value.pcRows.first { it.desktopId == "desktop-1" }.status)

        firstPairing.complete(PcPairingResult.Paired("desktop-1", "token", "AA:BB:CC:DD:EE:FF"))
        advanceUntilIdle()

        assertNull(tokens.getToken("desktop-1"))
        assertEquals("desktop-2", viewModel.uiState.value.connectedDesktopId)
    }

    @Test
    fun savedPairingConnectionTakesOverPendingRequestAccess() = runTest(dispatcher) {
        val firstPairing = CompletableDeferred<PcPairingResult>()
        val tokens = FakeTokenStore(
            initialTokens = mutableMapOf("desktop-2" to "token-2"),
            initialLastEndpointIds = mutableMapOf("desktop-2" to "11:22:33:44:55:66"),
            initialServiceNames = mutableMapOf("desktop-2" to "Office PC")
        )
        val connector = FakeConnector(
            pairingDeferredsByDesktop = mapOf("desktop-1" to firstPairing),
            pingResultsByDesktop = mapOf("desktop-2" to PcPingResult.Connected("11:22:33:44:55:66"))
        )
        val viewModel = viewModel(FakeDiscovery(listOf(pc)), tokens, connector, requestNonceProvider = { "nonce-1" })
        advanceUntilIdle()

        viewModel.requestAccess(pc)
        advanceUntilIdle()
        viewModel.connectSavedPairing("desktop-2")
        advanceUntilIdle()

        assertEquals(1, connector.closeCalls)
        assertEquals("desktop-2", viewModel.uiState.value.connectedDesktopId)

        firstPairing.complete(PcPairingResult.Paired("desktop-1", "token", "AA:BB:CC:DD:EE:FF"))
        advanceUntilIdle()

        assertNull(tokens.getToken("desktop-1"))
        assertEquals("desktop-2", viewModel.uiState.value.connectedDesktopId)
    }

    @Test
    fun cancelPairingDismissesApprovalAndClearsBusyState() = runTest(dispatcher) {
        val pairingDeferred = CompletableDeferred<PcPairingResult>()
        val connector = FakeConnector(pairingDeferred = pairingDeferred)
        val viewModel = viewModel(FakeDiscovery(listOf(pc)), FakeTokenStore(), connector, requestNonceProvider = { "nonce-1" })
        advanceUntilIdle()

        viewModel.requestAccess(pc)
        advanceUntilIdle()
        viewModel.cancelPairing()
        advanceUntilIdle()

        val row = viewModel.uiState.value.pcRows.single()
        assertNull(viewModel.uiState.value.approvalCode)
        assertEquals(false, viewModel.uiState.value.isBusy)
        assertEquals(PcRowStatus.Idle, row.status)
        assertEquals(true, row.enabled)
        assertEquals(1, connector.closeCalls)
    }

    @Test
    fun cancelPairingDoesNotSaveTokenWhenPairingCompletesLater() = runTest(dispatcher) {
        val pairingDeferred = CompletableDeferred<PcPairingResult>()
        val tokens = FakeTokenStore()
        val viewModel = viewModel(
            FakeDiscovery(listOf(pc)),
            tokens,
            FakeConnector(
                pairingDeferred = pairingDeferred,
                pingResult = PcPingResult.Connected("AA:BB:CC:DD:EE:FF")
            )
        )
        advanceUntilIdle()

        viewModel.requestAccess(pc)
        advanceUntilIdle()
        viewModel.cancelPairing()
        pairingDeferred.complete(PcPairingResult.Paired("desktop-1", "token", "AA:BB:CC:DD:EE:FF"))
        advanceUntilIdle()

        assertNull(tokens.getToken("desktop-1"))
        assertNull(tokens.getLastConnectedDesktopId())
        assertTrue(PcConnectionStateHolder.connectionState.value !is PcConnectionState.Connected)
    }

    @Test
    fun cancelPairingIsNoOpWithoutActivePairing() = runTest(dispatcher) {
        val connector = FakeConnector()
        val viewModel = viewModel(FakeDiscovery(listOf(pc)), FakeTokenStore(), connector)
        advanceUntilIdle()

        viewModel.cancelPairing()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.approvalCode)
        assertEquals(false, viewModel.uiState.value.isBusy)
        assertEquals(PcRowStatus.Idle, viewModel.uiState.value.pcRows.single().status)
        assertEquals(1, connector.closeCalls)
    }

    @Test
    fun stopPcBluetoothStopsDiscoveryAndClosesConnector() = runTest(dispatcher) {
        val discovery = FakeDiscovery(listOf(pc))
        val connector = FakeConnector()
        val viewModel = viewModel(discovery, FakeTokenStore(), connector)
        advanceUntilIdle()

        viewModel.stopPcBluetooth()
        advanceUntilIdle()

        assertEquals(1, discovery.stopDiscoveryCalls)
        assertEquals(1, connector.closeCalls)
        assertTrue(PcConnectionStateHolder.connectionState.value is PcConnectionState.Disconnected)
    }

    @Test
    fun stopPcBluetoothClearsConnectedStateWithoutClearingToken() = runTest(dispatcher) {
        val tokens = FakeTokenStore(initialTokens = mutableMapOf("desktop-1" to "token"))
        val viewModel = viewModel(FakeDiscovery(listOf(pc)), tokens, FakeConnector())
        PcConnectionStateHolder.setConnected(PcAuthenticatedSession("desktop-1", "device-1", "AA:BB:CC:DD:EE:FF"), "Switchify PC")
        advanceUntilIdle()

        viewModel.stopPcBluetooth()
        advanceUntilIdle()

        assertTrue(PcConnectionStateHolder.connectionState.value is PcConnectionState.Disconnected)
        assertEquals("token", tokens.getToken("desktop-1"))
    }

    @Test
    fun stopPcBluetoothDismissesApprovalAndBusyState() = runTest(dispatcher) {
        val discovery = FakeDiscovery(listOf(pc))
        val pairingDeferred = CompletableDeferred<PcPairingResult>()
        val connector = FakeConnector(pairingDeferred = pairingDeferred)
        val viewModel = viewModel(discovery, FakeTokenStore(), connector, requestNonceProvider = { "nonce-1" })
        advanceUntilIdle()

        viewModel.requestAccess(pc)
        advanceUntilIdle()
        assertEquals("215918", viewModel.uiState.value.approvalCode?.verificationCode)

        viewModel.stopPcBluetooth()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.approvalCode)
        assertEquals(false, viewModel.uiState.value.isBusy)
        assertEquals(1, connector.closeCalls)
        pairingDeferred.complete(PcPairingResult.Paired("desktop-1", "token", "AA:BB:CC:DD:EE:FF"))
        advanceUntilIdle()
        assertTrue(PcConnectionStateHolder.connectionState.value is PcConnectionState.Disconnected)
    }

    @Test
    fun pairingSuccessClearsApprovalCode() = runTest(dispatcher) {
        val discovery = FakeDiscovery(listOf(pc))
        val tokens = FakeTokenStore()
        val connector = FakeConnector(
            pairingResult = PcPairingResult.Paired("desktop-1", "token", "AA:BB:CC:DD:EE:FF"),
            pingResult = PcPingResult.Connected("AA:BB:CC:DD:EE:FF")
        )
        val viewModel = viewModel(discovery, tokens, connector, requestNonceProvider = { "nonce-1" })
        advanceUntilIdle()

        viewModel.requestAccess(pc)
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.approvalCode)
        assertEquals("desktop-1", viewModel.uiState.value.connectedDesktopId)
    }

    @Test
    fun pairingFailureClearsApprovalCodeAndShowsMessage() = runTest(dispatcher) {
        val discovery = FakeDiscovery(listOf(pc))
        val tokens = FakeTokenStore()
        val connector = FakeConnector(pairingResult = PcPairingResult.Failed(PcErrorReason.PairingRejected, "Request rejected."))
        val viewModel = viewModel(discovery, tokens, connector, requestNonceProvider = { "nonce-1" })
        advanceUntilIdle()

        viewModel.requestAccess(pc)
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.approvalCode)
        assertEquals("Request rejected.", viewModel.uiState.value.message)
    }

    @Test
    fun savedTokenPathDoesNotShowApprovalCode() = runTest(dispatcher) {
        val discovery = FakeDiscovery(listOf(pc))
        val tokens = FakeTokenStore(initialTokens = mutableMapOf("desktop-1" to "token"))
        val connector = FakeConnector(pingResult = PcPingResult.Connected("AA:BB:CC:DD:EE:FF"))
        val viewModel = viewModel(discovery, tokens, connector, requestNonceProvider = { "nonce-1" })
        advanceUntilIdle()

        viewModel.connectWithSavedToken(pc)
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.approvalCode)
        assertEquals(0, connector.requestApprovalCalls)
    }

    @Test
    fun invalidSavedTokenKeepsTokenAndOffersConnect() = runTest(dispatcher) {
        val discovery = FakeDiscovery(listOf(pc))
        val tokens = FakeTokenStore(initialTokens = mutableMapOf("desktop-1" to "token"))
        val connector = FakeConnector(pingResult = PcPingResult.AuthFailed())
        val viewModel = viewModel(discovery, tokens, connector)
        advanceUntilIdle()

        viewModel.connectWithSavedToken(pc)
        advanceUntilIdle()

        assertEquals(PC_AUTH_RETRY_ATTEMPTS, connector.pingCalls)
        assertEquals("token", tokens.getToken("desktop-1"))
        assertEquals("Connection expired. Request access again.", viewModel.uiState.value.message)
        assertEquals("Connect", viewModel.uiState.value.discoveredPcs.first().actionText)
        assertEquals(true, viewModel.uiState.value.discoveredPcs.first().canUnpair)
    }

    @Test
    fun savedTokenAuthFailureThenSuccessKeepsToken() = runTest(dispatcher) {
        val discovery = FakeDiscovery(listOf(pc))
        val tokens = FakeTokenStore(initialTokens = mutableMapOf("desktop-1" to "token"))
        val connector = FakeConnector(
            pingResults = List(PC_AUTH_RETRY_ATTEMPTS - 1) { PcPingResult.AuthFailed() } +
                PcPingResult.Connected("AA:BB:CC:DD:EE:FF")
        )
        val viewModel = viewModel(discovery, tokens, connector)
        advanceUntilIdle()

        viewModel.connectWithSavedToken(pc)
        advanceUntilIdle()

        assertEquals(PC_AUTH_RETRY_ATTEMPTS, connector.pingCalls)
        assertEquals("token", tokens.getToken("desktop-1"))
        assertEquals("desktop-1", viewModel.uiState.value.connectedDesktopId)
        assertEquals("Connected", viewModel.uiState.value.discoveredPcs.first().actionText)
    }

    @Test
    fun savedTokenNonAuthFailureDoesNotClearToken() = runTest(dispatcher) {
        val discovery = FakeDiscovery(listOf(pc))
        val tokens = FakeTokenStore(initialTokens = mutableMapOf("desktop-1" to "token"))
        val connector = FakeConnector(pingResult = PcPingResult.Failed(PcErrorReason.Unreachable, "Found PC, but could not connect."))
        val viewModel = viewModel(discovery, tokens, connector)
        advanceUntilIdle()

        viewModel.connectWithSavedToken(pc)
        advanceUntilIdle()

        assertEquals(1, connector.pingCalls)
        assertEquals("token", tokens.getToken("desktop-1"))
        assertEquals("Found PC, but could not connect.", viewModel.uiState.value.message)
    }

    @Test
    fun savedPairingsListsStoredPcWhenNotDiscovered() = runTest(dispatcher) {
        val discovery = FakeDiscovery(emptyList())
        val tokens = FakeTokenStore(
            initialTokens = mutableMapOf("desktop-1" to "token"),
            initialLastEndpointIds = mutableMapOf("desktop-1" to "AA:BB:CC:DD:EE:FF"),
            initialServiceNames = mutableMapOf("desktop-1" to "Switchify PC")
        )
        val viewModel = viewModel(discovery, tokens, FakeConnector())
        advanceUntilIdle()

        val savedPairing = viewModel.uiState.value.savedPairings.single()
        assertEquals("desktop-1", savedPairing.desktopId)
        assertEquals("Switchify PC", savedPairing.title)
        assertEquals("AA:BB:CC:DD:EE:FF", savedPairing.summary)
        assertEquals(true, savedPairing.canConnect)
    }

    @Test
    fun savedBluetoothPairingCanReconnectWithoutDiscovery() = runTest(dispatcher) {
        val discovery = FakeDiscovery(emptyList())
        val tokens = FakeTokenStore(
            initialTokens = mutableMapOf("desktop-1" to "token"),
            initialLastEndpointIds = mutableMapOf("desktop-1" to "AA:BB:CC:DD:EE:FF"),
            initialServiceNames = mutableMapOf("desktop-1" to "Switchify PC")
        )
        val connector = FakeConnector(pingResult = PcPingResult.Connected("AA:BB:CC:DD:EE:FF"))
        val viewModel = viewModel(discovery, tokens, connector)
        advanceUntilIdle()

        val savedPairing = viewModel.uiState.value.savedPairings.single()
        assertEquals("AA:BB:CC:DD:EE:FF", savedPairing.summary)
        assertEquals(true, savedPairing.canConnect)

        viewModel.connectSavedPairing("desktop-1")
        advanceUntilIdle()

        assertEquals(1, connector.pingCalls)
        assertEquals(PcTransport.Bluetooth, (PcConnectionStateHolder.connectionState.value as PcConnectionState.Connected).session.transport)
        assertEquals("AA:BB:CC:DD:EE:FF", connector.pingPcs.single().bluetoothEndpoint?.deviceAddress)
    }

    @Test
    fun savedBluetoothPairingAuthFailureRemainsRetryable() = runTest(dispatcher) {
        val discovery = FakeDiscovery(emptyList())
        val tokens = FakeTokenStore(
            initialTokens = mutableMapOf("desktop-1" to "token"),
            initialLastEndpointIds = mutableMapOf("desktop-1" to "AA:BB:CC:DD:EE:FF"),
            initialServiceNames = mutableMapOf("desktop-1" to "Switchify PC")
        )
        val connector = FakeConnector(pingResult = PcPingResult.AuthFailed())
        val viewModel = viewModel(discovery, tokens, connector)
        advanceUntilIdle()

        viewModel.connectSavedPairing("desktop-1")
        advanceUntilIdle()

        assertEquals(PC_AUTH_RETRY_ATTEMPTS, connector.pingCalls)
        assertEquals("token", tokens.getToken("desktop-1"))
        val savedPairing = viewModel.uiState.value.savedPairings.single()
        assertEquals("desktop-1", savedPairing.desktopId)
        assertEquals(true, savedPairing.canConnect)
        assertEquals("Connection expired. Request access again.", viewModel.uiState.value.message)
    }

    @Test
    fun savedPairingsExcludeDiscoveredPc() = runTest(dispatcher) {
        val discovery = FakeDiscovery(listOf(pc))
        val tokens = FakeTokenStore(initialTokens = mutableMapOf("desktop-1" to "token"))
        val viewModel = viewModel(discovery, tokens, FakeConnector())
        advanceUntilIdle()

        assertEquals(true, viewModel.uiState.value.discoveredPcs.first().canUnpair)
        assertEquals(emptyList<PcSavedPairingRowState>(), viewModel.uiState.value.savedPairings)
    }

    @Test
    fun pcRowsIncludesDiscoveredUnpairedPc() = runTest(dispatcher) {
        val discovery = FakeDiscovery(listOf(pc))
        val viewModel = viewModel(discovery, FakeTokenStore(), FakeConnector())
        advanceUntilIdle()

        val row = viewModel.uiState.value.pcRows.single()
        assertEquals("desktop-1", row.desktopId)
        assertEquals(PcConnectionRowSource.Discovered, row.source)
        assertEquals(true, row.canRequestAccess)
        assertEquals(false, row.canSetDefault)
        assertEquals("Request access", row.actionText)
    }

    @Test
    fun pcRowsIncludesDiscoveredPairedPc() = runTest(dispatcher) {
        val discovery = FakeDiscovery(listOf(pc))
        val tokens = FakeTokenStore(initialTokens = mutableMapOf("desktop-1" to "token"))
        val viewModel = viewModel(discovery, tokens, FakeConnector())
        advanceUntilIdle()

        val row = viewModel.uiState.value.pcRows.single()
        assertEquals(PcConnectionRowSource.Discovered, row.source)
        assertEquals(true, row.canConnect)
        assertEquals(true, row.canUnpair)
        assertEquals(true, row.canSetDefault)
    }

    @Test
    fun pcRowsMergesDiscoveredAndSavedPairing() = runTest(dispatcher) {
        val discovery = FakeDiscovery(listOf(pc))
        val tokens = FakeTokenStore(
            initialTokens = mutableMapOf("desktop-1" to "token"),
            initialLastEndpointIds = mutableMapOf("desktop-1" to "AA:BB:CC:DD:EE:FF"),
            initialServiceNames = mutableMapOf("desktop-1" to "Switchify PC")
        )
        val viewModel = viewModel(discovery, tokens, FakeConnector())
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.pcRows.size)
        assertEquals(PcConnectionRowSource.Discovered, viewModel.uiState.value.pcRows.single().source)
    }

    @Test
    fun pcRowsIncludesSavedOnlyPairing() = runTest(dispatcher) {
        val discovery = FakeDiscovery(emptyList())
        val tokens = FakeTokenStore(
            initialTokens = mutableMapOf("desktop-1" to "token"),
            initialLastEndpointIds = mutableMapOf("desktop-1" to "AA:BB:CC:DD:EE:FF"),
            initialServiceNames = mutableMapOf("desktop-1" to "Switchify PC")
        )
        val viewModel = viewModel(discovery, tokens, FakeConnector())
        advanceUntilIdle()

        val row = viewModel.uiState.value.pcRows.single()
        assertEquals(PcConnectionRowSource.SavedOnly, row.source)
        assertEquals(true, row.canConnect)
        assertEquals("AA:BB:CC:DD:EE:FF", row.summary)
    }

    @Test
    fun pcRowsIncludesSavedOnlyPairingWithoutEndpoint() = runTest(dispatcher) {
        val discovery = FakeDiscovery(emptyList())
        val tokens = FakeTokenStore(
            initialTokens = mutableMapOf("desktop-1" to "token"),
            initialServiceNames = mutableMapOf("desktop-1" to "Switchify PC")
        )
        val viewModel = viewModel(discovery, tokens, FakeConnector())
        advanceUntilIdle()

        val row = viewModel.uiState.value.pcRows.single()
        assertEquals(PcConnectionRowSource.SavedOnly, row.source)
        assertEquals(false, row.canConnect)
        assertEquals("Not available", row.summary)
    }

    @Test
    fun pcRowsMarksDefault() = runTest(dispatcher) {
        val discovery = FakeDiscovery(listOf(pc))
        val tokens = FakeTokenStore(
            initialTokens = mutableMapOf("desktop-1" to "token"),
            initialDefaultDesktopId = "desktop-1"
        )
        val viewModel = viewModel(discovery, tokens, FakeConnector())
        advanceUntilIdle()

        assertEquals(true, viewModel.uiState.value.pcRows.single().isDefault)
    }

    @Test
    fun defaultChoicesIncludeLastConnectionFirst() = runTest(dispatcher) {
        val tokens = FakeTokenStore(initialTokens = mutableMapOf("desktop-1" to "token"))
        val viewModel = viewModel(FakeDiscovery(listOf(pc)), tokens, FakeConnector())
        advanceUntilIdle()

        val firstChoice = viewModel.uiState.value.defaultPcChoices.first()
        assertEquals(PcDefaultPcPreference.LastConnection, firstChoice.preference)
        assertEquals("Use last connection", firstChoice.title)
    }

    @Test
    fun defaultChoicesIncludePairedPcs() = runTest(dispatcher) {
        val tokens = FakeTokenStore(
            initialTokens = mutableMapOf("desktop-1" to "token"),
            initialServiceNames = mutableMapOf("desktop-1" to "Switchify PC")
        )
        val viewModel = viewModel(FakeDiscovery(emptyList()), tokens, FakeConnector())
        advanceUntilIdle()

        assertEquals(
            listOf(
                PcDefaultPcPreference.LastConnection,
                PcDefaultPcPreference.SpecificPc("desktop-1")
            ),
            viewModel.uiState.value.defaultPcChoices.map { it.preference }
        )
    }

    @Test
    fun defaultChoicesPreferDiscoveredFriendlyNameOverStoredName() = runTest(dispatcher) {
        val tokens = FakeTokenStore(
            initialTokens = mutableMapOf("desktop-1" to "token"),
            initialServiceNames = mutableMapOf("desktop-1" to "Switchify PC"),
            initialLastConnectedDesktopId = "desktop-1"
        )
        val viewModel = viewModel(FakeDiscovery(listOf(friendlyPc)), tokens, FakeConnector())
        advanceUntilIdle()

        val choices = viewModel.uiState.value.defaultPcChoices
        assertEquals("Currently: Oliver Laptop", choices.first().description)
        assertEquals("Oliver Laptop", choices[1].title)
    }

    @Test
    fun discoveredRowsUseFriendlyControlDeviceName() = runTest(dispatcher) {
        val viewModel = viewModel(FakeDiscovery(listOf(friendlyPc)), FakeTokenStore(), FakeConnector())
        advanceUntilIdle()

        assertEquals("Oliver Laptop", viewModel.uiState.value.pcRows.single().title)
    }

    @Test
    fun defaultPreferenceDefaultsToLastConnection() = runTest(dispatcher) {
        val viewModel = viewModel(FakeDiscovery(emptyList()), FakeTokenStore(), FakeConnector())
        advanceUntilIdle()

        assertEquals(PcDefaultPcPreference.LastConnection, viewModel.uiState.value.defaultPreference)
    }

    @Test
    fun setDefaultPcPreferenceToLastConnectionUpdatesStateAndMessage() = runTest(dispatcher) {
        val tokens = FakeTokenStore(
            initialTokens = mutableMapOf("desktop-1" to "token"),
            initialDefaultDesktopId = "desktop-1"
        )
        val viewModel = viewModel(FakeDiscovery(listOf(pc)), tokens, FakeConnector())
        advanceUntilIdle()

        viewModel.setDefaultPcPreference(PcDefaultPcPreference.LastConnection)
        advanceUntilIdle()

        assertEquals(PcDefaultPcPreference.LastConnection, tokens.getDefaultPcPreference())
        assertEquals(PcDefaultPcPreference.LastConnection, viewModel.uiState.value.defaultPreference)
        assertEquals("Default set to last connection.", viewModel.uiState.value.message)
        assertEquals(false, viewModel.uiState.value.pcRows.single().isDefault)
    }

    @Test
    fun setDefaultPcPreferenceToSpecificPcUpdatesStateAndMessage() = runTest(dispatcher) {
        val tokens = FakeTokenStore(
            initialTokens = mutableMapOf("desktop-1" to "token"),
            initialServiceNames = mutableMapOf("desktop-1" to "Switchify PC")
        )
        val viewModel = viewModel(FakeDiscovery(listOf(pc)), tokens, FakeConnector())
        advanceUntilIdle()

        viewModel.setDefaultPcPreference(PcDefaultPcPreference.SpecificPc("desktop-1"))
        advanceUntilIdle()

        assertEquals(PcDefaultPcPreference.SpecificPc("desktop-1"), tokens.getDefaultPcPreference())
        assertEquals(true, viewModel.uiState.value.pcRows.single().isDefault)
        assertEquals("Default PC set to Switchify PC.", viewModel.uiState.value.message)
    }

    @Test
    fun setDefaultPcPreferenceUsesDiscoveredFriendlyNameInMessage() = runTest(dispatcher) {
        val tokens = FakeTokenStore(
            initialTokens = mutableMapOf("desktop-1" to "token"),
            initialServiceNames = mutableMapOf("desktop-1" to "Switchify PC")
        )
        val viewModel = viewModel(FakeDiscovery(listOf(friendlyPc)), tokens, FakeConnector())
        advanceUntilIdle()

        viewModel.setDefaultPcPreference(PcDefaultPcPreference.SpecificPc("desktop-1"))
        advanceUntilIdle()

        assertEquals("Default PC set to Oliver Laptop.", viewModel.uiState.value.message)
    }

    @Test
    fun successfulPairingRecordsLastConnection() = runTest(dispatcher) {
        val discovery = FakeDiscovery(listOf(friendlyPc))
        val tokens = FakeTokenStore()
        val connector = FakeConnector(
            pairingResult = PcPairingResult.Paired("desktop-1", "token", "AA:BB:CC:DD:EE:FF"),
            pingResult = PcPingResult.Connected("AA:BB:CC:DD:EE:FF")
        )
        val viewModel = viewModel(discovery, tokens, connector)
        advanceUntilIdle()

        viewModel.requestAccess(friendlyPc)
        advanceUntilIdle()

        assertEquals("desktop-1", tokens.getLastConnectedDesktopId())
        assertEquals("Oliver Laptop", tokens.getServiceName("desktop-1"))
    }

    @Test
    fun successfulSavedTokenConnectionRecordsLastConnection() = runTest(dispatcher) {
        val tokens = FakeTokenStore(initialTokens = mutableMapOf("desktop-1" to "token"))
        val viewModel = viewModel(
            FakeDiscovery(listOf(pc)),
            tokens,
            FakeConnector(pingResult = PcPingResult.Connected("AA:BB:CC:DD:EE:FF"))
        )
        advanceUntilIdle()

        viewModel.connectWithSavedToken(pc)
        advanceUntilIdle()

        assertEquals("desktop-1", tokens.getLastConnectedDesktopId())
    }

    @Test
    fun pairedDiscoveredPcCanBeSetAsDefault() = runTest(dispatcher) {
        val discovery = FakeDiscovery(listOf(pc))
        val tokens = FakeTokenStore(initialTokens = mutableMapOf("desktop-1" to "token"))
        val viewModel = viewModel(discovery, tokens, FakeConnector())
        advanceUntilIdle()

        val row = viewModel.uiState.value.discoveredPcs.single()
        assertEquals(true, row.canSetDefault)
        assertEquals(false, row.isDefault)
    }

    @Test
    fun unpairedDiscoveredPcCannotBeSetAsDefault() = runTest(dispatcher) {
        val discovery = FakeDiscovery(listOf(pc))
        val viewModel = viewModel(discovery, FakeTokenStore(), FakeConnector())
        advanceUntilIdle()

        val row = viewModel.uiState.value.discoveredPcs.single()
        assertEquals(false, row.canSetDefault)
        assertEquals(false, row.isDefault)
    }

    @Test
    fun savedPairingCanBeSetAsDefault() = runTest(dispatcher) {
        val discovery = FakeDiscovery(emptyList())
        val tokens = FakeTokenStore(
            initialTokens = mutableMapOf("desktop-1" to "token"),
            initialLastEndpointIds = mutableMapOf("desktop-1" to "AA:BB:CC:DD:EE:FF"),
            initialServiceNames = mutableMapOf("desktop-1" to "Switchify PC")
        )
        val viewModel = viewModel(discovery, tokens, FakeConnector())
        advanceUntilIdle()

        val row = viewModel.uiState.value.savedPairings.single()
        assertEquals(true, row.canSetDefault)
        assertEquals(false, row.isDefault)
    }

    @Test
    fun currentDefaultDiscoveredRowIsMarkedDefault() = runTest(dispatcher) {
        val discovery = FakeDiscovery(listOf(pc))
        val tokens = FakeTokenStore(
            initialTokens = mutableMapOf("desktop-1" to "token"),
            initialDefaultDesktopId = "desktop-1"
        )
        val viewModel = viewModel(discovery, tokens, FakeConnector())
        advanceUntilIdle()

        val row = viewModel.uiState.value.discoveredPcs.single()
        assertEquals(true, row.isDefault)
    }

    @Test
    fun setDefaultPcStoresDefaultAndUpdatesDiscoveredRow() = runTest(dispatcher) {
        val discovery = FakeDiscovery(listOf(pc))
        val tokens = FakeTokenStore(initialTokens = mutableMapOf("desktop-1" to "token"))
        val viewModel = viewModel(discovery, tokens, FakeConnector())
        advanceUntilIdle()

        viewModel.setDefaultPc("desktop-1", "Switchify PC")
        advanceUntilIdle()

        assertEquals("desktop-1", tokens.getDefaultDesktopId())
        assertEquals(true, viewModel.uiState.value.discoveredPcs.single().isDefault)
        assertEquals("Default PC set to Switchify PC.", viewModel.uiState.value.message)
    }

    @Test
    fun setDefaultPcStoresDefaultAndUpdatesSavedPairingRow() = runTest(dispatcher) {
        val discovery = FakeDiscovery(emptyList())
        val tokens = FakeTokenStore(
            initialTokens = mutableMapOf("desktop-1" to "token"),
            initialLastEndpointIds = mutableMapOf("desktop-1" to "AA:BB:CC:DD:EE:FF"),
            initialServiceNames = mutableMapOf("desktop-1" to "Switchify PC")
        )
        val viewModel = viewModel(discovery, tokens, FakeConnector())
        advanceUntilIdle()

        viewModel.setDefaultPc("desktop-1", "Switchify PC")
        advanceUntilIdle()

        assertEquals("desktop-1", tokens.getDefaultDesktopId())
        assertEquals(true, viewModel.uiState.value.savedPairings.single().isDefault)
    }

    @Test
    fun confirmUnpairClearsSavedToken() = runTest(dispatcher) {
        val discovery = FakeDiscovery(listOf(pc))
        val tokens = FakeTokenStore(initialTokens = mutableMapOf("desktop-1" to "token"))
        val viewModel = viewModel(discovery, tokens, FakeConnector())
        advanceUntilIdle()

        viewModel.requestUnpair("desktop-1", "Switchify PC")
        viewModel.confirmUnpair()
        advanceUntilIdle()

        assertNull(tokens.getToken("desktop-1"))
        assertNull(viewModel.uiState.value.pendingUnpair)
        assertEquals("Unpaired from Switchify PC.", viewModel.uiState.value.message)
    }

    @Test
    fun confirmUnpairClearsDefaultWhenUnpairingDefaultPc() = runTest(dispatcher) {
        val discovery = FakeDiscovery(listOf(pc))
        val tokens = FakeTokenStore(
            initialTokens = mutableMapOf("desktop-1" to "token"),
            initialDefaultDesktopId = "desktop-1"
        )
        val viewModel = viewModel(discovery, tokens, FakeConnector())
        advanceUntilIdle()

        viewModel.requestUnpair("desktop-1", "Switchify PC")
        viewModel.confirmUnpair()
        advanceUntilIdle()

        assertNull(tokens.getDefaultDesktopId())
    }

    @Test
    fun confirmUnpairKeepsDefaultWhenUnpairingDifferentPc() = runTest(dispatcher) {
        val discovery = FakeDiscovery(listOf(pc, secondPc))
        val tokens = FakeTokenStore(
            initialTokens = mutableMapOf(
                "desktop-1" to "token",
                "desktop-2" to "token-2"
            ),
            initialDefaultDesktopId = "desktop-1"
        )
        val viewModel = viewModel(discovery, tokens, FakeConnector())
        advanceUntilIdle()

        viewModel.requestUnpair("desktop-2", "Office PC")
        viewModel.confirmUnpair()
        advanceUntilIdle()

        assertEquals("desktop-1", tokens.getDefaultDesktopId())
    }

    @Test
    fun confirmUnpairDisconnectsCurrentPc() = runTest(dispatcher) {
        val discovery = FakeDiscovery(listOf(pc))
        val tokens = FakeTokenStore(initialTokens = mutableMapOf("desktop-1" to "token"))
        val viewModel = viewModel(discovery, tokens, FakeConnector())
        PcConnectionStateHolder.setConnected(
            PcAuthenticatedSession("desktop-1", "device-1", "AA:BB:CC:DD:EE:FF"),
            "Switchify PC"
        )
        advanceUntilIdle()

        viewModel.requestUnpair("desktop-1", "Switchify PC")
        viewModel.confirmUnpair()
        advanceUntilIdle()

        assertEquals(PcConnectionState.Disconnected, PcConnectionStateHolder.connectionState.value)
    }

    @Test
    fun setPermissionRequiredReflectsInUiState() = runTest(dispatcher) {
        val viewModel = viewModel(FakeDiscovery(emptyList()), FakeTokenStore(), FakeConnector())
        advanceUntilIdle()

        assertEquals(false, viewModel.uiState.value.permissionRequired)

        viewModel.setPermissionRequired(true)
        advanceUntilIdle()
        assertEquals(true, viewModel.uiState.value.permissionRequired)

        viewModel.setPermissionRequired(false)
        advanceUntilIdle()
        assertEquals(false, viewModel.uiState.value.permissionRequired)
    }

    @Test
    fun uiStateIncludesDiscoveryStatusSearching() = runTest(dispatcher) {
        val discovery = FakeDiscovery(emptyList())
        discovery.status.value = PcDiscoveryStatus.Searching
        val viewModel = viewModel(discovery, FakeTokenStore(), FakeConnector())
        advanceUntilIdle()

        assertEquals(PcDiscoveryStatus.Searching, viewModel.uiState.value.discoveryStatus)
    }

    @Test
    fun uiStateIncludesDiscoveryStatusFound() = runTest(dispatcher) {
        val discovery = FakeDiscovery(listOf(pc))
        val viewModel = viewModel(discovery, FakeTokenStore(), FakeConnector())
        advanceUntilIdle()

        assertEquals(PcDiscoveryStatus.Found, viewModel.uiState.value.discoveryStatus)
    }

    @Test
    fun uiStateIncludesDiscoveryStatusFailed() = runTest(dispatcher) {
        val discovery = FakeDiscovery(emptyList())
        discovery.status.value = PcDiscoveryStatus.Failed
        val viewModel = viewModel(discovery, FakeTokenStore(), FakeConnector())
        advanceUntilIdle()

        assertEquals(PcDiscoveryStatus.Failed, viewModel.uiState.value.discoveryStatus)
    }

    @Test
    fun confirmUnpairRemovesSavedPairingRow() = runTest(dispatcher) {
        val discovery = FakeDiscovery(emptyList())
        val tokens = FakeTokenStore(
            initialTokens = mutableMapOf("desktop-1" to "token"),
            initialLastEndpointIds = mutableMapOf("desktop-1" to "AA:BB:CC:DD:EE:FF"),
            initialServiceNames = mutableMapOf("desktop-1" to "Switchify PC")
        )
        val viewModel = viewModel(discovery, tokens, FakeConnector())
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.savedPairings.size)

        viewModel.requestUnpair("desktop-1", "Switchify PC")
        viewModel.confirmUnpair()
        advanceUntilIdle()

        assertEquals(emptyList<PcSavedPairingRowState>(), viewModel.uiState.value.savedPairings)
    }

    @Test
    fun confirmUnpairRefreshesDiscoveredRow() = runTest(dispatcher) {
        val discovery = FakeDiscovery(listOf(pc))
        val tokens = FakeTokenStore(initialTokens = mutableMapOf("desktop-1" to "token"))
        val viewModel = viewModel(discovery, tokens, FakeConnector())
        advanceUntilIdle()

        assertEquals(true, viewModel.uiState.value.discoveredPcs.first().canUnpair)
        assertEquals("Connect", viewModel.uiState.value.discoveredPcs.first().actionText)

        viewModel.requestUnpair("desktop-1", "Switchify PC")
        viewModel.confirmUnpair()
        advanceUntilIdle()

        assertEquals(false, viewModel.uiState.value.discoveredPcs.first().canUnpair)
        assertEquals("Request access", viewModel.uiState.value.discoveredPcs.first().actionText)
    }

    @Test
    fun dismissUnpairKeepsToken() = runTest(dispatcher) {
        val discovery = FakeDiscovery(listOf(pc))
        val tokens = FakeTokenStore(initialTokens = mutableMapOf("desktop-1" to "token"))
        val viewModel = viewModel(discovery, tokens, FakeConnector())
        advanceUntilIdle()

        viewModel.requestUnpair("desktop-1", "Switchify PC")
        viewModel.dismissUnpair()
        advanceUntilIdle()

        assertEquals("token", tokens.getToken("desktop-1"))
        assertNull(viewModel.uiState.value.pendingUnpair)
    }

    @Test
    fun postPairingAuthFailureRetriesWithoutSavingToken() = runTest(dispatcher) {
        val discovery = FakeDiscovery(listOf(pc))
        val tokens = FakeTokenStore()
        val connector = FakeConnector(
            pairingResult = PcPairingResult.Paired("desktop-1", "token", "AA:BB:CC:DD:EE:FF"),
            pingResult = PcPingResult.AuthFailed()
        )
        val viewModel = viewModel(discovery, tokens, connector)
        advanceUntilIdle()

        viewModel.requestAccess(pc)
        advanceUntilIdle()

        assertEquals(PC_AUTH_RETRY_ATTEMPTS, connector.pingCalls)
        assertNull(tokens.getToken("desktop-1"))
        assertEquals("Connection expired. Request access again.", viewModel.uiState.value.message)
    }

    @Test
    fun postPairingAuthFailureThenSuccessSavesToken() = runTest(dispatcher) {
        val discovery = FakeDiscovery(listOf(pc))
        val tokens = FakeTokenStore()
        val connector = FakeConnector(
            pairingResult = PcPairingResult.Paired("desktop-1", "token", "AA:BB:CC:DD:EE:FF"),
            pingResults = listOf(
                PcPingResult.AuthFailed(),
                PcPingResult.Connected("AA:BB:CC:DD:EE:FF")
            )
        )
        val viewModel = viewModel(discovery, tokens, connector)
        advanceUntilIdle()

        viewModel.requestAccess(pc)
        advanceUntilIdle()

        assertEquals(2, connector.pingCalls)
        assertEquals("token", tokens.getToken("desktop-1"))
        assertEquals("desktop-1", viewModel.uiState.value.connectedDesktopId)
    }

    private fun viewModel(
        discovery: FakeDiscovery,
        tokens: FakeTokenStore,
        connector: FakeConnector,
        requestNonceProvider: () -> String = { "nonce" }
    ) = PcConnectionViewModel(
        context = null,
        discoveryService = discovery,
        tokenStore = tokens,
        identityRepository = FakeIdentity,
        connector = connector,
        requestNonceProvider = requestNonceProvider,
        backgroundDispatcher = dispatcher
    )

    private class FakeDiscovery(initialPcs: List<DiscoveredPc>) : PcDiscovery {
        override val pcs = MutableStateFlow(initialPcs)
        override val status = MutableStateFlow(if (initialPcs.isEmpty()) PcDiscoveryStatus.Empty else PcDiscoveryStatus.Found)
        var stopDiscoveryCalls = 0
        override fun startDiscovery() = Unit
        override fun stopDiscovery() {
            stopDiscoveryCalls++
        }
    }

    private class FakeTokenStore(
        private val initialTokens: MutableMap<String, String> = mutableMapOf(),
        private val initialLastEndpointIds: MutableMap<String, String> = mutableMapOf(),
        private val initialServiceNames: MutableMap<String, String> = mutableMapOf(),
        private var initialDefaultDesktopId: String? = null,
        private var initialLastConnectedDesktopId: String? = null
    ) : PcPairingTokenStore {
        private val lastEndpointIds = initialLastEndpointIds
        private val serviceNames = initialServiceNames

        override fun getToken(desktopId: String): String? = initialTokens[desktopId]

        override fun saveToken(desktopId: String, token: String, lastEndpointId: String, serviceName: String?) {
            initialTokens[desktopId] = token
            lastEndpointIds[desktopId] = lastEndpointId
            if (!serviceName.isNullOrBlank()) serviceNames[desktopId] = serviceName
        }

        override fun clearToken(desktopId: String) {
            initialTokens.remove(desktopId)
            lastEndpointIds.remove(desktopId)
            serviceNames.remove(desktopId)
            if (initialDefaultDesktopId == desktopId) initialDefaultDesktopId = null
            if (initialLastConnectedDesktopId == desktopId) initialLastConnectedDesktopId = null
        }

        override fun listPairings(): List<PcStoredPairing> {
            return initialTokens.keys.map { desktopId ->
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
            val desktopId = initialDefaultDesktopId ?: return null
            if (initialTokens.containsKey(desktopId)) return desktopId
            initialDefaultDesktopId = null
            return null
        }

        override fun setDefaultDesktopId(desktopId: String) {
            if (initialTokens.containsKey(desktopId)) initialDefaultDesktopId = desktopId
        }

        override fun clearDefaultDesktopId() {
            initialDefaultDesktopId = null
        }

        override fun getLastConnectedDesktopId(): String? {
            val desktopId = initialLastConnectedDesktopId ?: return null
            if (initialTokens.containsKey(desktopId)) return desktopId
            initialLastConnectedDesktopId = null
            return null
        }

        override fun recordSuccessfulConnection(desktopId: String) {
            if (initialTokens.containsKey(desktopId)) initialLastConnectedDesktopId = desktopId
        }
    }

    private object FakeIdentity : PcDeviceIdentity {
        override fun getDeviceId(): String = "device-1"
        override fun getDeviceName(): String = "Android phone"
    }

    private class FakeConnector(
        private val pairingResult: PcPairingResult = PcPairingResult.Failed(PcErrorReason.PairingRejected, "Request rejected."),
        private val pingResult: PcPingResult = PcPingResult.Failed(PcErrorReason.Unreachable, "Found PC, but could not connect."),
        private val pairingDeferred: CompletableDeferred<PcPairingResult>? = null,
        private val pairingDeferredsByDesktop: Map<String, CompletableDeferred<PcPairingResult>> = emptyMap(),
        private val pingResultsByDesktop: Map<String, PcPingResult> = emptyMap(),
        pingResults: List<PcPingResult> = emptyList()
    ) : PcConnector {
        private val queuedPingResults = ArrayDeque(pingResults)
        var requestApprovalCalls = 0
        var pingCalls = 0
        val requestNonces = mutableListOf<String>()
        val approvalPcs = mutableListOf<DiscoveredPc>()
        val pingPcs = mutableListOf<DiscoveredPc>()
        var closeCalls = 0

        override suspend fun requestApproval(pc: DiscoveredPc, requestNonce: String): PcPairingResult {
            requestApprovalCalls++
            requestNonces.add(requestNonce)
            approvalPcs.add(pc)
            return pairingDeferredsByDesktop[pc.desktopId]?.await() ?: pairingDeferred?.await() ?: pairingResult
        }

        override suspend fun authenticatedPing(pc: DiscoveredPc, token: String): PcPingResult {
            pingCalls++
            pingPcs.add(pc)
            queuedPingResults.removeFirstOrNull()?.let { return it }
            return pingResultsByDesktop[pc.desktopId] ?: pingResult
        }

        override suspend fun openControlSession(session: PcAuthenticatedSession): PcLiveControlResult {
            return PcLiveControlResult.Failed("unused")
        }

        override suspend fun sendCommand(session: PcAuthenticatedSession, command: PcControlCommand): PcCommandResult {
            return PcCommandResult.Ack
        }

        override fun close() {
            closeCalls++
        }
    }
}
