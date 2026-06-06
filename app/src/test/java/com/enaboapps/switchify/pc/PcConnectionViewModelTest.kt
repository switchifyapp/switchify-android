package com.enaboapps.switchify.pc

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

    private fun viewModel(
        discovery: FakeDiscovery,
        tokens: FakeTokenStore,
        connector: FakeConnector
    ) = PcConnectionViewModel(
        context = null,
        discoveryService = discovery,
        tokenStore = tokens,
        identityRepository = FakeIdentity,
        connector = connector
    )

    private class FakeDiscovery(initialPcs: List<DiscoveredPc>) : PcDiscovery {
        override val pcs = MutableStateFlow(initialPcs)
        override val status = MutableStateFlow(if (initialPcs.isEmpty()) PcDiscoveryStatus.Empty else PcDiscoveryStatus.Found)
        override fun startDiscovery() = Unit
        override fun stopDiscovery() = Unit
    }

    private class FakeTokenStore(
        private val initialTokens: MutableMap<String, String> = mutableMapOf()
    ) : PcPairingTokenStore {
        private val lastUrls = mutableMapOf<String, String>()

        override fun getToken(desktopId: String): String? = initialTokens[desktopId]

        override fun saveToken(desktopId: String, token: String, lastUrl: String, serviceName: String?) {
            initialTokens[desktopId] = token
            lastUrls[desktopId] = lastUrl
        }

        override fun clearToken(desktopId: String) {
            initialTokens.remove(desktopId)
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
        private val pairingResult: PcPairingResult = PcPairingResult.Failed("Request rejected."),
        private val pingResult: PcPingResult = PcPingResult.Failed("Found PC, but could not connect.")
    ) : PcConnector {
        var requestApprovalCalls = 0
        var pingCalls = 0

        override suspend fun requestApproval(pc: DiscoveredPc): PcPairingResult {
            requestApprovalCalls++
            return pairingResult
        }

        override suspend fun authenticatedPing(pc: DiscoveredPc, token: String): PcPingResult {
            pingCalls++
            return pingResult
        }

        override fun close() = Unit
    }
}
