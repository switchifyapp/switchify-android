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
    fun invalidSavedTokenClearsTokenAndOffersRequestAccess() = runTest(dispatcher) {
        val discovery = FakeDiscovery(listOf(pc))
        val tokens = FakeTokenStore(initialTokens = mutableMapOf("desktop-1" to "token"))
        val connector = FakeConnector(pingResult = PcPingResult.AuthFailed())
        val viewModel = viewModel(discovery, tokens, connector)
        advanceUntilIdle()

        viewModel.connectWithSavedToken(pc)
        advanceUntilIdle()

        assertEquals(PC_AUTH_RETRY_ATTEMPTS, connector.pingCalls)
        assertNull(tokens.getToken("desktop-1"))
        assertEquals("Connection expired. Request access again.", viewModel.uiState.value.message)
        assertEquals("Request access", viewModel.uiState.value.discoveredPcs.first().actionText)
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
    fun savedPairingsExcludeDiscoveredPc() = runTest(dispatcher) {
        val discovery = FakeDiscovery(listOf(pc))
        val tokens = FakeTokenStore(initialTokens = mutableMapOf("desktop-1" to "token"))
        val viewModel = viewModel(discovery, tokens, FakeConnector())
        advanceUntilIdle()

        assertEquals(true, viewModel.uiState.value.discoveredPcs.first().canUnpair)
        assertEquals(emptyList<PcSavedPairingRowState>(), viewModel.uiState.value.savedPairings)
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
    fun postPairingAuthFailureRetriesBeforeClearingToken() = runTest(dispatcher) {
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
        private val initialServiceNames: MutableMap<String, String> = mutableMapOf()
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
    }

    private object FakeIdentity : PcDeviceIdentity {
        override fun getDeviceId(): String = "device-1"
        override fun getDeviceName(): String = "Android phone"
    }

    private class FakeConnector(
        private val pairingResult: PcPairingResult = PcPairingResult.Failed(PcErrorReason.PairingRejected, "Request rejected."),
        private val pingResult: PcPingResult = PcPingResult.Failed(PcErrorReason.Unreachable, "Found PC, but could not connect."),
        private val pairingDeferred: CompletableDeferred<PcPairingResult>? = null,
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
            return pairingDeferred?.await() ?: pairingResult
        }

        override suspend fun authenticatedPing(pc: DiscoveredPc, token: String): PcPingResult {
            pingCalls++
            pingPcs.add(pc)
            return queuedPingResults.removeFirstOrNull() ?: pingResult
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
