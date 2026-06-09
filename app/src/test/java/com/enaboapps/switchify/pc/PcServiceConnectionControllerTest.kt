package com.enaboapps.switchify.pc

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
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
        hostAddresses = listOf("192.168.1.20"),
        port = 7347,
        websocketUrls = listOf("ws://192.168.1.20:7347")
    )
    private val secondPc = DiscoveredPc(
        serviceName = "Office PC",
        desktopId = "desktop-2",
        hostAddresses = listOf("192.168.1.21"),
        port = 7347,
        websocketUrls = listOf("ws://192.168.1.21:7347")
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
        val connector = FakeConnector(PcPingResult.Connected("ws://192.168.1.20:7347"))
        val controller = controller(tokens, connector)

        val result = controller.connectOrRequestAccess()

        assertTrue(result is PcServiceConnectResult.Connected)
        assertEquals(1, connector.pingCalls)
        assertEquals(0, connector.pairingCalls)
        assertEquals("desktop-1", (PcConnectionStateHolder.connectionState.value as PcConnectionState.Connected).session.desktopId)
        assertEquals("ws://192.168.1.20:7347", tokens.getLastUrl("desktop-1"))
    }

    @Test
    fun missingTokenRequestsApprovalThenPings() = runTest(dispatcher) {
        val tokens = FakeTokenStore()
        val connector = FakeConnector(
            pingResult = PcPingResult.Connected("ws://192.168.1.20:7347"),
            pairingResult = PcPairingResult.Paired("desktop-1", "new-token", "ws://192.168.1.20:7347")
        )
        val controller = controller(tokens, connector)
        var approvalCode: PcApprovalCodeState? = null

        val result = controller.connectOrRequestAccess { approvalCode = it }

        assertTrue(result is PcServiceConnectResult.Connected)
        assertEquals(PcApprovalCodeState("Switchify PC", "215918"), approvalCode)
        assertEquals("nonce-1", connector.requestNonces.single())
        assertEquals(1, connector.pairingCalls)
        assertEquals(1, connector.pingCalls)
        assertEquals("new-token", tokens.getToken("desktop-1"))
        assertTrue(PcConnectionStateHolder.connectionState.value is PcConnectionState.Connected)
    }

    @Test
    fun invalidSavedTokenClearsTokenAndDisconnects() = runTest(dispatcher) {
        val tokens = FakeTokenStore(mutableMapOf("desktop-1" to "token"))
        val connector = FakeConnector(PcPingResult.AuthFailed())
        val controller = controller(tokens, connector)

        val result = controller.connectOrRequestAccess()

        assertTrue(result is PcServiceConnectResult.Failed)
        assertEquals(PcErrorReason.AuthExpired, (result as PcServiceConnectResult.Failed).reason)
        assertNull(tokens.getToken("desktop-1"))
        assertTrue(PcConnectionStateHolder.connectionState.value is PcConnectionState.Disconnected)
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
    fun connectToUsesSavedTokenWithoutPairing() = runTest(dispatcher) {
        val tokens = FakeTokenStore(mutableMapOf("desktop-1" to "token"))
        val connector = FakeConnector(PcPingResult.Connected("ws://192.168.1.20:7347"))
        val controller = controller(tokens, connector)

        val result = controller.connectTo(pc)

        assertTrue(result is PcServiceConnectResult.Connected)
        assertEquals(1, connector.pingCalls)
        assertEquals(0, connector.pairingCalls)
        assertEquals("desktop-1", (PcConnectionStateHolder.connectionState.value as PcConnectionState.Connected).session.desktopId)
    }

    @Test
    fun connectToExpiredTokenFallsThroughToPairing() = runTest(dispatcher) {
        val tokens = FakeTokenStore(mutableMapOf("desktop-1" to "old-token"))
        val connector = FakeConnector(
            pingResult = PcPingResult.AuthFailed(),
            pairingResult = PcPairingResult.Paired("desktop-1", "new-token", "ws://192.168.1.20:7347"),
            pingResultsByToken = mapOf(
                "old-token" to PcPingResult.AuthFailed(),
                "new-token" to PcPingResult.Connected("ws://192.168.1.20:7347")
            )
        )
        val controller = controller(tokens, connector)
        var approvalCode: PcApprovalCodeState? = null

        val result = controller.connectTo(pc) { approvalCode = it }

        assertTrue(result is PcServiceConnectResult.Connected)
        assertEquals("Switchify PC", approvalCode?.pcName)
        assertEquals(1, connector.pairingCalls)
        assertEquals(2, connector.pingCalls)
        assertEquals("new-token", tokens.getToken("desktop-1"))
        assertTrue(PcConnectionStateHolder.connectionState.value is PcConnectionState.Connected)
    }

    @Test
    fun connectToMissingTokenPairsDirectly() = runTest(dispatcher) {
        val tokens = FakeTokenStore()
        val connector = FakeConnector(
            pingResult = PcPingResult.Connected("ws://192.168.1.21:7347"),
            pairingResult = PcPairingResult.Paired("desktop-2", "token-2", "ws://192.168.1.21:7347")
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
            pingResult = PcPingResult.Connected("ws://192.168.1.21:7347"),
            pairingResultsByDesktop = mapOf(
                "desktop-1" to PcPairingResult.Failed(PcErrorReason.Unreachable, "Could not reach this PC."),
                "desktop-2" to PcPairingResult.Paired("desktop-2", "token-2", "ws://192.168.1.21:7347")
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
            pingResult = PcPingResult.Connected("ws://192.168.1.21:7347"),
            pairingResultsByDesktop = mapOf(
                "desktop-1" to PcPairingResult.Failed(PcErrorReason.PairingRejected, "Request rejected."),
                "desktop-2" to PcPairingResult.Paired("desktop-2", "token-2", "ws://192.168.1.21:7347")
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
        override fun startDiscovery() = Unit
        override fun stopDiscovery() = Unit
    }

    private class FakeTokenStore(
        private val tokens: MutableMap<String, String> = mutableMapOf()
    ) : PcPairingTokenStore {
        private val lastUrls = mutableMapOf<String, String>()

        override fun getToken(desktopId: String): String? = tokens[desktopId]

        override fun saveToken(desktopId: String, token: String, lastUrl: String, serviceName: String?) {
            tokens[desktopId] = token
            lastUrls[desktopId] = lastUrl
        }

        override fun clearToken(desktopId: String) {
            tokens.remove(desktopId)
            lastUrls.remove(desktopId)
        }

        override fun listPairings(): List<PcStoredPairing> {
            return tokens.keys.map { desktopId ->
                PcStoredPairing(
                    desktopId = desktopId,
                    serviceName = null,
                    lastUrl = lastUrls[desktopId]
                )
            }
        }

        override fun getLastUrl(desktopId: String): String? = lastUrls[desktopId]
        override fun getServiceName(desktopId: String): String? = null
    }

    private object FakeIdentity : PcDeviceIdentity {
        override fun getDeviceId(): String = "device-1"
        override fun getDeviceName(): String = "Android phone"
    }

    private class FakeConnector(
        private val pingResult: PcPingResult,
        private val pairingResult: PcPairingResult = PcPairingResult.Failed(PcErrorReason.Failed, "unused"),
        private val pingResultsByToken: Map<String, PcPingResult> = emptyMap(),
        private val pairingResultsByDesktop: Map<String, PcPairingResult> = emptyMap()
    ) : PcConnector {
        var pingCalls = 0
        var pairingCalls = 0
        val requestNonces = mutableListOf<String>()

        override suspend fun requestApproval(pc: DiscoveredPc, requestNonce: String): PcPairingResult {
            pairingCalls++
            requestNonces.add(requestNonce)
            return pairingResultsByDesktop[pc.desktopId] ?: pairingResult
        }

        override suspend fun authenticatedPing(pc: DiscoveredPc, token: String): PcPingResult {
            pingCalls++
            return pingResultsByToken[token] ?: pingResult
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
