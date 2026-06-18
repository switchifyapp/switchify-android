package com.enaboapps.switchify.screens.pc

import com.enaboapps.switchify.pc.DiscoveredPc
import com.enaboapps.switchify.pc.PcAuthenticatedSession
import com.enaboapps.switchify.pc.PcBluetoothEndpoint
import com.enaboapps.switchify.pc.PcCommandResult
import com.enaboapps.switchify.pc.PcConnectionStateHolder
import com.enaboapps.switchify.pc.PcConnector
import com.enaboapps.switchify.pc.PcControlCloseReason
import com.enaboapps.switchify.pc.PcControlCommand
import com.enaboapps.switchify.pc.PcControlConnection
import com.enaboapps.switchify.pc.PcControlConnectionEvent
import com.enaboapps.switchify.pc.PcDeviceIdentity
import com.enaboapps.switchify.pc.PcDiscovery
import com.enaboapps.switchify.pc.PcDiscoveryStatus
import com.enaboapps.switchify.pc.PcErrorReason
import com.enaboapps.switchify.pc.PcKeyboardKey
import com.enaboapps.switchify.pc.PcLiveControlResult
import com.enaboapps.switchify.pc.PcPairingResult
import com.enaboapps.switchify.pc.PcPairingTokenStore
import com.enaboapps.switchify.pc.PcPingResult
import com.enaboapps.switchify.pc.PcPointerBounds
import com.enaboapps.switchify.pc.PcPointerDeltas
import com.enaboapps.switchify.pc.PcPointerMovementProfile
import com.enaboapps.switchify.pc.PcServiceConnectionController
import com.enaboapps.switchify.pc.PcStoredPairing
import com.enaboapps.switchify.pc.PcWindowControlAction
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
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
        Dispatchers.resetMain()
    }

    @Test
    fun directOpenWithoutControllerShowsConnectFirst() = runTest(dispatcher) {
        val viewModel = viewModel(null)

        viewModel.send(PcControlCommand.LeftClick)
        advanceUntilIdle()

        assertEquals(PcMouseControlViewModel.CONNECT_FIRST_MESSAGE, viewModel.uiState.value.message)
    }

    @Test
    fun directOpenWithoutLiveSessionShowsConnectFirst() = runTest(dispatcher) {
        val connector = FakeConnector()
        val controller = controller(FakeTokenStore(), connector)
        val viewModel = viewModel(controller)

        viewModel.send(PcControlCommand.LeftClick)
        advanceUntilIdle()

        assertTrue(connector.commands.isEmpty())
        assertEquals(PcMouseControlViewModel.CONNECT_FIRST_MESSAGE, viewModel.uiState.value.message)
    }

    @Test
    fun connectedServiceStateShowsDisplayName() = runTest(dispatcher) {
        val controller = connectedController()
        val viewModel = viewModel(controller)

        advanceUntilIdle()

        assertEquals("Switchify PC", viewModel.uiState.value.connectedDisplayName)
        assertNull(viewModel.uiState.value.message)
    }

    @Test
    fun connectedServiceStateUsesPointerProfile() = runTest(dispatcher) {
        val controller = connectedController(pointerProfile = pointerProfile(small = 52, medium = 130, large = 260))
        val viewModel = viewModel(controller, FakeMovementSizeStore(PcMouseMovementSize.Medium))

        advanceUntilIdle()

        assertEquals(130, viewModel.uiState.value.movementStep)
    }

    @Test
    fun firstRunDefaultsToSmallFallbackStep() = runTest(dispatcher) {
        val viewModel = viewModel(null)

        advanceUntilIdle()

        assertEquals(PcMouseMovementSize.Small, viewModel.uiState.value.selectedMovementSize)
        assertEquals(40, viewModel.uiState.value.movementStep)
    }

    @Test
    fun storedTypingSurfaceLoadsAsActiveSurface() = runTest(dispatcher) {
        val viewModel = viewModel(null, controlSurfaceStore = FakeControlSurfaceStore(PcControlSurface.Typing))

        advanceUntilIdle()

        assertEquals(PcControlSurface.Typing, viewModel.uiState.value.activeSurface)
    }

    @Test
    fun selectingMediumPersistsPreference() = runTest(dispatcher) {
        val movementSizeStore = FakeMovementSizeStore()
        val viewModel = viewModel(null, movementSizeStore)

        viewModel.selectMovementSize(PcMouseMovementSize.Medium)
        advanceUntilIdle()

        assertEquals(PcMouseMovementSize.Medium, movementSizeStore.getSelectedSize())
        assertEquals(80, viewModel.uiState.value.movementStep)
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
        assertEquals(PcControlSurface.Window, PcControlSurface.fromPreferenceValue("window"))
    }

    @Test
    fun showTypingSurfaceSwitchesActiveSurface() = runTest(dispatcher) {
        val surfaceStore = FakeControlSurfaceStore()
        val viewModel = viewModel(null, controlSurfaceStore = surfaceStore)

        viewModel.selectControlSurface(PcControlSurface.Typing)
        advanceUntilIdle()

        assertEquals(PcControlSurface.Typing, viewModel.uiState.value.activeSurface)
        assertEquals(PcControlSurface.Typing, surfaceStore.getSelectedSurface())
    }

    @Test
    fun updateTypingTextStoresText() = runTest(dispatcher) {
        val viewModel = viewModel(null)

        viewModel.updateTypingText("Hello")
        advanceUntilIdle()

        assertEquals("Hello", viewModel.uiState.value.typingText)
    }

    @Test
    fun invalidTypingTextDoesNotSendAndShowsMessage() = runTest(dispatcher) {
        val connector = FakeConnector()
        val controller = connectedController(connector = connector)
        val viewModel = viewModel(controller)

        viewModel.updateTypingText("hello\u001Bworld")
        viewModel.sendTypedText()
        advanceUntilIdle()

        assertTrue(connector.commands.isEmpty())
        assertEquals(PcMouseControlViewModel.TEXT_UNSUPPORTED_MESSAGE, viewModel.uiState.value.typingMessage)
    }

    @Test
    fun moveCommandDelegatesToRealtimeServiceController() = runTest(dispatcher) {
        val connector = FakeConnector()
        val controller = connectedController(connector = connector)
        val viewModel = viewModel(controller)

        viewModel.send(PcControlCommand.Move(80, 0))
        advanceUntilIdle()

        assertEquals(listOf(PcControlCommand.Move(80, 0)), connector.realtimeCommands)
        assertTrue(connector.commands.isEmpty())
        assertTrue(connector.oneShotCommands.isEmpty())
        assertTrue(!viewModel.uiState.value.isBusy)
        assertNull(viewModel.uiState.value.message)
    }

    @Test
    fun connectedStateSendsTypedTextCommand() = runTest(dispatcher) {
        val connector = FakeConnector()
        val controller = connectedController(connector = connector)
        val viewModel = viewModel(controller)

        viewModel.showTypingSurface()
        viewModel.updateTypingText("Hello")
        viewModel.sendTypedText()
        advanceUntilIdle()

        assertEquals(listOf(PcControlCommand.TypeText("Hello")), connector.realtimeCommands)
        assertTrue(connector.commands.isEmpty())
        assertEquals("", viewModel.uiState.value.typingText)
        assertNull(viewModel.uiState.value.typingMessage)
    }

    @Test
    fun connectedStateSendsWindowControlCommand() = runTest(dispatcher) {
        val connector = FakeConnector()
        val controller = connectedController(connector = connector)
        val viewModel = viewModel(controller)

        viewModel.selectControlSurface(PcControlSurface.Window)
        viewModel.send(PcControlCommand.WindowControl(PcWindowControlAction.SwitchNext))
        advanceUntilIdle()

        assertEquals(listOf(PcControlCommand.WindowControl(PcWindowControlAction.SwitchNext)), connector.realtimeCommands)
        assertTrue(connector.commands.isEmpty())
        assertEquals(PcControlSurface.Window, viewModel.uiState.value.activeSurface)
    }

    @Test
    fun controlCommandDoesNotSetBusy() = runTest(dispatcher) {
        val connector = FakeConnector()
        val controller = connectedController(connector = connector)
        val viewModel = viewModel(controller)

        viewModel.send(PcControlCommand.Scroll(0, 5))
        advanceUntilIdle()

        assertTrue(!viewModel.uiState.value.isBusy)
        assertNull(viewModel.uiState.value.busyCommand)
        assertEquals(listOf(PcControlCommand.Scroll(0, 5)), connector.realtimeCommands)
    }

    @Test
    fun repeatedMouseCommandsAreNotDroppedByBusyState() = runTest(dispatcher) {
        val connector = FakeConnector()
        val controller = connectedController(connector = connector)
        val viewModel = viewModel(controller)

        viewModel.send(PcControlCommand.Scroll(0, 5))
        runCurrent()
        viewModel.send(PcControlCommand.LeftClick)
        advanceUntilIdle()

        assertEquals(listOf(PcControlCommand.Scroll(0, 5), PcControlCommand.LeftClick), connector.realtimeCommands)
        assertTrue(!viewModel.uiState.value.isBusy)
    }

    @Test
    fun repeatedMoveCommandsAreNotDroppedByBusyState() = runTest(dispatcher) {
        val connector = FakeConnector()
        val controller = connectedController(connector = connector)
        val viewModel = viewModel(controller)

        viewModel.send(PcControlCommand.Scroll(0, 5))
        runCurrent()
        viewModel.send(PcControlCommand.Move(80, 0))
        viewModel.send(PcControlCommand.Move(0, 80))
        advanceUntilIdle()

        assertTrue(connector.commands.isEmpty())
        assertEquals(
            listOf(PcControlCommand.Scroll(0, 5), PcControlCommand.Move(80, 0), PcControlCommand.Move(0, 80)),
            connector.realtimeCommands
        )
        assertTrue(!viewModel.uiState.value.isBusy)
    }

    @Test
    fun windowCommandIsNotDroppedByBusyState() = runTest(dispatcher) {
        val connector = FakeConnector()
        val controller = connectedController(connector = connector)
        val viewModel = viewModel(controller)

        viewModel.send(PcControlCommand.Scroll(0, 5))
        runCurrent()
        viewModel.send(PcControlCommand.WindowControl(PcWindowControlAction.SwitchNext))
        advanceUntilIdle()

        assertEquals(
            listOf(PcControlCommand.Scroll(0, 5), PcControlCommand.WindowControl(PcWindowControlAction.SwitchNext)),
            connector.realtimeCommands
        )
    }

    @Test
    fun typedTextIsNotDroppedByBusyState() = runTest(dispatcher) {
        val connector = FakeConnector()
        val controller = connectedController(connector = connector)
        val viewModel = viewModel(controller)

        viewModel.send(PcControlCommand.Scroll(0, 5))
        runCurrent()
        viewModel.updateTypingText("Hello")
        viewModel.sendTypedText()
        advanceUntilIdle()

        assertEquals(
            listOf(PcControlCommand.Scroll(0, 5), PcControlCommand.TypeText("Hello")),
            connector.realtimeCommands
        )
    }

    @Test
    fun keyCommandIsNotDroppedByBusyState() = runTest(dispatcher) {
        val connector = FakeConnector()
        val controller = connectedController(connector = connector)
        val viewModel = viewModel(controller)

        viewModel.send(PcControlCommand.Scroll(0, 5))
        runCurrent()
        viewModel.sendKey(PcKeyboardKey.Enter)
        advanceUntilIdle()

        assertEquals(
            listOf(PcControlCommand.Scroll(0, 5), PcControlCommand.PressKey(PcKeyboardKey.Enter)),
            connector.realtimeCommands
        )
    }

    @Test
    fun commandFailureShowsCommandMessage() = runTest(dispatcher) {
        val controller = connectedController(connector = FakeConnector(commandResult = PcCommandResult.Failed()))
        val viewModel = viewModel(controller)

        viewModel.send(PcControlCommand.LeftClick)
        advanceUntilIdle()

        assertEquals(PcMouseControlViewModel.COMMAND_FAILED_MESSAGE, viewModel.uiState.value.message)
    }

    @Test
    fun commandAuthFailureShowsExpiredMessage() = runTest(dispatcher) {
        val controller = connectedController(connector = FakeConnector(commandResult = PcCommandResult.AuthFailed()))
        val viewModel = viewModel(controller)

        viewModel.send(PcControlCommand.LeftClick)
        advanceUntilIdle()

        assertEquals("Connection expired. Connect to PC from Switchify first.", viewModel.uiState.value.message)
    }

    @Test
    fun dragSendsDragStartWhenNotDragging() = runTest(dispatcher) {
        val connector = FakeConnector()
        val controller = connectedController(connector = connector)
        val viewModel = viewModel(controller)

        viewModel.send(PcControlCommand.DragStart())
        advanceUntilIdle()

        assertEquals(listOf(PcControlCommand.DragStart()), connector.realtimeCommands)
    }

    @Test
    fun dragAckSetsDraggingTrue() = runTest(dispatcher) {
        val controller = connectedController()
        val viewModel = viewModel(controller)

        viewModel.send(PcControlCommand.DragStart())
        advanceUntilIdle()

        assertEquals(true, viewModel.uiState.value.isDragging)
    }

    @Test
    fun dragSendsDragEndWhenDragging() = runTest(dispatcher) {
        val connector = FakeConnector()
        val controller = connectedController(connector = connector)
        val viewModel = viewModel(controller)

        viewModel.send(PcControlCommand.DragStart())
        advanceUntilIdle()
        viewModel.send(PcControlCommand.DragStart())
        advanceUntilIdle()

        assertEquals(
            listOf(PcControlCommand.DragStart(), PcControlCommand.DragEnd()),
            connector.realtimeCommands
        )
    }

    @Test
    fun dragEndAckSetsDraggingFalse() = runTest(dispatcher) {
        val controller = connectedController()
        val viewModel = viewModel(controller)

        viewModel.send(PcControlCommand.DragStart())
        advanceUntilIdle()
        viewModel.send(PcControlCommand.DragStart())
        advanceUntilIdle()

        assertEquals(false, viewModel.uiState.value.isDragging)
    }

    @Test
    fun dragFailureDoesNotFlipDragState() = runTest(dispatcher) {
        val connector = FakeConnector(commandResult = PcCommandResult.Failed())
        val controller = connectedController(connector = connector)
        val viewModel = viewModel(controller)

        viewModel.send(PcControlCommand.DragStart())
        advanceUntilIdle()

        assertEquals(false, viewModel.uiState.value.isDragging)
        assertEquals(PcMouseControlViewModel.COMMAND_FAILED_MESSAGE, viewModel.uiState.value.message)
    }

    @Test
    fun reconnectResetsDraggingFalse() = runTest(dispatcher) {
        val controller = connectedController()
        val viewModel = viewModel(controller)
        val session = PcAuthenticatedSession("desktop-1", "device-1", "AA:BB:CC:DD:EE:FF")

        viewModel.send(PcControlCommand.DragStart())
        advanceUntilIdle()
        PcConnectionStateHolder.setReconnecting(session, "Switchify PC")
        advanceUntilIdle()

        assertEquals(false, viewModel.uiState.value.isDragging)
    }

    @Test
    fun disconnectResetsDraggingFalse() = runTest(dispatcher) {
        val controller = connectedController()
        val viewModel = viewModel(controller)

        viewModel.send(PcControlCommand.DragStart())
        advanceUntilIdle()
        controller.disconnect()
        advanceUntilIdle()

        assertEquals(false, viewModel.uiState.value.isDragging)
    }

    @Test
    fun reconnectingServiceStateShowsReconnecting() = runTest(dispatcher) {
        val session = PcAuthenticatedSession("desktop-1", "device-1", "AA:BB:CC:DD:EE:FF")
        val viewModel = viewModel(null)

        PcConnectionStateHolder.setReconnecting(session, "Switchify PC")
        advanceUntilIdle()

        assertEquals(PcMouseControlViewModel.RECONNECTING_MESSAGE, viewModel.uiState.value.message)
    }

    @Test
    fun onResumeAndPauseDelegateToControllerLifecycle() = runTest(dispatcher) {
        val connector = FakeConnector()
        val controller = connectedController(connector = connector)
        val viewModel = viewModel(controller)

        viewModel.onPcUiResumed()
        viewModel.onPcUiPaused()
        runCurrent()

        assertEquals(0, connector.openedConnections.single().closeCalls)
    }

    private suspend fun connectedController(
        connector: FakeConnector = FakeConnector(),
        pointerProfile: PcPointerMovementProfile? = null
    ): PcServiceConnectionController {
        connector.pointerProfile = pointerProfile
        val tokens = FakeTokenStore(mutableMapOf("desktop-1" to "token"))
        val controller = controller(tokens, connector)
        controller.connectTo(pc)
        return controller
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

    private fun viewModel(
        controller: PcServiceConnectionController?,
        movementSizeStore: FakeMovementSizeStore = FakeMovementSizeStore(),
        controlSurfaceStore: FakeControlSurfaceStore = FakeControlSurfaceStore()
    ): PcMouseControlViewModel {
        return PcMouseControlViewModel(
            serviceControllerProvider = { controller },
            movementSizeStore = movementSizeStore,
            controlSurfaceStore = controlSurfaceStore
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
        private val lastEndpointIds = mutableMapOf<String, String>()

        override fun getToken(desktopId: String): String? = tokens[desktopId]

        override fun saveToken(desktopId: String, token: String, lastEndpointId: String, serviceName: String?) {
            tokens[desktopId] = token
            lastEndpointIds[desktopId] = lastEndpointId
        }

        override fun clearToken(desktopId: String) {
            tokens.remove(desktopId)
            lastEndpointIds.remove(desktopId)
        }

        override fun listPairings(): List<PcStoredPairing> {
            return tokens.keys.map { desktopId ->
                PcStoredPairing(
                    desktopId = desktopId,
                    serviceName = null,
                    lastEndpointId = lastEndpointIds[desktopId]
                )
            }
        }

        override fun getLastEndpointId(desktopId: String): String? = lastEndpointIds[desktopId]
        override fun getServiceName(desktopId: String): String? = null
    }

    private object FakeIdentity : PcDeviceIdentity {
        override fun getDeviceId(): String = "device-1"
        override fun getDeviceName(): String = "Android phone"
    }

    private class FakeConnector(
        private val pingResult: PcPingResult = PcPingResult.Connected("AA:BB:CC:DD:EE:FF"),
        private val pairingResult: PcPairingResult = PcPairingResult.Failed(PcErrorReason.Failed, "unused"),
        private val commandResult: Any = PcCommandResult.Ack,
        val liveResults: MutableList<PcLiveControlResult> = mutableListOf()
    ) : PcConnector {
        var pointerProfile: PcPointerMovementProfile? = null
        var pingCalls = 0
        var pairingCalls = 0
        var openControlSessionCalls = 0
        var closeCalls = 0
        val commands = mutableListOf<PcControlCommand>()
        val realtimeCommands = mutableListOf<PcControlCommand>()
        val oneShotCommands = mutableListOf<PcControlCommand>()
        val openedConnections = mutableListOf<FakeLiveConnection>()

        override suspend fun requestApproval(pc: DiscoveredPc, requestNonce: String): PcPairingResult {
            pairingCalls++
            return pairingResult
        }

        override suspend fun authenticatedPing(pc: DiscoveredPc, token: String): PcPingResult {
            pingCalls++
            return pingResult
        }

        override suspend fun openControlSession(session: PcAuthenticatedSession): PcLiveControlResult {
            openControlSessionCalls++
            if (liveResults.isNotEmpty()) return liveResults.removeAt(0)
            val connection = FakeLiveConnection(
                pointerProfile = pointerProfile,
                onCommand = { command ->
                    commands.add(command)
                    when (commandResult) {
                        is CompletableDeferred<*> -> commandResult.await() as PcCommandResult
                        is PcCommandResult -> commandResult
                        else -> PcCommandResult.Ack
                    }
                },
                onRealtimeCommand = { command ->
                    realtimeCommands.add(command)
                    when (commandResult) {
                        is CompletableDeferred<*> -> commandResult.await() as PcCommandResult
                        is PcCommandResult -> commandResult
                        else -> PcCommandResult.Ack
                    }
                }
            )
            openedConnections.add(connection)
            return PcLiveControlResult.Connected(connection)
        }

        override suspend fun sendCommand(session: PcAuthenticatedSession, command: PcControlCommand): PcCommandResult {
            oneShotCommands.add(command)
            return PcCommandResult.Ack
        }

        override fun close() {
            closeCalls++
        }
    }

    private class FakeLiveConnection(
        override val pointerProfile: PcPointerMovementProfile? = null,
        private val onCommand: suspend (PcControlCommand) -> PcCommandResult = { PcCommandResult.Ack },
        private val onRealtimeCommand: suspend (PcControlCommand) -> PcCommandResult = { PcCommandResult.Ack },
        private val onHealth: suspend () -> PcCommandResult = { PcCommandResult.Ack }
    ) : PcControlConnection {
        val eventsFlow = MutableSharedFlow<PcControlConnectionEvent>(replay = 1, extraBufferCapacity = 8)
        override val connectionEvents = eventsFlow
        var closeCalls = 0
        val closeReasons = mutableListOf<PcControlCloseReason>()
        val realtimeCommands = mutableListOf<PcControlCommand>()

        override suspend fun checkHealth(): PcCommandResult = onHealth()

        override suspend fun sendCommand(command: PcControlCommand): PcCommandResult = onCommand(command)

        override suspend fun sendRealtimeCommand(command: PcControlCommand): PcCommandResult {
            realtimeCommands += command
            return onRealtimeCommand(command)
        }

        override fun close(reason: PcControlCloseReason) {
            closeCalls++
            closeReasons += reason
        }
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
}
