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
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PcConnectionViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val pc = DiscoveredPc(
        serviceName = "Switchify PC",
        desktopId = "desktop-1",
        hostAddresses = listOf("192.168.1.20"),
        port = 7347,
        websocketUrls = listOf("ws://192.168.1.20:7347")
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
        val connector = FakeConnector(pingResult = PcPingResult.Connected("ws://192.168.1.20:7347"))
        val viewModel = viewModel(discovery, tokens, connector)
        advanceUntilIdle()

        viewModel.connectWithSavedToken(pc)
        advanceUntilIdle()

        assertEquals(0, connector.requestApprovalCalls)
        assertEquals(1, connector.pingCalls)
        assertEquals("desktop-1", viewModel.uiState.value.connectedDesktopId)
    }

    @Test
    fun missingTokenPathSendsPairingRequestThenPing() = runTest(dispatcher) {
        val discovery = FakeDiscovery(listOf(pc))
        val tokens = FakeTokenStore()
        val connector = FakeConnector(
            pairingResult = PcPairingResult.Paired("desktop-1", "token", "ws://192.168.1.20:7347"),
            pingResult = PcPingResult.Connected("ws://192.168.1.20:7347")
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
            pingResult = PcPingResult.Connected("ws://192.168.1.20:7347")
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
    fun pairingSuccessClearsApprovalCode() = runTest(dispatcher) {
        val discovery = FakeDiscovery(listOf(pc))
        val tokens = FakeTokenStore()
        val connector = FakeConnector(
            pairingResult = PcPairingResult.Paired("desktop-1", "token", "ws://192.168.1.20:7347"),
            pingResult = PcPingResult.Connected("ws://192.168.1.20:7347")
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
        val connector = FakeConnector(pingResult = PcPingResult.Connected("ws://192.168.1.20:7347"))
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

        assertNull(tokens.getToken("desktop-1"))
        assertEquals("Connection expired. Request access again.", viewModel.uiState.value.message)
        assertEquals("Request access", viewModel.uiState.value.discoveredPcs.first().actionText)
    }

    @Test
    fun savedPairingsListsStoredPcWhenNotDiscovered() = runTest(dispatcher) {
        val discovery = FakeDiscovery(emptyList())
        val tokens = FakeTokenStore(
            initialTokens = mutableMapOf("desktop-1" to "token"),
            initialLastUrls = mutableMapOf("desktop-1" to "ws://192.168.1.20:7347"),
            initialServiceNames = mutableMapOf("desktop-1" to "Switchify PC")
        )
        val viewModel = viewModel(discovery, tokens, FakeConnector())
        advanceUntilIdle()

        val savedPairing = viewModel.uiState.value.savedPairings.single()
        assertEquals("desktop-1", savedPairing.desktopId)
        assertEquals("Switchify PC", savedPairing.title)
        assertEquals("ws://192.168.1.20:7347", savedPairing.summary)
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
            PcAuthenticatedSession("desktop-1", "device-1", "ws://192.168.1.20:7347"),
            "Switchify PC"
        )
        advanceUntilIdle()

        viewModel.requestUnpair("desktop-1", "Switchify PC")
        viewModel.confirmUnpair()
        advanceUntilIdle()

        assertEquals(PcConnectionState.Disconnected, PcConnectionStateHolder.connectionState.value)
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
        requestNonceProvider = requestNonceProvider
    )

    private class FakeDiscovery(initialPcs: List<DiscoveredPc>) : PcDiscovery {
        override val pcs = MutableStateFlow(initialPcs)
        override val status = MutableStateFlow(if (initialPcs.isEmpty()) PcDiscoveryStatus.Empty else PcDiscoveryStatus.Found)
        override fun startDiscovery() = Unit
        override fun stopDiscovery() = Unit
    }

    private class FakeTokenStore(
        private val initialTokens: MutableMap<String, String> = mutableMapOf(),
        private val initialLastUrls: MutableMap<String, String> = mutableMapOf(),
        private val initialServiceNames: MutableMap<String, String> = mutableMapOf()
    ) : PcPairingTokenStore {
        private val lastUrls = initialLastUrls
        private val serviceNames = initialServiceNames

        override fun getToken(desktopId: String): String? = initialTokens[desktopId]

        override fun saveToken(desktopId: String, token: String, lastUrl: String, serviceName: String?) {
            initialTokens[desktopId] = token
            lastUrls[desktopId] = lastUrl
            if (!serviceName.isNullOrBlank()) serviceNames[desktopId] = serviceName
        }

        override fun clearToken(desktopId: String) {
            initialTokens.remove(desktopId)
            lastUrls.remove(desktopId)
            serviceNames.remove(desktopId)
        }

        override fun listPairings(): List<PcStoredPairing> {
            return initialTokens.keys.map { desktopId ->
                PcStoredPairing(
                    desktopId = desktopId,
                    serviceName = serviceNames[desktopId],
                    lastUrl = lastUrls[desktopId]
                )
            }
        }

        override fun getLastUrl(desktopId: String): String? = lastUrls[desktopId]
        override fun getServiceName(desktopId: String): String? = serviceNames[desktopId]
    }

    private object FakeIdentity : PcDeviceIdentity {
        override fun getDeviceId(): String = "device-1"
        override fun getDeviceName(): String = "Android phone"
    }

    private class FakeConnector(
        private val pairingResult: PcPairingResult = PcPairingResult.Failed(PcErrorReason.PairingRejected, "Request rejected."),
        private val pingResult: PcPingResult = PcPingResult.Failed(PcErrorReason.Unreachable, "Found PC, but could not connect."),
        private val pairingDeferred: CompletableDeferred<PcPairingResult>? = null
    ) : PcConnector {
        var requestApprovalCalls = 0
        var pingCalls = 0
        val requestNonces = mutableListOf<String>()

        override suspend fun requestApproval(pc: DiscoveredPc, requestNonce: String): PcPairingResult {
            requestApprovalCalls++
            requestNonces.add(requestNonce)
            return pairingDeferred?.await() ?: pairingResult
        }

        override suspend fun authenticatedPing(pc: DiscoveredPc, token: String): PcPingResult {
            pingCalls++
            return pingResult
        }

        override suspend fun openControlSession(session: PcAuthenticatedSession): PcLiveControlResult {
            return PcLiveControlResult.Failed("unused")
        }

        override suspend fun sendCommand(session: PcAuthenticatedSession, command: PcControlCommand): PcCommandResult {
            return PcCommandResult.Ack
        }

        override fun close() = Unit
    }
}
