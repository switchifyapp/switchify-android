package com.enaboapps.switchify.pc

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
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
        var waitingForApproval = false

        val result = controller.connectOrRequestAccess { waitingForApproval = true }

        assertTrue(result is PcServiceConnectResult.Connected)
        assertTrue(waitingForApproval)
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
        assertNull(tokens.getToken("desktop-1"))
        assertTrue(PcConnectionStateHolder.connectionState.value is PcConnectionState.Disconnected)
    }

    private fun controller(tokens: FakeTokenStore, connector: FakeConnector): PcServiceConnectionController {
        return PcServiceConnectionController(
            context = null,
            scope = kotlinx.coroutines.CoroutineScope(dispatcher),
            discovery = FakeDiscovery(listOf(pc)),
            tokenStore = tokens,
            identityRepository = FakeIdentity,
            connector = connector
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

        override fun getLastUrl(desktopId: String): String? = lastUrls[desktopId]
        override fun getServiceName(desktopId: String): String? = null
    }

    private object FakeIdentity : PcDeviceIdentity {
        override fun getDeviceId(): String = "device-1"
        override fun getDeviceName(): String = "Android phone"
    }

    private class FakeConnector(
        private val pingResult: PcPingResult,
        private val pairingResult: PcPairingResult = PcPairingResult.Failed("unused")
    ) : PcConnector {
        var pingCalls = 0
        var pairingCalls = 0

        override suspend fun requestApproval(pc: DiscoveredPc): PcPairingResult {
            pairingCalls++
            return pairingResult
        }

        override suspend fun authenticatedPing(pc: DiscoveredPc, token: String): PcPingResult {
            pingCalls++
            return pingResult
        }

        override suspend fun openMouseControlSession(session: PcAuthenticatedSession): PcLiveControlResult {
            return PcLiveControlResult.Failed("unused")
        }

        override suspend fun sendMouseCommand(session: PcAuthenticatedSession, command: PcMouseCommand): PcCommandResult {
            return PcCommandResult.Ack
        }

        override fun close() = Unit
    }
}
