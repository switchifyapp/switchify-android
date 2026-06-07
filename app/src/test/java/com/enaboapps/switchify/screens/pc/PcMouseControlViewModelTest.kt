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
import com.enaboapps.switchify.pc.PcPointerBounds
import com.enaboapps.switchify.pc.PcPointerDeltas
import com.enaboapps.switchify.pc.PcPointerMovementProfile
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
        val viewModel = PcMouseControlViewModel(FakeTokenStore(), connector, FakeMovementSizeStore())

        viewModel.send(PcMouseCommand.Move(80, 0))
        advanceUntilIdle()

        assertEquals(PcMouseCommand.Move(80, 0), connector.commands.single())
        assertEquals(1, connector.openControlSessionCalls)
        assertTrue(connector.oneShotCommands.isEmpty())
        assertNull(viewModel.uiState.value.message)
    }

    @Test
    fun firstRunDefaultsToSmallFallbackStep() = runTest(dispatcher) {
        val viewModel = PcMouseControlViewModel(FakeTokenStore(), FakeConnector(PcCommandResult.Ack), FakeMovementSizeStore())

        advanceUntilIdle()

        assertEquals(PcMouseMovementSize.Small, viewModel.uiState.value.selectedMovementSize)
        assertEquals(40, viewModel.uiState.value.movementStep)
    }

    @Test
    fun storedMediumLoadsWithFallbackStep() = runTest(dispatcher) {
        val viewModel = PcMouseControlViewModel(
            FakeTokenStore(),
            FakeConnector(PcCommandResult.Ack),
            FakeMovementSizeStore(PcMouseMovementSize.Medium)
        )

        advanceUntilIdle()

        assertEquals(PcMouseMovementSize.Medium, viewModel.uiState.value.selectedMovementSize)
        assertEquals(80, viewModel.uiState.value.movementStep)
    }

    @Test
    fun storedLargeLoadsWithFallbackStep() = runTest(dispatcher) {
        val viewModel = PcMouseControlViewModel(
            FakeTokenStore(),
            FakeConnector(PcCommandResult.Ack),
            FakeMovementSizeStore(PcMouseMovementSize.Large)
        )

        advanceUntilIdle()

        assertEquals(PcMouseMovementSize.Large, viewModel.uiState.value.selectedMovementSize)
        assertEquals(160, viewModel.uiState.value.movementStep)
    }

    @Test
    fun selectingMediumPersistsPreference() = runTest(dispatcher) {
        val movementSizeStore = FakeMovementSizeStore()
        val viewModel = PcMouseControlViewModel(FakeTokenStore(), FakeConnector(PcCommandResult.Ack), movementSizeStore)

        viewModel.selectMovementSize(PcMouseMovementSize.Medium)
        advanceUntilIdle()

        assertEquals(PcMouseMovementSize.Medium, movementSizeStore.getSelectedSize())
        assertEquals(PcMouseMovementSize.Medium, viewModel.uiState.value.selectedMovementSize)
        assertEquals(80, viewModel.uiState.value.movementStep)
    }

    @Test
    fun selectingLargeUpdatesMovementStepImmediately() = runTest(dispatcher) {
        val viewModel = PcMouseControlViewModel(FakeTokenStore(), FakeConnector(PcCommandResult.Ack), FakeMovementSizeStore())

        viewModel.selectMovementSize(PcMouseMovementSize.Large)
        advanceUntilIdle()

        assertEquals(PcMouseMovementSize.Large, viewModel.uiState.value.selectedMovementSize)
        assertEquals(160, viewModel.uiState.value.movementStep)
    }

    @Test
    fun livePointerProfileMapsSelectedSmallToProfileSmall() = runTest(dispatcher) {
        PcConnectionStateHolder.setConnected(session, "Switchify PC")
        val viewModel = PcMouseControlViewModel(
            FakeTokenStore(),
            FakeConnector(PcCommandResult.Ack, pointerProfile = pointerProfile(small = 50, medium = 130, large = 252)),
            FakeMovementSizeStore(PcMouseMovementSize.Small)
        )

        advanceUntilIdle()

        assertEquals(50, viewModel.uiState.value.movementStep)
    }

    @Test
    fun livePointerProfileMapsSelectedMediumToProfileMedium() = runTest(dispatcher) {
        PcConnectionStateHolder.setConnected(session, "Switchify PC")
        val viewModel = PcMouseControlViewModel(
            FakeTokenStore(),
            FakeConnector(PcCommandResult.Ack, pointerProfile = pointerProfile(small = 50, medium = 130, large = 252)),
            FakeMovementSizeStore(PcMouseMovementSize.Medium)
        )

        advanceUntilIdle()

        assertEquals(130, viewModel.uiState.value.movementStep)
    }

    @Test
    fun livePointerProfileMapsSelectedLargeToProfileLarge() = runTest(dispatcher) {
        PcConnectionStateHolder.setConnected(session, "Switchify PC")
        val viewModel = PcMouseControlViewModel(
            FakeTokenStore(),
            FakeConnector(PcCommandResult.Ack, pointerProfile = pointerProfile(small = 50, medium = 130, large = 252)),
            FakeMovementSizeStore(PcMouseMovementSize.Large)
        )

        advanceUntilIdle()

        assertEquals(252, viewModel.uiState.value.movementStep)
    }

    @Test
    fun livePointerProfileClampsSelectedStepToMaxDelta() = runTest(dispatcher) {
        PcConnectionStateHolder.setConnected(session, "Switchify PC")
        val viewModel = PcMouseControlViewModel(
            FakeTokenStore(),
            FakeConnector(PcCommandResult.Ack, pointerProfile = pointerProfile(small = 50, medium = 900, large = 1_000, maxDelta = 500)),
            FakeMovementSizeStore(PcMouseMovementSize.Large)
        )

        advanceUntilIdle()

        assertEquals(500, viewModel.uiState.value.movementStep)
    }

    @Test
    fun missingLivePointerProfileKeepsFallbackSteps() = runTest(dispatcher) {
        PcConnectionStateHolder.setConnected(session, "Switchify PC")
        val viewModel = PcMouseControlViewModel(
            FakeTokenStore(),
            FakeConnector(PcCommandResult.Ack),
            FakeMovementSizeStore(PcMouseMovementSize.Large)
        )

        advanceUntilIdle()

        assertEquals(160, viewModel.uiState.value.movementStep)
        assertNull(viewModel.uiState.value.message)
    }

    @Test
    fun disconnectedStateShowsConnectFirstAndDoesNotSendCommand() = runTest(dispatcher) {
        val connector = FakeConnector(PcCommandResult.Ack)
        val viewModel = PcMouseControlViewModel(FakeTokenStore(), connector, FakeMovementSizeStore())

        viewModel.send(PcMouseCommand.LeftClick)
        advanceUntilIdle()

        assertTrue(connector.commands.isEmpty())
        assertEquals(PcMouseControlViewModel.CONNECT_FIRST_MESSAGE, viewModel.uiState.value.message)
    }

    @Test
    fun ackClearsPreviousMessage() = runTest(dispatcher) {
        PcConnectionStateHolder.setConnected(session, "Switchify PC")
        val connector = FakeConnector(PcCommandResult.Ack)
        val viewModel = PcMouseControlViewModel(FakeTokenStore(), connector, FakeMovementSizeStore())

        viewModel.send(PcMouseCommand.LeftClick)
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.message)
    }

    @Test
    fun failedCommandShowsFailureMessage() = runTest(dispatcher) {
        PcConnectionStateHolder.setConnected(session, "Switchify PC")
        val viewModel = PcMouseControlViewModel(FakeTokenStore(), FakeConnector(PcCommandResult.Failed()), FakeMovementSizeStore())

        viewModel.send(PcMouseCommand.LeftClick)
        advanceUntilIdle()

        assertEquals(PcMouseControlViewModel.COMMAND_FAILED_MESSAGE, viewModel.uiState.value.message)
    }

    @Test
    fun authFailureClearsTokenAndDisconnects() = runTest(dispatcher) {
        PcConnectionStateHolder.setConnected(session, "Switchify PC")
        val tokenStore = FakeTokenStore(mutableMapOf("desktop-1" to "token"))
        val viewModel = PcMouseControlViewModel(tokenStore, FakeConnector(PcCommandResult.AuthFailed()), FakeMovementSizeStore(PcMouseMovementSize.Large))

        viewModel.send(PcMouseCommand.LeftClick)
        advanceUntilIdle()

        assertNull(tokenStore.getToken("desktop-1"))
        assertTrue(PcConnectionStateHolder.connectionState.value is PcConnectionState.Disconnected)
        assertEquals(PcMouseControlViewModel.CONNECT_FIRST_MESSAGE, viewModel.uiState.value.message)
        assertEquals(PcMouseMovementSize.Large, viewModel.uiState.value.selectedMovementSize)
        assertEquals(160, viewModel.uiState.value.movementStep)
    }

    @Test
    fun busyStateIsSetWhileCommandIsInFlight() = runTest(dispatcher) {
        PcConnectionStateHolder.setConnected(session, "Switchify PC")
        val deferred = CompletableDeferred<PcCommandResult>()
        val viewModel = PcMouseControlViewModel(FakeTokenStore(), FakeConnector(deferred), FakeMovementSizeStore())

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
        private val commandResult: PcCommandResult,
        private val pointerProfile: PcPointerMovementProfile? = null
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
                    override val pointerProfile: PcPointerMovementProfile? = this@FakeConnector.pointerProfile

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

    private fun pointerProfile(small: Int, medium: Int, large: Int, maxDelta: Int = 500): PcPointerMovementProfile {
        return PcPointerMovementProfile(
            displayId = "0:0:1280:720:1.5",
            scaleFactor = 1.5,
            bounds = PcPointerBounds(0, 0, 1280, 720),
            maxDelta = maxDelta,
            recommendedDeltas = PcPointerDeltas(small = small, medium = medium, large = large)
        )
    }

    private class FakeMovementSizeStore(
        private var selectedSize: PcMouseMovementSize = PcMouseMovementSize.Small
    ) : PcMouseMovementSizeStore {
        override fun getSelectedSize(): PcMouseMovementSize = selectedSize

        override fun setSelectedSize(size: PcMouseMovementSize) {
            selectedSize = size
        }
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
