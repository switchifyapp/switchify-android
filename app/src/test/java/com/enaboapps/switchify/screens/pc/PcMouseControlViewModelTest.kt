package com.enaboapps.switchify.screens.pc

import com.enaboapps.switchify.pc.DiscoveredPc
import com.enaboapps.switchify.pc.PcAuthenticatedSession
import com.enaboapps.switchify.pc.PcCommandResult
import com.enaboapps.switchify.pc.PcConnectionState
import com.enaboapps.switchify.pc.PcConnectionStateHolder
import com.enaboapps.switchify.pc.PcConnector
import com.enaboapps.switchify.pc.PcKeyboardKey
import com.enaboapps.switchify.pc.PcLiveControlResult
import com.enaboapps.switchify.pc.PcControlConnection
import com.enaboapps.switchify.pc.PcControlCommand
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

        viewModel.send(PcControlCommand.Move(80, 0))
        advanceUntilIdle()

        assertEquals(PcControlCommand.Move(80, 0), connector.commands.single())
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
    fun firstRunDefaultsToMouseSurface() = runTest(dispatcher) {
        val viewModel = PcMouseControlViewModel(
            FakeTokenStore(),
            FakeConnector(PcCommandResult.Ack),
            FakeMovementSizeStore(),
            FakeControlSurfaceStore()
        )

        advanceUntilIdle()

        assertEquals(PcControlSurface.Mouse, viewModel.uiState.value.activeSurface)
    }

    @Test
    fun storedTypingSurfaceLoadsAsActiveSurface() = runTest(dispatcher) {
        val viewModel = PcMouseControlViewModel(
            FakeTokenStore(),
            FakeConnector(PcCommandResult.Ack),
            FakeMovementSizeStore(),
            FakeControlSurfaceStore(PcControlSurface.Typing)
        )

        advanceUntilIdle()

        assertEquals(PcControlSurface.Typing, viewModel.uiState.value.activeSurface)
    }

    @Test
    fun controlSurfacePreferenceParserDefaultsUnknownValuesToMouse() {
        assertEquals(PcControlSurface.Mouse, PcControlSurface.fromPreferenceValue(""))
        assertEquals(PcControlSurface.Mouse, PcControlSurface.fromPreferenceValue(null))
        assertEquals(PcControlSurface.Mouse, PcControlSurface.fromPreferenceValue("keyboard"))
    }

    @Test
    fun controlSurfacePreferenceParserReadsKnownValues() {
        assertEquals(PcControlSurface.Mouse, PcControlSurface.fromPreferenceValue("mouse"))
        assertEquals(PcControlSurface.Typing, PcControlSurface.fromPreferenceValue("typing"))
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

        viewModel.send(PcControlCommand.LeftClick)
        advanceUntilIdle()

        assertTrue(connector.commands.isEmpty())
        assertEquals(PcMouseControlViewModel.CONNECT_FIRST_MESSAGE, viewModel.uiState.value.message)
    }

    @Test
    fun showTypingSurfaceSwitchesActiveSurface() = runTest(dispatcher) {
        val surfaceStore = FakeControlSurfaceStore()
        val viewModel = PcMouseControlViewModel(
            FakeTokenStore(),
            FakeConnector(PcCommandResult.Ack),
            FakeMovementSizeStore(),
            surfaceStore
        )

        viewModel.selectControlSurface(PcControlSurface.Typing)
        advanceUntilIdle()

        assertEquals(PcControlSurface.Typing, viewModel.uiState.value.activeSurface)
        assertEquals(PcControlSurface.Typing, surfaceStore.getSelectedSurface())
    }

    @Test
    fun showMouseSurfaceSwitchesActiveSurfaceBackToMouse() = runTest(dispatcher) {
        val surfaceStore = FakeControlSurfaceStore(PcControlSurface.Typing)
        val viewModel = PcMouseControlViewModel(
            FakeTokenStore(),
            FakeConnector(PcCommandResult.Ack),
            FakeMovementSizeStore(),
            surfaceStore
        )

        viewModel.updateTypingText("draft")
        viewModel.updateTypingText("draft\u001B")
        viewModel.selectControlSurface(PcControlSurface.Mouse)
        advanceUntilIdle()

        assertEquals(PcControlSurface.Mouse, viewModel.uiState.value.activeSurface)
        assertEquals(PcControlSurface.Mouse, surfaceStore.getSelectedSurface())
        assertEquals("draft\u001B", viewModel.uiState.value.typingText)
        assertNull(viewModel.uiState.value.typingMessage)
    }

    @Test
    fun switchingSurfacesDoesNotOpenAnotherLiveSocket() = runTest(dispatcher) {
        PcConnectionStateHolder.setConnected(session, "Switchify PC")
        val connector = FakeConnector(PcCommandResult.Ack)
        val viewModel = PcMouseControlViewModel(FakeTokenStore(), connector, FakeMovementSizeStore())

        advanceUntilIdle()
        viewModel.showTypingSurface()
        viewModel.showMouseSurface()
        viewModel.showTypingSurface()
        advanceUntilIdle()

        assertEquals(1, connector.openControlSessionCalls)
    }

    @Test
    fun updatingTypingTextStoresText() = runTest(dispatcher) {
        val viewModel = PcMouseControlViewModel(FakeTokenStore(), FakeConnector(PcCommandResult.Ack), FakeMovementSizeStore())

        viewModel.updateTypingText("Hello")
        advanceUntilIdle()

        assertEquals("Hello", viewModel.uiState.value.typingText)
    }

    @Test
    fun emptyTypingTextDoesNotSend() = runTest(dispatcher) {
        PcConnectionStateHolder.setConnected(session, "Switchify PC")
        val connector = FakeConnector(PcCommandResult.Ack)
        val viewModel = PcMouseControlViewModel(FakeTokenStore(), connector, FakeMovementSizeStore())

        viewModel.sendTypedText()
        advanceUntilIdle()

        assertTrue(connector.commands.isEmpty())
    }

    @Test
    fun invalidTypingTextDoesNotSendAndShowsMessage() = runTest(dispatcher) {
        PcConnectionStateHolder.setConnected(session, "Switchify PC")
        val connector = FakeConnector(PcCommandResult.Ack)
        val viewModel = PcMouseControlViewModel(FakeTokenStore(), connector, FakeMovementSizeStore())

        viewModel.updateTypingText("hello\u001Bworld")
        viewModel.sendTypedText()
        advanceUntilIdle()

        assertTrue(connector.commands.isEmpty())
        assertEquals(PcMouseControlViewModel.TEXT_UNSUPPORTED_MESSAGE, viewModel.uiState.value.typingMessage)
    }

    @Test
    fun connectedStateSendsTypedTextCommand() = runTest(dispatcher) {
        PcConnectionStateHolder.setConnected(session, "Switchify PC")
        val connector = FakeConnector(PcCommandResult.Ack)
        val viewModel = PcMouseControlViewModel(FakeTokenStore(), connector, FakeMovementSizeStore())

        viewModel.showTypingSurface()
        viewModel.updateTypingText("Hello")
        viewModel.sendTypedText()
        advanceUntilIdle()

        assertEquals(PcControlCommand.TypeText("Hello"), connector.commands.single())
        assertEquals("", viewModel.uiState.value.typingText)
        assertEquals(PcControlSurface.Typing, viewModel.uiState.value.activeSurface)
        assertNull(viewModel.uiState.value.typingMessage)
    }

    @Test
    fun failedTypedTextShowsTypingFailureMessage() = runTest(dispatcher) {
        PcConnectionStateHolder.setConnected(session, "Switchify PC")
        val viewModel = PcMouseControlViewModel(FakeTokenStore(), FakeConnector(PcCommandResult.Failed()), FakeMovementSizeStore())

        viewModel.showTypingSurface()
        viewModel.updateTypingText("Hello")
        viewModel.sendTypedText()
        advanceUntilIdle()

        assertEquals(PcMouseControlViewModel.TYPING_FAILED_MESSAGE, viewModel.uiState.value.typingMessage)
        assertEquals("Hello", viewModel.uiState.value.typingText)
        assertEquals(PcControlSurface.Typing, viewModel.uiState.value.activeSurface)
    }

    @Test
    fun disconnectedTypedTextShowsConnectFirstAndDoesNotSend() = runTest(dispatcher) {
        val connector = FakeConnector(PcCommandResult.Ack)
        val viewModel = PcMouseControlViewModel(FakeTokenStore(), connector, FakeMovementSizeStore())

        viewModel.showTypingSurface()
        viewModel.updateTypingText("Hello")
        viewModel.sendTypedText()
        advanceUntilIdle()

        assertTrue(connector.commands.isEmpty())
        assertEquals(PcMouseControlViewModel.CONNECT_FIRST_MESSAGE, viewModel.uiState.value.message)
        assertEquals(PcMouseControlViewModel.CONNECT_FIRST_MESSAGE, viewModel.uiState.value.typingMessage)
    }

    @Test
    fun typedTextAuthFailureClearsTokenAndDisconnects() = runTest(dispatcher) {
        PcConnectionStateHolder.setConnected(session, "Switchify PC")
        val tokenStore = FakeTokenStore(mutableMapOf("desktop-1" to "token"))
        val viewModel = PcMouseControlViewModel(tokenStore, FakeConnector(PcCommandResult.AuthFailed()), FakeMovementSizeStore())

        viewModel.showTypingSurface()
        viewModel.updateTypingText("Hello")
        viewModel.sendTypedText()
        advanceUntilIdle()

        assertNull(tokenStore.getToken("desktop-1"))
        assertTrue(PcConnectionStateHolder.connectionState.value is PcConnectionState.Disconnected)
        assertEquals(PcMouseControlViewModel.CONNECT_FIRST_MESSAGE, viewModel.uiState.value.message)
        assertEquals(PcMouseControlViewModel.CONNECT_FIRST_MESSAGE, viewModel.uiState.value.typingMessage)
    }

    @Test
    fun sendKeySendsKeyboardKeyCommand() = runTest(dispatcher) {
        PcConnectionStateHolder.setConnected(session, "Switchify PC")
        val connector = FakeConnector(PcCommandResult.Ack)
        val viewModel = PcMouseControlViewModel(FakeTokenStore(), connector, FakeMovementSizeStore())

        viewModel.showTypingSurface()
        viewModel.sendKey(PcKeyboardKey.Enter)
        advanceUntilIdle()

        assertEquals(PcControlCommand.PressKey(PcKeyboardKey.Enter), connector.commands.single())
        assertNull(viewModel.uiState.value.typingMessage)
    }

    @Test
    fun failedKeyCommandShowsTypingFailureMessage() = runTest(dispatcher) {
        PcConnectionStateHolder.setConnected(session, "Switchify PC")
        val viewModel = PcMouseControlViewModel(FakeTokenStore(), FakeConnector(PcCommandResult.Failed()), FakeMovementSizeStore())

        viewModel.showTypingSurface()
        viewModel.sendKey(PcKeyboardKey.Backspace)
        advanceUntilIdle()

        assertEquals(PcMouseControlViewModel.KEY_FAILED_MESSAGE, viewModel.uiState.value.typingMessage)
    }

    @Test
    fun keyAuthFailureClearsTokenAndDisconnects() = runTest(dispatcher) {
        PcConnectionStateHolder.setConnected(session, "Switchify PC")
        val tokenStore = FakeTokenStore(mutableMapOf("desktop-1" to "token"))
        val viewModel = PcMouseControlViewModel(tokenStore, FakeConnector(PcCommandResult.AuthFailed()), FakeMovementSizeStore())

        viewModel.showTypingSurface()
        viewModel.sendKey(PcKeyboardKey.Delete)
        advanceUntilIdle()

        assertNull(tokenStore.getToken("desktop-1"))
        assertTrue(PcConnectionStateHolder.connectionState.value is PcConnectionState.Disconnected)
        assertEquals(PcMouseControlViewModel.CONNECT_FIRST_MESSAGE, viewModel.uiState.value.message)
        assertEquals(PcMouseControlViewModel.CONNECT_FIRST_MESSAGE, viewModel.uiState.value.typingMessage)
    }

    @Test
    fun ackClearsPreviousMessage() = runTest(dispatcher) {
        PcConnectionStateHolder.setConnected(session, "Switchify PC")
        val connector = FakeConnector(PcCommandResult.Ack)
        val viewModel = PcMouseControlViewModel(FakeTokenStore(), connector, FakeMovementSizeStore())

        viewModel.send(PcControlCommand.LeftClick)
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.message)
    }

    @Test
    fun failedCommandShowsFailureMessage() = runTest(dispatcher) {
        PcConnectionStateHolder.setConnected(session, "Switchify PC")
        val viewModel = PcMouseControlViewModel(FakeTokenStore(), FakeConnector(PcCommandResult.Failed()), FakeMovementSizeStore())

        viewModel.send(PcControlCommand.LeftClick)
        advanceUntilIdle()

        assertEquals(PcMouseControlViewModel.COMMAND_FAILED_MESSAGE, viewModel.uiState.value.message)
    }

    @Test
    fun authFailureClearsTokenAndDisconnects() = runTest(dispatcher) {
        PcConnectionStateHolder.setConnected(session, "Switchify PC")
        val tokenStore = FakeTokenStore(mutableMapOf("desktop-1" to "token"))
        val viewModel = PcMouseControlViewModel(tokenStore, FakeConnector(PcCommandResult.AuthFailed()), FakeMovementSizeStore(PcMouseMovementSize.Large))

        viewModel.send(PcControlCommand.LeftClick)
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

        viewModel.send(PcControlCommand.Scroll(0, 5))
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isBusy)
        assertEquals(PcControlCommand.Scroll(0, 5), viewModel.uiState.value.busyCommand)

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
        val commands = mutableListOf<PcControlCommand>()
        val oneShotCommands = mutableListOf<PcControlCommand>()

        override suspend fun requestApproval(pc: DiscoveredPc, requestNonce: String): PcPairingResult {
            return PcPairingResult.Failed("unused")
        }

        override suspend fun authenticatedPing(pc: DiscoveredPc, token: String): PcPingResult {
            return PcPingResult.Failed("unused")
        }

        override suspend fun openControlSession(session: PcAuthenticatedSession): PcLiveControlResult {
            openControlSessionCalls++
            return PcLiveControlResult.Connected(
                object : PcControlConnection {
                    override val pointerProfile: PcPointerMovementProfile? = this@FakeConnector.pointerProfile

                    override suspend fun sendCommand(command: PcControlCommand): PcCommandResult {
                        commands.add(command)
                        return deferredResult?.await() ?: commandResult
                    }

                    override fun close() = Unit
                }
            )
        }

        override suspend fun sendCommand(
            session: PcAuthenticatedSession,
            command: PcControlCommand
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

    private class FakeControlSurfaceStore(
        private var selectedSurface: PcControlSurface = PcControlSurface.Mouse
    ) : PcControlSurfaceStore {
        override fun getSelectedSurface(): PcControlSurface = selectedSurface

        override fun setSelectedSurface(surface: PcControlSurface) {
            selectedSurface = surface
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
