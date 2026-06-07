package com.enaboapps.switchify.screens.pc

import com.enaboapps.switchify.pc.DiscoveredPc
import com.enaboapps.switchify.pc.PcAuthenticatedSession
import com.enaboapps.switchify.pc.PcCommandResult
import com.enaboapps.switchify.pc.PcConnectionState
import com.enaboapps.switchify.pc.PcConnectionStateHolder
import com.enaboapps.switchify.pc.PcConnector
import com.enaboapps.switchify.pc.PcLiveControlResult
import com.enaboapps.switchify.pc.PcMouseControlConnection
import com.enaboapps.switchify.pc.PcMouseCommand
import com.enaboapps.switchify.pc.PcPairingResult
import com.enaboapps.switchify.pc.PcPairingTokenStore
import com.enaboapps.switchify.pc.PcPingResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
class PcMouseControlViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val session = PcAuthenticatedSession(
        desktopId = "desktop-1",
        deviceId = "device-1",
        websocketUrl = "ws://192.168.1.20:7347"
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
    fun connectedStateSendsSelectedCommand() = runTest(dispatcher) {
        PcConnectionStateHolder.setConnected(session, "Switchify PC")
        val connector = FakeConnector(PcCommandResult.Ack)
        val viewModel = PcMouseControlViewModel(FakeTokenStore(), connector)

        viewModel.send(PcMouseCommand.Move(80, 0))
        advanceUntilIdle()

        assertEquals(PcMouseCommand.Move(80, 0), connector.commands.single())
        assertEquals(1, connector.openControlSessionCalls)
        assertTrue(connector.oneShotCommands.isEmpty())
        assertNull(viewModel.uiState.value.message)
    }

    @Test
    fun disconnectedStateShowsConnectFirstAndDoesNotSendCommand() = runTest(dispatcher) {
        val connector = FakeConnector(PcCommandResult.Ack)
        val viewModel = PcMouseControlViewModel(FakeTokenStore(), connector)

        viewModel.send(PcMouseCommand.LeftClick)
        advanceUntilIdle()

        assertTrue(connector.commands.isEmpty())
        assertEquals(PcMouseControlViewModel.CONNECT_FIRST_MESSAGE, viewModel.uiState.value.message)
    }

    @Test
    fun ackClearsPreviousMessage() = runTest(dispatcher) {
        PcConnectionStateHolder.setConnected(session, "Switchify PC")
        val connector = FakeConnector(PcCommandResult.Ack)
        val viewModel = PcMouseControlViewModel(FakeTokenStore(), connector)

        viewModel.send(PcMouseCommand.LeftClick)
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.message)
    }

    @Test
    fun failedCommandShowsFailureMessage() = runTest(dispatcher) {
        PcConnectionStateHolder.setConnected(session, "Switchify PC")
        val viewModel = PcMouseControlViewModel(FakeTokenStore(), FakeConnector(PcCommandResult.Failed()))

        viewModel.send(PcMouseCommand.LeftClick)
        advanceUntilIdle()

        assertEquals(PcMouseControlViewModel.COMMAND_FAILED_MESSAGE, viewModel.uiState.value.message)
    }

    @Test
    fun authFailureClearsTokenAndDisconnects() = runTest(dispatcher) {
        PcConnectionStateHolder.setConnected(session, "Switchify PC")
        val tokenStore = FakeTokenStore(mutableMapOf("desktop-1" to "token"))
        val viewModel = PcMouseControlViewModel(tokenStore, FakeConnector(PcCommandResult.AuthFailed()))

        viewModel.send(PcMouseCommand.LeftClick)
        advanceUntilIdle()

        assertNull(tokenStore.getToken("desktop-1"))
        assertTrue(PcConnectionStateHolder.connectionState.value is PcConnectionState.Disconnected)
        assertEquals(PcMouseControlViewModel.CONNECT_FIRST_MESSAGE, viewModel.uiState.value.message)
    }

    @Test
    fun busyStateIsSetWhileCommandIsInFlight() = runTest(dispatcher) {
        PcConnectionStateHolder.setConnected(session, "Switchify PC")
        val deferred = CompletableDeferred<PcCommandResult>()
        val viewModel = PcMouseControlViewModel(FakeTokenStore(), FakeConnector(deferred))

        viewModel.send(PcMouseCommand.Scroll(0, 5))
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isBusy)
        assertEquals(PcMouseCommand.Scroll(0, 5), viewModel.uiState.value.busyCommand)

        deferred.complete(PcCommandResult.Ack)
        advanceUntilIdle()

        assertTrue(!viewModel.uiState.value.isBusy)
        assertNull(viewModel.uiState.value.busyCommand)
    }

    private class FakeConnector(
        private val commandResult: PcCommandResult
    ) : PcConnector {
        constructor(commandResult: CompletableDeferred<PcCommandResult>) : this(PcCommandResult.Ack) {
            deferredResult = commandResult
        }

        private var deferredResult: CompletableDeferred<PcCommandResult>? = null
        var openControlSessionCalls = 0
        val commands = mutableListOf<PcMouseCommand>()
        val oneShotCommands = mutableListOf<PcMouseCommand>()

        override suspend fun requestApproval(pc: DiscoveredPc): PcPairingResult {
            return PcPairingResult.Failed("unused")
        }

        override suspend fun authenticatedPing(pc: DiscoveredPc, token: String): PcPingResult {
            return PcPingResult.Failed("unused")
        }

        override suspend fun openMouseControlSession(session: PcAuthenticatedSession): PcLiveControlResult {
            openControlSessionCalls++
            return PcLiveControlResult.Connected(
                object : PcMouseControlConnection {
                    override suspend fun sendMouseCommand(command: PcMouseCommand): PcCommandResult {
                        commands.add(command)
                        return deferredResult?.await() ?: commandResult
                    }

                    override fun close() = Unit
                }
            )
        }

        override suspend fun sendMouseCommand(
            session: PcAuthenticatedSession,
            command: PcMouseCommand
        ): PcCommandResult {
            oneShotCommands.add(command)
            return deferredResult?.await() ?: commandResult
        }

        override fun close() = Unit
    }

    private class FakeTokenStore(
        private val tokens: MutableMap<String, String> = mutableMapOf()
    ) : PcPairingTokenStore {
        override fun getToken(desktopId: String): String? = tokens[desktopId]

        override fun saveToken(desktopId: String, token: String, lastUrl: String, serviceName: String?) {
            tokens[desktopId] = token
        }

        override fun clearToken(desktopId: String) {
            tokens.remove(desktopId)
        }

        override fun getLastUrl(desktopId: String): String? = null
        override fun getServiceName(desktopId: String): String? = null
    }
}
