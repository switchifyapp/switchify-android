package com.enaboapps.switchify.screens.pc

import com.enaboapps.switchify.pc.DiscoveredPc
import com.enaboapps.switchify.pc.PcAuthenticatedSession
import com.enaboapps.switchify.pc.PcBluetoothEndpoint
import com.enaboapps.switchify.pc.PcCommandResult
import com.enaboapps.switchify.pc.PcConnectionState
import com.enaboapps.switchify.pc.PcConnectionStateHolder
import com.enaboapps.switchify.pc.PcConnector
import com.enaboapps.switchify.pc.PcControlCloseReason
import com.enaboapps.switchify.pc.PcControlCommand
import com.enaboapps.switchify.pc.PcControlConnection
import com.enaboapps.switchify.pc.PcControlConnectionEvent
import com.enaboapps.switchify.pc.PcDeviceIdentity
import com.enaboapps.switchify.pc.PcDiscovery
import com.enaboapps.switchify.pc.PcDiscoveryStatus
import com.enaboapps.switchify.pc.PcDisplayNavigationCapabilities
import com.enaboapps.switchify.pc.PcErrorReason
import com.enaboapps.switchify.pc.PcKeyboardKey
import com.enaboapps.switchify.pc.PcKeyboardModifierKey
import com.enaboapps.switchify.pc.PcKeyboardShortcutKey
import com.enaboapps.switchify.pc.PcLiveControlResult
import com.enaboapps.switchify.pc.PcMouseRepeatManager
import com.enaboapps.switchify.pc.PcMouseRepeatSettings
import com.enaboapps.switchify.pc.PcPairingResult
import com.enaboapps.switchify.pc.PcPairingTokenStore
import com.enaboapps.switchify.pc.PcPingResult
import com.enaboapps.switchify.pc.PcPointerBounds
import com.enaboapps.switchify.pc.PcPointerCapabilities
import com.enaboapps.switchify.pc.PcPointerDeltas
import com.enaboapps.switchify.pc.PcPointerMovementProfile
import com.enaboapps.switchify.pc.PcPointerSpeedCapabilities
import com.enaboapps.switchify.pc.PcServiceConnectionController
import com.enaboapps.switchify.pc.PcStoredPairing
import com.enaboapps.switchify.pc.PcWindowControlAction
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PcMouseControlViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val mouseRepeatManager = PcMouseRepeatManager.instance
    private val mouseRepeatSettings = FakeMouseRepeatSettings()
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
    private val officePc = DiscoveredPc(
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
        mouseRepeatManager.resetForTesting()
        mouseRepeatSettings.enabled = true
        mouseRepeatSettings.intervalMs = 250L
        mouseRepeatManager.setSettingsForTesting(mouseRepeatSettings)
        mouseRepeatManager.setHudMessageHandlerForTesting { _, _ -> }
    }

    @After
    fun tearDown() {
        mouseRepeatManager.resetForTesting()
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
    fun bottomStripStateUsesControlDeviceName() = runTest(dispatcher) {
        val devicePc = pc.copy(
            serviceName = "Switchify PC",
            bluetoothEndpoint = pc.bluetoothEndpoint?.copy(deviceName = "Oliver Laptop")
        )
        val connector = FakeConnector()
        val tokens = FakeTokenStore(mutableMapOf("desktop-1" to "token"))
        val controller = controller(tokens, connector, FakeDiscovery(listOf(devicePc)))
        controller.connectTo(devicePc)
        val viewModel = viewModel(controller)

        advanceUntilIdle()

        assertEquals("Oliver Laptop", viewModel.uiState.value.connectedDisplayName)
        assertEquals("Oliver Laptop", viewModel.uiState.value.switcherConnectedDisplayName)
    }

    @Test
    fun connectedServiceStateUsesPointerProfile() = runTest(dispatcher) {
        val controller = connectedController(
            pointerProfile = pointerProfile(
                medium = 130,
                capabilities = PcPointerCapabilities(
                    pointerSpeed = PcPointerSpeedCapabilities(
                        supported = true,
                        setSupported = true,
                        scalePercent = 125.0,
                        baseMoveDelta = 128,
                        effectiveMoveDelta = 160
                    )
                )
            )
        )
        val viewModel = viewModel(controller)

        advanceUntilIdle()

        assertEquals(128, viewModel.uiState.value.movementStep)
        assertEquals(true, viewModel.uiState.value.pointerSpeedSupported)
        assertEquals("125%", viewModel.uiState.value.pointerSpeedPercentLabel)
    }

    @Test
    fun connectedServiceStateExposesAdvertisedDisplayNavigation() = runTest(dispatcher) {
        val controller = connectedController(
            pointerProfile = pointerProfile(
                capabilities = PcPointerCapabilities(
                    supportedCommands = setOf("pointer.display.move"),
                    displayNavigation = PcDisplayNavigationCapabilities(
                        supported = true,
                        displayCount = 3
                    )
                )
            )
        )
        val viewModel = viewModel(controller)

        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.displayNavigationSupported)
        assertEquals(3, viewModel.uiState.value.displayCount)
    }

    @Test
    fun pointerSpeedSetSupportEnablesSpeedEditing() = runTest(dispatcher) {
        val controller = connectedController(
            pointerProfile = pointerProfile(
                capabilities = PcPointerCapabilities(
                    pointerSpeed = PcPointerSpeedCapabilities(
                        supported = true,
                        setSupported = true,
                        scalePercent = 160.0,
                        minScalePercent = 5.0,
                        maxScalePercent = 225.0,
                        stepPercent = 5.0,
                        baseMoveDelta = 128,
                        effectiveMoveDelta = 205
                    )
                )
            )
        )
        val viewModel = viewModel(controller)

        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.pointerSpeedSupported)
        assertTrue(viewModel.uiState.value.pointerSpeedSetSupported)
        assertEquals(160.0, viewModel.uiState.value.pointerSpeedScalePercent, 0.0)
        assertEquals(5.0, viewModel.uiState.value.pointerSpeedMinScalePercent, 0.0)
        assertEquals(225.0, viewModel.uiState.value.pointerSpeedMaxScalePercent, 0.0)
        assertEquals(5.0, viewModel.uiState.value.pointerSpeedStepPercent, 0.0)
    }

    @Test
    fun settingPointerSpeedSendsNormalizedValueAndUpdatesAfterAck() = runTest(dispatcher) {
        val connector = FakeConnector()
        val controller = connectedController(
            connector = connector,
            pointerProfile = pointerProfile(
                capabilities = PcPointerCapabilities(
                    pointerSpeed = PcPointerSpeedCapabilities(
                        supported = true,
                        setSupported = true,
                        scalePercent = 100.0,
                        minScalePercent = 5.0,
                        maxScalePercent = 225.0,
                        stepPercent = 5.0,
                        baseMoveDelta = 128,
                        effectiveMoveDelta = 128
                    )
                )
            )
        )
        val viewModel = viewModel(controller)
        advanceUntilIdle()

        viewModel.setPointerSpeed(127.0)
        advanceUntilIdle()

        assertEquals(listOf(PcControlCommand.SetPointerSpeed(125.0)), connector.commands)
        assertEquals(125.0, viewModel.uiState.value.pointerSpeedScalePercent, 0.0)
        assertEquals("125%", viewModel.uiState.value.pointerSpeedPercentLabel)
        assertEquals(128, viewModel.uiState.value.movementStep)
    }

    @Test
    fun settingPointerSpeedClampsToFivePercentMinimum() = runTest(dispatcher) {
        val connector = FakeConnector()
        val controller = connectedController(
            connector = connector,
            pointerProfile = pointerProfile(
                capabilities = PcPointerCapabilities(
                    pointerSpeed = PcPointerSpeedCapabilities(
                        supported = true,
                        setSupported = true,
                        scalePercent = 100.0,
                        minScalePercent = 5.0,
                        maxScalePercent = 225.0,
                        stepPercent = 5.0,
                        baseMoveDelta = 128,
                        effectiveMoveDelta = 128
                    )
                )
            )
        )
        val viewModel = viewModel(controller)
        advanceUntilIdle()

        viewModel.setPointerSpeed(1.0)
        advanceUntilIdle()

        assertEquals(listOf(PcControlCommand.SetPointerSpeed(5.0)), connector.commands)
        assertEquals(5.0, viewModel.uiState.value.pointerSpeedScalePercent, 0.0)
        assertEquals("5%", viewModel.uiState.value.pointerSpeedPercentLabel)
    }

    @Test
    fun failedPointerSpeedSetDoesNotUpdateDisplayedSpeed() = runTest(dispatcher) {
        val connector = FakeConnector(commandResults = mutableListOf(PcCommandResult.Failed()))
        val controller = connectedController(
            connector = connector,
            pointerProfile = pointerProfile(
                capabilities = PcPointerCapabilities(
                    pointerSpeed = PcPointerSpeedCapabilities(
                        supported = true,
                        setSupported = true,
                        scalePercent = 100.0,
                        minScalePercent = 5.0,
                        maxScalePercent = 225.0,
                        stepPercent = 5.0,
                        baseMoveDelta = 128,
                        effectiveMoveDelta = 128
                    )
                )
            )
        )
        val viewModel = viewModel(controller)
        advanceUntilIdle()

        viewModel.setPointerSpeed(160.0)
        advanceUntilIdle()

        assertEquals(listOf(PcControlCommand.SetPointerSpeed(160.0)), connector.commands)
        assertEquals(100.0, viewModel.uiState.value.pointerSpeedScalePercent, 0.0)
        assertEquals("100%", viewModel.uiState.value.pointerSpeedPercentLabel)
        assertEquals(PcMouseControlViewModel.COMMAND_FAILED_MESSAGE, viewModel.uiState.value.message)
    }

    @Test
    fun unsupportedPointerSpeedSetDoesNotSendCommand() = runTest(dispatcher) {
        val connector = FakeConnector()
        val controller = connectedController(
            connector = connector,
            pointerProfile = pointerProfile(
                capabilities = PcPointerCapabilities(
                    pointerSpeed = PcPointerSpeedCapabilities(
                        supported = true,
                        setSupported = false,
                        scalePercent = 100.0
                    )
                )
            )
        )
        val viewModel = viewModel(controller)
        advanceUntilIdle()

        viewModel.setPointerSpeed(160.0)
        advanceUntilIdle()

        assertTrue(connector.commands.isEmpty())
        assertEquals(PcMouseControlViewModel.POINTER_SPEED_UNAVAILABLE_MESSAGE, viewModel.uiState.value.message)
    }

    @Test
    fun connectedServiceStateReportsTextStreamSupport() = runTest(dispatcher) {
        val controller = connectedController(pointerProfile = textStreamPointerProfile())
        val viewModel = viewModel(controller)

        advanceUntilIdle()

        assertEquals(true, viewModel.uiState.value.supportsTextStreamInput)
    }

    @Test
    fun openSwitchPcChooserDiscoversPairedPcs() = runTest(dispatcher) {
        val controller = pairedController()
        val viewModel = viewModel(controller)
        advanceUntilIdle()

        viewModel.openSwitchPcChooser()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.switchPcChooserVisible)
        assertEquals(listOf("desktop-1", "desktop-2"), viewModel.uiState.value.switchPcRows.map { it.desktopId })
        assertTrue(viewModel.uiState.value.switchPcRows.first { it.desktopId == "desktop-1" }.connected)
        assertFalse(viewModel.uiState.value.switchPcRows.first { it.desktopId == "desktop-2" }.connected)
    }

    @Test
    fun switchRowsUseActualBluetoothDeviceName() = runTest(dispatcher) {
        val devicePc = pc.copy(
            serviceName = "Switchify PC",
            bluetoothEndpoint = pc.bluetoothEndpoint?.copy(deviceName = "Oliver Laptop")
        )
        val connector = FakeConnector()
        val tokens = FakeTokenStore(mutableMapOf("desktop-1" to "token"))
        val controller = controller(tokens, connector, FakeDiscovery(listOf(devicePc)))
        controller.connectTo(devicePc)
        val viewModel = viewModel(controller)
        advanceUntilIdle()

        viewModel.openSwitchPcChooser()
        advanceUntilIdle()

        assertEquals("Oliver Laptop", viewModel.uiState.value.switchPcRows.single().displayName)
    }

    @Test
    fun openSwitchPcChooserShowsEmptyWhenNoPairedPcsNearby() = runTest(dispatcher) {
        val connector = FakeConnector()
        val tokens = FakeTokenStore(mutableMapOf("desktop-1" to "token"))
        val controller = controller(tokens, connector, FakeDiscovery(emptyList()))
        val viewModel = viewModel(controller)

        viewModel.openSwitchPcChooser()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.switchPcChooserVisible)
        assertTrue(viewModel.uiState.value.switchPcRows.isEmpty())
        assertFalse(viewModel.uiState.value.isDiscoveringSwitchPcs)
    }

    @Test
    fun switchToPcConnectsSelectedPairedPc() = runTest(dispatcher) {
        val connector = FakeConnector()
        val controller = pairedController(connector)
        val viewModel = viewModel(controller)
        advanceUntilIdle()

        viewModel.openSwitchPcChooser()
        advanceUntilIdle()
        viewModel.switchToPc("desktop-2")
        advanceUntilIdle()

        val state = PcConnectionStateHolder.connectionState.value as PcConnectionState.Connected
        assertEquals("desktop-2", state.session.desktopId)
        assertEquals("Office PC", viewModel.uiState.value.connectedDisplayName)
        assertFalse(viewModel.uiState.value.switchPcChooserVisible)
        assertNull(viewModel.uiState.value.switchingDesktopId)
        assertEquals(2, connector.openControlSessionCalls)
    }

    @Test
    fun switchToCurrentPcDoesNotReconnect() = runTest(dispatcher) {
        val connector = FakeConnector()
        val controller = pairedController(connector)
        val viewModel = viewModel(controller)
        advanceUntilIdle()

        viewModel.openSwitchPcChooser()
        advanceUntilIdle()
        viewModel.switchToPc("desktop-1")
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.switchPcChooserVisible)
        assertEquals(1, connector.openControlSessionCalls)
    }

    @Test
    fun switchToPcStopsMouseRepeatAndDragging() = runTest(dispatcher) {
        val connector = FakeConnector()
        val controller = pairedController(connector)
        val viewModel = viewModel(controller)
        advanceUntilIdle()
        viewModel.openSwitchPcChooser()
        advanceUntilIdle()

        viewModel.sendMouseCommand(PcControlCommand.Move(80, 0), repeatable = true)
        viewModel.send(PcControlCommand.DragStart())
        runCurrent()
        assertTrue(mouseRepeatManager.isRepeating())
        assertTrue(viewModel.uiState.value.isDragging)

        viewModel.switchToPc("desktop-2")
        advanceUntilIdle()

        assertFalse(mouseRepeatManager.isRepeating())
        assertFalse(viewModel.uiState.value.isDragging)
    }

    @Test
    fun switchToPcFailureKeepsChooserOpenAndShowsMessage() = runTest(dispatcher) {
        val connector = FakeConnector(liveResults = mutableListOf(PcLiveControlResult.Connected(FakeLiveConnection()), PcLiveControlResult.Failed("Could not connect.")))
        val controller = pairedController(connector)
        val viewModel = viewModel(controller)
        advanceUntilIdle()

        viewModel.openSwitchPcChooser()
        advanceUntilIdle()
        viewModel.switchToPc("desktop-2")
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.switchPcChooserVisible)
        assertNull(viewModel.uiState.value.switchingDesktopId)
        assertEquals("Could not connect.", viewModel.uiState.value.message)
    }

    @Test
    fun switchToPcApprovalCodeIsExposedWhenTokenExpired() = runTest(dispatcher) {
        val pairingDeferred = CompletableDeferred<PcPairingResult>()
        val connector = FakeConnector(
            liveResults = mutableListOf(PcLiveControlResult.Connected(FakeLiveConnection())),
            pingResultsByDesktop = mapOf("desktop-2" to PcPingResult.AuthFailed()),
            pairingResultsByDesktop = mapOf("desktop-2" to pairingDeferred)
        )
        val controller = pairedController(connector)
        val viewModel = viewModel(controller)
        advanceUntilIdle()

        viewModel.openSwitchPcChooser()
        advanceUntilIdle()
        viewModel.switchToPc("desktop-2")
        runCurrent()

        assertEquals("Office PC", viewModel.uiState.value.switchPcApprovalCode?.pcName)
        assertEquals("838981", viewModel.uiState.value.switchPcApprovalCode?.verificationCode)

        pairingDeferred.complete(PcPairingResult.Paired("desktop-2", "new-token", "11:22:33:44:55:66"))
        advanceUntilIdle()
    }

    @Test
    fun cancelSwitchPcPairingDismissesApprovalAndClearsSwitchingState() = runTest(dispatcher) {
        val pairingDeferred = CompletableDeferred<PcPairingResult>()
        val connector = FakeConnector(
            liveResults = mutableListOf(PcLiveControlResult.Connected(FakeLiveConnection())),
            pingResultsByDesktop = mapOf("desktop-2" to PcPingResult.AuthFailed()),
            pairingResultsByDesktop = mapOf("desktop-2" to pairingDeferred)
        )
        val controller = pairedController(connector)
        val viewModel = viewModel(controller)
        advanceUntilIdle()

        viewModel.openSwitchPcChooser()
        advanceUntilIdle()
        viewModel.switchToPc("desktop-2")
        runCurrent()
        viewModel.cancelSwitchPcPairing()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.switchPcApprovalCode)
        assertNull(viewModel.uiState.value.switchingDesktopId)
        assertTrue(viewModel.uiState.value.switchPcChooserVisible)
        assertEquals("Switchify PC", viewModel.uiState.value.connectedDisplayName)
        assertTrue(viewModel.uiState.value.switchPcRows.first { it.desktopId == "desktop-2" }.enabled)
    }

    @Test
    fun dismissSwitchPcChooserClearsSwitchState() = runTest(dispatcher) {
        val pairingDeferred = CompletableDeferred<PcPairingResult>()
        val connector = FakeConnector(
            liveResults = mutableListOf(PcLiveControlResult.Connected(FakeLiveConnection())),
            pingResultsByDesktop = mapOf("desktop-2" to PcPingResult.AuthFailed()),
            pairingResultsByDesktop = mapOf("desktop-2" to pairingDeferred)
        )
        val controller = pairedController(connector)
        val viewModel = viewModel(controller)
        advanceUntilIdle()

        viewModel.openSwitchPcChooser()
        advanceUntilIdle()
        viewModel.switchToPc("desktop-2")
        runCurrent()
        viewModel.dismissSwitchPcChooser()

        assertFalse(viewModel.uiState.value.switchPcChooserVisible)
        assertNull(viewModel.uiState.value.switchPcApprovalCode)
        assertNull(viewModel.uiState.value.switchingDesktopId)

        pairingDeferred.complete(PcPairingResult.Paired("desktop-2", "new-token", "11:22:33:44:55:66"))
        advanceUntilIdle()
    }

    @Test
    fun cancelSwitchPcPairingDoesNotReplaceCurrentConnectionWhenPairingCompletesLater() = runTest(dispatcher) {
        val pairingDeferred = CompletableDeferred<PcPairingResult>()
        val connector = FakeConnector(
            liveResults = mutableListOf(PcLiveControlResult.Connected(FakeLiveConnection())),
            pingResultsByDesktop = mapOf("desktop-2" to PcPingResult.AuthFailed()),
            pairingResultsByDesktop = mapOf("desktop-2" to pairingDeferred)
        )
        val controller = pairedController(connector)
        val viewModel = viewModel(controller)
        advanceUntilIdle()

        viewModel.openSwitchPcChooser()
        advanceUntilIdle()
        viewModel.switchToPc("desktop-2")
        runCurrent()
        viewModel.cancelSwitchPcPairing()
        pairingDeferred.complete(PcPairingResult.Paired("desktop-2", "new-token", "11:22:33:44:55:66"))
        advanceUntilIdle()

        assertEquals("Switchify PC", viewModel.uiState.value.connectedDisplayName)
        assertNull(viewModel.uiState.value.switchingDesktopId)
        assertNull(viewModel.uiState.value.switchPcApprovalCode)
    }

    @Test
    fun firstRunDefaultsToMediumFallbackStep() = runTest(dispatcher) {
        val viewModel = viewModel(null)

        advanceUntilIdle()

        assertEquals(80, viewModel.uiState.value.movementStep)
        assertEquals(false, viewModel.uiState.value.pointerSpeedSupported)
        assertEquals(
            "Update Switchify PC to set pointer speed.",
            viewModel.uiState.value.pointerSpeedPercentLabel
        )
    }

    @Test
    fun storedTypingSurfaceLoadsAsActiveSurface() = runTest(dispatcher) {
        val viewModel = viewModel(null, controlSurfaceStore = FakeControlSurfaceStore(PcControlSurface.Typing))

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
    fun typingDraftRestoresFromStore() = runTest(dispatcher) {
        val viewModel = viewModel(null, typingDraftStore = FakeTypingDraftStore("Hello"))

        advanceUntilIdle()

        assertEquals("Hello", viewModel.uiState.value.typingText)
    }

    @Test
    fun typingTextChangesAreSavedToDraftStore() = runTest(dispatcher) {
        val draftStore = FakeTypingDraftStore()
        val viewModel = viewModel(null, typingDraftStore = draftStore)

        viewModel.updateTypingText("Hello")
        advanceUntilIdle()

        assertEquals("Hello", draftStore.getDraft())
    }

    @Test
    fun clearTypingTextClearsDraftStore() = runTest(dispatcher) {
        val draftStore = FakeTypingDraftStore("Hello")
        val viewModel = viewModel(null, typingDraftStore = draftStore)

        viewModel.clearTypingText()
        advanceUntilIdle()

        assertEquals("", viewModel.uiState.value.typingText)
        assertEquals("", draftStore.getDraft())
    }

    @Test
    fun restoredInvalidDraftShowsValidationMessage() = runTest(dispatcher) {
        val viewModel = viewModel(null, typingDraftStore = FakeTypingDraftStore("hello\u001Bworld"))

        advanceUntilIdle()

        assertEquals("hello\u001Bworld", viewModel.uiState.value.typingText)
        assertEquals(PcMouseControlViewModel.TEXT_UNSUPPORTED_MESSAGE, viewModel.uiState.value.typingMessage)
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
    fun repeatableMouseCommandSendsImmediatelyThenRepeats() = runTest(dispatcher) {
        val connector = FakeConnector()
        val controller = connectedController(connector = connector)
        val viewModel = viewModel(controller)

        viewModel.sendMouseCommand(PcControlCommand.Move(80, 0), repeatable = true)

        assertEquals(listOf(PcControlCommand.Move(80, 0)), connector.realtimeCommands)

        advanceTimeBy(249)
        runCurrent()

        assertEquals(listOf(PcControlCommand.Move(80, 0)), connector.realtimeCommands)

        advanceTimeBy(1)
        runCurrent()

        assertEquals(
            listOf(
                PcControlCommand.Move(80, 0),
                PcControlCommand.Move(80, 0)
            ),
            connector.realtimeCommands
        )
        mouseRepeatManager.stop(showMessage = false)
    }

    @Test
    fun repeatableScrollCommandSendsImmediatelyThenRepeats() = runTest(dispatcher) {
        val connector = FakeConnector()
        val controller = connectedController(connector = connector)
        val viewModel = viewModel(controller)

        viewModel.sendMouseCommand(PcControlCommand.Scroll(0, 5), repeatable = true)

        assertEquals(listOf(PcControlCommand.Scroll(0, 5)), connector.realtimeCommands)

        advanceTimeBy(249)
        runCurrent()

        assertEquals(listOf(PcControlCommand.Scroll(0, 5)), connector.realtimeCommands)

        advanceTimeBy(1)
        runCurrent()

        assertEquals(
            listOf(
                PcControlCommand.Scroll(0, 5),
                PcControlCommand.Scroll(0, 5)
            ),
            connector.realtimeCommands
        )
        mouseRepeatManager.stop(showMessage = false)
    }

    @Test
    fun disabledMouseRepeatSendsOnlyOnce() = runTest(dispatcher) {
        mouseRepeatSettings.enabled = false
        val connector = FakeConnector()
        val controller = connectedController(connector = connector)
        val viewModel = viewModel(controller)

        viewModel.sendMouseCommand(PcControlCommand.Scroll(0, 5), repeatable = true)
        runCurrent()
        advanceTimeBy(250)
        runCurrent()

        assertEquals(listOf(PcControlCommand.Scroll(0, 5)), connector.realtimeCommands)
    }

    @Test
    fun nonRepeatableMouseCommandSendsOnlyOnce() = runTest(dispatcher) {
        val connector = FakeConnector()
        val controller = connectedController(connector = connector)
        val viewModel = viewModel(controller)

        viewModel.sendMouseCommand(PcControlCommand.LeftClick, repeatable = false)
        runCurrent()
        advanceTimeBy(250)
        runCurrent()

        assertEquals(listOf(PcControlCommand.LeftClick), connector.realtimeCommands)
    }

    @Test
    fun repeatableMouseCommandIsStoppableBeforeFirstAck() = runTest(dispatcher) {
        val firstResult = CompletableDeferred<PcCommandResult>()
        val connector = FakeConnector(realtimeResults = mutableListOf(firstResult))
        val controller = connectedController(connector = connector)
        val viewModel = viewModel(controller)
        val command = PcControlCommand.Move(80, 0)

        viewModel.sendMouseCommand(command, repeatable = true)

        assertEquals(listOf(command), connector.realtimeCommands)
        assertTrue(mouseRepeatManager.isRepeating())
        assertTrue(mouseRepeatManager.stopForSwitchPress())

        firstResult.complete(PcCommandResult.Ack)
        advanceUntilIdle()
        advanceTimeBy(250)
        runCurrent()

        assertEquals(listOf(command), connector.realtimeCommands)
        assertFalse(mouseRepeatManager.isRepeating())
    }

    @Test
    fun repeatableMouseCommandFailureDoesNotStartRepeat() = runTest(dispatcher) {
        val connector = FakeConnector(commandResult = PcCommandResult.Failed())
        val controller = connectedController(connector = connector)
        val viewModel = viewModel(controller)

        viewModel.sendMouseCommand(PcControlCommand.Move(80, 0), repeatable = true)

        assertEquals(listOf(PcControlCommand.Move(80, 0)), connector.realtimeCommands)
        assertFalse(mouseRepeatManager.isRepeating())
    }

    @Test
    fun repeatedMouseCommandFailurePausesForReconnect() = runTest(dispatcher) {
        val connector = FakeConnector(
            realtimeResults = mutableListOf(PcCommandResult.Ack, PcCommandResult.Failed())
        )
        val controller = connectedController(connector = connector)
        val viewModel = viewModel(controller)
        controller.onPcUiResumed()
        connector.liveResults += PcLiveControlResult.Failed("Disconnected.")

        viewModel.sendMouseCommand(PcControlCommand.Move(80, 0), repeatable = true)
        runCurrent()
        advanceTimeBy(mouseRepeatSettings.intervalMs)
        runCurrent()

        assertTrue(mouseRepeatManager.isRepeating())
        assertTrue(mouseRepeatManager.isPausedForReconnect())
        mouseRepeatManager.stop(showMessage = false)
        controller.disconnect()
    }

    @Test
    fun repeatableMouseCommandAuthFailureDoesNotStartRepeat() = runTest(dispatcher) {
        val connector = FakeConnector(commandResult = PcCommandResult.AuthFailed())
        val controller = connectedController(connector = connector)
        val viewModel = viewModel(controller)

        viewModel.sendMouseCommand(PcControlCommand.Move(80, 0), repeatable = true)

        assertTrue(connector.realtimeCommands.isNotEmpty())
        assertEquals(
            connector.realtimeCommands,
            List(connector.realtimeCommands.size) { PcControlCommand.Move(80, 0) }
        )
        assertFalse(mouseRepeatManager.isRepeating())
    }

    @Test
    fun reconnectingPausesMouseRepeat() = runTest(dispatcher) {
        val connector = FakeConnector()
        val controller = connectedController(connector = connector)
        val viewModel = viewModel(controller)
        val session = PcAuthenticatedSession("desktop-1", "device-1", "AA:BB:CC:DD:EE:FF")

        viewModel.sendMouseCommand(PcControlCommand.Move(80, 0), repeatable = true)
        runCurrent()
        assertTrue(mouseRepeatManager.isRepeating())

        PcConnectionStateHolder.setReconnecting(session, "Switchify PC")
        runCurrent()

        assertTrue(mouseRepeatManager.isRepeating())
        assertTrue(mouseRepeatManager.isPausedForReconnect())
        mouseRepeatManager.stop(showMessage = false)
    }

    @Test
    fun reconnectGraceExpiryStopsMouseRepeat() = runTest(dispatcher) {
        val connector = FakeConnector()
        val controller = connectedController(connector = connector)
        val viewModel = viewModel(controller)
        val session = PcAuthenticatedSession("desktop-1", "device-1", "AA:BB:CC:DD:EE:FF")

        viewModel.sendMouseCommand(PcControlCommand.Move(80, 0), repeatable = true)
        runCurrent()
        PcConnectionStateHolder.setReconnecting(session, "Switchify PC")
        runCurrent()

        advanceTimeBy(PcMouseRepeatManager.RECONNECT_GRACE_MS)
        runCurrent()

        assertFalse(mouseRepeatManager.isRepeating())
        assertFalse(mouseRepeatManager.isPausedForReconnect())
    }

    @Test
    fun connectedAfterReconnectResumesPausedMouseRepeat() = runTest(dispatcher) {
        val connector = FakeConnector()
        val controller = connectedController(connector = connector)
        val viewModel = viewModel(controller)
        controller.onPcUiResumed()
        connector.liveResults += PcLiveControlResult.Failed("Disconnected.")
        try {
            viewModel.sendMouseCommand(PcControlCommand.Move(80, 0), repeatable = true)
            runCurrent()
            connector.realtimeCommands.clear()

            connector.openedConnections.single().eventsFlow.tryEmit(PcControlConnectionEvent.Disconnected)
            runCurrent()

            assertTrue(mouseRepeatManager.isRepeating())
            assertTrue(mouseRepeatManager.isPausedForReconnect())
            advanceTimeBy(499)
            runCurrent()
            assertEquals(emptyList<PcControlCommand>(), connector.realtimeCommands)

            advanceTimeBy(1)
            runCurrent()
            advanceTimeBy(mouseRepeatSettings.intervalMs)
            runCurrent()

            assertFalse(mouseRepeatManager.isPausedForReconnect())
            assertEquals(listOf(PcControlCommand.Move(80, 0)), connector.realtimeCommands)
        } finally {
            mouseRepeatManager.stop(showMessage = false)
            controller.disconnect()
            runCurrent()
        }
    }

    @Test
    fun disconnectingStopsMouseRepeat() = runTest(dispatcher) {
        val connector = FakeConnector()
        val controller = connectedController(connector = connector)
        val viewModel = viewModel(controller)

        viewModel.sendMouseCommand(PcControlCommand.Move(80, 0), repeatable = true)
        runCurrent()
        assertTrue(mouseRepeatManager.isRepeating())

        controller.disconnect()
        advanceUntilIdle()

        assertFalse(mouseRepeatManager.isRepeating())
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
        assertFalse(viewModel.uiState.value.isBusy)
        assertNull(viewModel.uiState.value.busyCommand)
    }

    @Test
    fun successfulTypedTextSendClearsDraftStore() = runTest(dispatcher) {
        val draftStore = FakeTypingDraftStore()
        val controller = connectedController()
        val viewModel = viewModel(controller, typingDraftStore = draftStore)

        viewModel.updateTypingText("Hello")
        viewModel.sendTypedText()
        advanceUntilIdle()

        assertEquals("", viewModel.uiState.value.typingText)
        assertEquals("", draftStore.getDraft())
    }

    @Test
    fun typedTextSetsBusyWhileBulkSendIsInFlight() = runTest(dispatcher) {
        val pendingText = CompletableDeferred<PcCommandResult>()
        val connector = FakeConnector(realtimeResults = mutableListOf(pendingText))
        val controller = connectedController(connector = connector)
        val viewModel = viewModel(controller)

        viewModel.updateTypingText("Hello")
        viewModel.sendTypedText()
        runCurrent()

        assertTrue(viewModel.uiState.value.isBusy)
        assertEquals(PcControlCommand.TypeText("Hello"), viewModel.uiState.value.busyCommand)

        pendingText.complete(PcCommandResult.Ack)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isBusy)
        assertNull(viewModel.uiState.value.busyCommand)
        assertEquals("", viewModel.uiState.value.typingText)
    }

    @Test
    fun typedTextThenEnterKeepsBusyUntilEnterCompletes() = runTest(dispatcher) {
        val pendingEnter = CompletableDeferred<PcCommandResult>()
        val connector = FakeConnector(commandResults = mutableListOf(PcCommandResult.Ack, pendingEnter))
        val controller = connectedController(connector = connector)
        val viewModel = viewModel(controller)

        viewModel.updateTypingText("Hello")
        viewModel.sendTypedTextThenEnter()
        runCurrent()

        assertTrue(viewModel.uiState.value.isBusy)
        assertEquals("Hello", viewModel.uiState.value.typingText)
        assertEquals(PcControlCommand.TypeText("Hello"), viewModel.uiState.value.busyCommand)

        pendingEnter.complete(PcCommandResult.Ack)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isBusy)
        assertNull(viewModel.uiState.value.busyCommand)
    }

    @Test
    fun typingSendIgnoresSecondTapWhileBusy() = runTest(dispatcher) {
        val pendingText = CompletableDeferred<PcCommandResult>()
        val connector = FakeConnector(realtimeResults = mutableListOf(pendingText))
        val controller = connectedController(connector = connector)
        val viewModel = viewModel(controller)

        viewModel.updateTypingText("Hello")
        viewModel.sendTypedText()
        viewModel.sendTypedText()
        runCurrent()

        assertEquals(listOf(PcControlCommand.TypeText("Hello")), connector.realtimeCommands)

        pendingText.complete(PcCommandResult.Ack)
        advanceUntilIdle()
    }

    @Test
    fun connectedStateUsesBulkTypedTextWhenStreamsSupportedForPlainText() = runTest(dispatcher) {
        val connector = FakeConnector()
        val controller = connectedController(connector = connector, pointerProfile = textStreamPointerProfile())
        val viewModel = viewModel(controller)

        viewModel.showTypingSurface()
        viewModel.updateTypingText("Hi")
        viewModel.sendTypedText()
        advanceUntilIdle()

        assertEquals(listOf(PcControlCommand.TypeText("Hi")), connector.realtimeCommands)
        assertTrue(connector.commands.isEmpty())
        assertEquals("", viewModel.uiState.value.typingText)
        assertNull(viewModel.uiState.value.typingMessage)
        assertFalse(viewModel.uiState.value.isBusy)
        assertNull(viewModel.uiState.value.busyCommand)
        assertEquals("", viewModel.uiState.value.typingText)
    }

    @Test
    fun typedTextThenEnterUsesAckedCommandsToPreserveOrder() = runTest(dispatcher) {
        val pendingEnter = CompletableDeferred<PcCommandResult>()
        val connector = FakeConnector(commandResults = mutableListOf(PcCommandResult.Ack, pendingEnter))
        val controller = connectedController(connector = connector)
        val viewModel = viewModel(controller)

        viewModel.updateTypingText("First")
        viewModel.sendTypedTextThenEnter()
        runCurrent()
        viewModel.updateTypingText("Second")
        viewModel.sendTypedText()

        assertEquals(
            listOf(
                PcControlCommand.TypeText("First"),
                PcControlCommand.PressKey(PcKeyboardKey.Enter)
            ),
            connector.commands
        )
        assertTrue(connector.realtimeCommands.isEmpty())
        assertTrue(viewModel.uiState.value.isBusy)

        pendingEnter.complete(PcCommandResult.Ack)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isBusy)
        assertEquals("", viewModel.uiState.value.typingText)
    }

    @Test
    fun connectedStateStreamsTypedTextWithNewlineWhenSupported() = runTest(dispatcher) {
        val connector = FakeConnector()
        val controller = connectedController(connector = connector, pointerProfile = textStreamPointerProfile())
        val viewModel = viewModel(controller)

        viewModel.showTypingSurface()
        viewModel.updateTypingText("a\nb")
        viewModel.sendTypedText()
        advanceUntilIdle()

        assertEquals(3, connector.commands.size)
        val open = connector.commands[0] as PcControlCommand.TextStreamOpen
        assertEquals(
            listOf(
                PcControlCommand.TextStreamOpen(open.streamId),
                PcControlCommand.TextStreamKey(open.streamId, 1, PcKeyboardKey.Enter),
                PcControlCommand.TextStreamClose(open.streamId, expectedCount = 3)
            ),
            connector.commands
        )
        assertEquals(
            listOf(
                PcControlCommand.TextStreamChunk(open.streamId, 0, "a"),
                PcControlCommand.TextStreamChunk(open.streamId, 2, "b")
            ),
            connector.realtimeCommands
        )
        assertEquals("", viewModel.uiState.value.typingText)
        assertNull(viewModel.uiState.value.typingMessage)
        assertFalse(viewModel.uiState.value.isBusy)
        assertNull(viewModel.uiState.value.busyCommand)
    }

    @Test
    fun streamedTypedTextSetsBusyWhileInFlight() = runTest(dispatcher) {
        val pendingChunk = CompletableDeferred<PcCommandResult>()
        val connector = FakeConnector(commandResults = mutableListOf(PcCommandResult.Ack), realtimeResults = mutableListOf(pendingChunk))
        val controller = connectedController(connector = connector, pointerProfile = textStreamPointerProfile())
        val viewModel = viewModel(controller)

        viewModel.updateTypingText("a\nb")
        viewModel.sendTypedText()
        runCurrent()

        assertTrue(viewModel.uiState.value.isBusy)
        assertEquals(PcControlCommand.TypeText("a\nb"), viewModel.uiState.value.busyCommand)

        pendingChunk.complete(PcCommandResult.Ack)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isBusy)
        assertNull(viewModel.uiState.value.busyCommand)
        assertEquals("", viewModel.uiState.value.typingText)
    }

    @Test
    fun connectedStateFallsBackToBulkTypedTextWhenStreamsUnsupported() = runTest(dispatcher) {
        val connector = FakeConnector()
        val controller = connectedController(connector = connector, pointerProfile = pointerProfile())
        val viewModel = viewModel(controller)

        viewModel.updateTypingText("Hi")
        viewModel.sendTypedText()
        advanceUntilIdle()

        assertEquals(listOf(PcControlCommand.TypeText("Hi")), connector.realtimeCommands)
        assertTrue(connector.commands.isEmpty())
        assertEquals("", viewModel.uiState.value.typingText)
        assertFalse(viewModel.uiState.value.isBusy)
        assertNull(viewModel.uiState.value.busyCommand)
    }

    @Test
    fun connectedStateSendsTypedTextThenEnterCommands() = runTest(dispatcher) {
        val connector = FakeConnector()
        val controller = connectedController(connector = connector)
        val viewModel = viewModel(controller)

        viewModel.showTypingSurface()
        viewModel.updateTypingText("Hello")
        viewModel.sendTypedTextThenEnter()
        advanceUntilIdle()

        assertEquals(
            listOf(
                PcControlCommand.TypeText("Hello"),
                PcControlCommand.PressKey(PcKeyboardKey.Enter)
            ),
            connector.commands
        )
        assertTrue(connector.realtimeCommands.isEmpty())
        assertEquals("", viewModel.uiState.value.typingText)
        assertNull(viewModel.uiState.value.typingMessage)
        assertFalse(viewModel.uiState.value.isBusy)
        assertNull(viewModel.uiState.value.busyCommand)
    }

    @Test
    fun successfulTypedTextThenEnterClearsDraftStore() = runTest(dispatcher) {
        val draftStore = FakeTypingDraftStore()
        val controller = connectedController()
        val viewModel = viewModel(controller, typingDraftStore = draftStore)

        viewModel.updateTypingText("Hello")
        viewModel.sendTypedTextThenEnter()
        advanceUntilIdle()

        assertEquals("", viewModel.uiState.value.typingText)
        assertEquals("", draftStore.getDraft())
    }

    @Test
    fun connectedStateUsesBulkTypedTextThenEnterWhenStreamsSupportedForPlainText() = runTest(dispatcher) {
        val connector = FakeConnector()
        val controller = connectedController(connector = connector, pointerProfile = textStreamPointerProfile())
        val viewModel = viewModel(controller)

        viewModel.updateTypingText("Hi")
        viewModel.sendTypedTextThenEnter()
        advanceUntilIdle()

        assertEquals(
            listOf(
                PcControlCommand.TypeText("Hi"),
                PcControlCommand.PressKey(PcKeyboardKey.Enter)
            ),
            connector.commands
        )
        assertTrue(connector.realtimeCommands.isEmpty())
        assertEquals("", viewModel.uiState.value.typingText)
        assertNull(viewModel.uiState.value.typingMessage)
        assertFalse(viewModel.uiState.value.isBusy)
        assertNull(viewModel.uiState.value.busyCommand)
    }

    @Test
    fun connectedStateStreamsTypedTextThenEnterWithNewlineWhenSupported() = runTest(dispatcher) {
        val connector = FakeConnector()
        val controller = connectedController(connector = connector, pointerProfile = textStreamPointerProfile())
        val viewModel = viewModel(controller)

        viewModel.updateTypingText("a\nb")
        viewModel.sendTypedTextThenEnter()
        advanceUntilIdle()

        val streamId = (connector.commands[0] as PcControlCommand.TextStreamOpen).streamId
        assertEquals(
            listOf(
                PcControlCommand.TextStreamOpen(streamId),
                PcControlCommand.TextStreamKey(streamId, 1, PcKeyboardKey.Enter),
                PcControlCommand.TextStreamKey(streamId, 3, PcKeyboardKey.Enter),
                PcControlCommand.TextStreamClose(streamId, expectedCount = 4)
            ),
            connector.commands
        )
        assertEquals(
            listOf(
                PcControlCommand.TextStreamChunk(streamId, 0, "a"),
                PcControlCommand.TextStreamChunk(streamId, 2, "b")
            ),
            connector.realtimeCommands
        )
        assertEquals("", viewModel.uiState.value.typingText)
        assertNull(viewModel.uiState.value.typingMessage)
        assertFalse(viewModel.uiState.value.isBusy)
        assertNull(viewModel.uiState.value.busyCommand)
    }

    @Test
    fun connectedStateStreamsNewlineAsEnterKey() = runTest(dispatcher) {
        val connector = FakeConnector()
        val controller = connectedController(connector = connector, pointerProfile = textStreamPointerProfile())
        val viewModel = viewModel(controller)

        viewModel.updateTypingText("a\nb")
        viewModel.sendTypedText()
        advanceUntilIdle()

        val streamId = (connector.commands[0] as PcControlCommand.TextStreamOpen).streamId
        assertEquals(
            listOf(
                PcControlCommand.TextStreamOpen(streamId),
                PcControlCommand.TextStreamKey(streamId, 1, PcKeyboardKey.Enter),
                PcControlCommand.TextStreamClose(streamId, expectedCount = 3)
            ),
            connector.commands
        )
        assertEquals(
            listOf(
                PcControlCommand.TextStreamChunk(streamId, 0, "a"),
                PcControlCommand.TextStreamChunk(streamId, 2, "b")
            ),
            connector.realtimeCommands
        )
    }

    @Test
    fun textStreamSendsItemsWithoutFixedDelay() = runTest(dispatcher) {
        val connector = FakeConnector()
        val controller = connectedController(connector = connector, pointerProfile = textStreamPointerProfile())
        val viewModel = viewModel(controller)

        viewModel.updateTypingText("a\nb\nc")
        viewModel.sendTypedText()
        advanceUntilIdle()

        val streamId = (connector.commands[0] as PcControlCommand.TextStreamOpen).streamId
        assertEquals(
            listOf(
                PcControlCommand.TextStreamOpen(streamId),
                PcControlCommand.TextStreamKey(streamId, 1, PcKeyboardKey.Enter),
                PcControlCommand.TextStreamKey(streamId, 3, PcKeyboardKey.Enter),
                PcControlCommand.TextStreamClose(streamId, expectedCount = 5)
            ),
            connector.commands
        )
        assertEquals(
            listOf(
                PcControlCommand.TextStreamChunk(streamId, 0, "a"),
                PcControlCommand.TextStreamChunk(streamId, 2, "b"),
                PcControlCommand.TextStreamChunk(streamId, 4, "c")
            ),
            connector.realtimeCommands
        )
    }

    @Test
    fun streamedTypedTextFailureClearsBusyAndKeepsText() = runTest(dispatcher) {
        val connector = FakeConnector(commandResults = mutableListOf(PcCommandResult.Ack), realtimeResults = mutableListOf(PcCommandResult.Failed()))
        val controller = connectedController(connector = connector, pointerProfile = textStreamPointerProfile())
        val viewModel = viewModel(controller)

        viewModel.updateTypingText("a\nb")
        viewModel.sendTypedText()
        advanceUntilIdle()

        val streamId = (connector.commands[0] as PcControlCommand.TextStreamOpen).streamId
        assertEquals(
            listOf(
                PcControlCommand.TextStreamOpen(streamId)
            ),
            connector.commands
        )
        assertEquals(listOf(PcControlCommand.TextStreamChunk(streamId, 0, "a")), connector.realtimeCommands)
        assertEquals("a\nb", viewModel.uiState.value.typingText)
        assertEquals(PcMouseControlViewModel.TYPING_FAILED_MESSAGE, viewModel.uiState.value.typingMessage)
        assertFalse(viewModel.uiState.value.isBusy)
        assertNull(viewModel.uiState.value.busyCommand)
    }

    @Test
    fun streamedTypedTextCloseFailureClearsBusyAndKeepsText() = runTest(dispatcher) {
        val connector = FakeConnector(commandResults = mutableListOf(PcCommandResult.Ack, PcCommandResult.Ack, PcCommandResult.Failed()))
        val controller = connectedController(connector = connector, pointerProfile = textStreamPointerProfile())
        val viewModel = viewModel(controller)

        viewModel.updateTypingText("a\nb")
        viewModel.sendTypedText()
        advanceUntilIdle()

        val streamId = (connector.commands[0] as PcControlCommand.TextStreamOpen).streamId
        assertEquals(
            listOf(
                PcControlCommand.TextStreamOpen(streamId),
                PcControlCommand.TextStreamKey(streamId, 1, PcKeyboardKey.Enter),
                PcControlCommand.TextStreamClose(streamId, expectedCount = 3)
            ),
            connector.commands
        )
        assertEquals(
            listOf(
                PcControlCommand.TextStreamChunk(streamId, 0, "a"),
                PcControlCommand.TextStreamChunk(streamId, 2, "b")
            ),
            connector.realtimeCommands
        )
        assertEquals("a\nb", viewModel.uiState.value.typingText)
        assertEquals(PcMouseControlViewModel.TYPING_FAILED_MESSAGE, viewModel.uiState.value.typingMessage)
        assertFalse(viewModel.uiState.value.isBusy)
        assertNull(viewModel.uiState.value.busyCommand)
    }

    @Test
    fun sendTypedTextThenEnterDoesNotSendEnterWhenTextInvalid() = runTest(dispatcher) {
        val connector = FakeConnector()
        val controller = connectedController(connector = connector)
        val viewModel = viewModel(controller)

        viewModel.updateTypingText("hello\u001Bworld")
        viewModel.sendTypedTextThenEnter()
        advanceUntilIdle()

        assertTrue(connector.realtimeCommands.isEmpty())
        assertEquals("hello\u001Bworld", viewModel.uiState.value.typingText)
        assertEquals(PcMouseControlViewModel.TEXT_UNSUPPORTED_MESSAGE, viewModel.uiState.value.typingMessage)
        assertFalse(viewModel.uiState.value.isBusy)
        assertNull(viewModel.uiState.value.busyCommand)
    }

    @Test
    fun sendTypedTextThenEnterDoesNotSendEnterWhenTextSendFails() = runTest(dispatcher) {
        val connector = FakeConnector(commandResult = PcCommandResult.Failed())
        val controller = connectedController(connector = connector)
        val viewModel = viewModel(controller)

        viewModel.updateTypingText("Hello")
        viewModel.sendTypedTextThenEnter()
        advanceUntilIdle()

        assertEquals(listOf(PcControlCommand.TypeText("Hello")), connector.commands)
        assertTrue(connector.realtimeCommands.isEmpty())
        assertEquals("Hello", viewModel.uiState.value.typingText)
        assertEquals(PcMouseControlViewModel.TYPING_FAILED_MESSAGE, viewModel.uiState.value.typingMessage)
        assertFalse(viewModel.uiState.value.isBusy)
        assertNull(viewModel.uiState.value.busyCommand)
    }

    @Test
    fun failedTypedTextSendKeepsDraftStore() = runTest(dispatcher) {
        val draftStore = FakeTypingDraftStore()
        val connector = FakeConnector(commandResult = PcCommandResult.Failed())
        val controller = connectedController(connector = connector)
        val viewModel = viewModel(controller, typingDraftStore = draftStore)

        viewModel.updateTypingText("Hello")
        viewModel.sendTypedText()
        advanceUntilIdle()

        assertEquals("Hello", viewModel.uiState.value.typingText)
        assertEquals("Hello", draftStore.getDraft())
    }

    @Test
    fun sendTypedTextThenEnterClearsTextWhenEnterFailsAfterTextSends() = runTest(dispatcher) {
        val connector = FakeConnector(
            commandResults = mutableListOf(
                PcCommandResult.Ack,
                PcCommandResult.Failed()
            )
        )
        val controller = connectedController(connector = connector)
        val viewModel = viewModel(controller)

        viewModel.updateTypingText("Hello")
        viewModel.sendTypedTextThenEnter()
        advanceUntilIdle()

        assertEquals(
            listOf(
                PcControlCommand.TypeText("Hello"),
                PcControlCommand.PressKey(PcKeyboardKey.Enter)
            ),
            connector.commands
        )
        assertTrue(connector.realtimeCommands.isEmpty())
        assertEquals("", viewModel.uiState.value.typingText)
        assertEquals(PcMouseControlViewModel.KEY_FAILED_MESSAGE, viewModel.uiState.value.typingMessage)
        assertFalse(viewModel.uiState.value.isBusy)
        assertNull(viewModel.uiState.value.busyCommand)
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
    fun windowSurfaceCanSendNavigationKeyCommand() = runTest(dispatcher) {
        val connector = FakeConnector()
        val controller = connectedController(connector = connector)
        val viewModel = viewModel(controller)

        viewModel.selectControlSurface(PcControlSurface.Window)
        viewModel.send(PcControlCommand.PressKey(PcKeyboardKey.Escape))
        advanceUntilIdle()

        assertEquals(listOf(PcControlCommand.PressKey(PcKeyboardKey.Escape)), connector.realtimeCommands)
        assertTrue(connector.commands.isEmpty())
        assertEquals(PcControlSurface.Window, viewModel.uiState.value.activeSurface)
    }

    @Test
    fun windowSurfaceCanSendStartShortcutCommand() = runTest(dispatcher) {
        val connector = FakeConnector()
        val controller = connectedController(connector = connector)
        val viewModel = viewModel(controller)
        val command = PcControlCommand.KeyboardShortcut(listOf(PcKeyboardShortcutKey.Meta))

        viewModel.selectControlSurface(PcControlSurface.Window)
        viewModel.send(command)
        advanceUntilIdle()

        assertEquals(listOf(command), connector.realtimeCommands)
        assertTrue(connector.commands.isEmpty())
        assertEquals(PcControlSurface.Window, viewModel.uiState.value.activeSurface)
        assertFalse(viewModel.uiState.value.isBusy)
        assertNull(viewModel.uiState.value.message)
    }

    @Test
    fun windowSurfaceCanSendEditingShortcutCommand() = runTest(dispatcher) {
        val connector = FakeConnector()
        val controller = connectedController(connector = connector)
        val viewModel = viewModel(controller)
        val command = PcControlCommand.KeyboardShortcut(listOf(PcKeyboardShortcutKey.Ctrl, PcKeyboardShortcutKey.C))

        viewModel.selectControlSurface(PcControlSurface.Window)
        viewModel.send(command)
        advanceUntilIdle()

        assertEquals(listOf(command), connector.realtimeCommands)
        assertTrue(connector.commands.isEmpty())
        assertEquals(PcControlSurface.Window, viewModel.uiState.value.activeSurface)
        assertFalse(viewModel.uiState.value.isBusy)
        assertNull(viewModel.uiState.value.message)
    }

    @Test
    fun shortcutLetterWithoutActiveModifiersDoesNotSendCommand() = runTest(dispatcher) {
        val connector = FakeConnector()
        val controller = connectedController(connector = connector)
        val viewModel = viewModel(controller)

        viewModel.sendShortcutLetter(PcKeyboardShortcutKey.C)
        advanceUntilIdle()

        assertTrue(connector.realtimeCommands.isEmpty())
        assertEquals(PcMouseControlViewModel.SELECT_SHORTCUT_MODIFIER_MESSAGE, viewModel.uiState.value.message)
    }

    @Test
    fun shortcutLetterSendsWithActiveModifiersInStableOrder() = runTest(dispatcher) {
        val connector = FakeConnector()
        val controller = connectedController(connector = connector)
        val viewModel = viewModel(controller)
        advanceUntilIdle()

        viewModel.toggleModifier(PcKeyboardModifierKey.Shift)
        advanceUntilIdle()
        viewModel.toggleModifier(PcKeyboardModifierKey.Ctrl)
        advanceUntilIdle()
        viewModel.sendShortcutLetter(PcKeyboardShortcutKey.Z)
        advanceUntilIdle()

        assertEquals(
            listOf(
                PcControlCommand.ModifierDown(PcKeyboardModifierKey.Shift),
                PcControlCommand.ModifierDown(PcKeyboardModifierKey.Ctrl),
                PcControlCommand.KeyboardShortcut(
                    listOf(
                        PcKeyboardShortcutKey.Ctrl,
                        PcKeyboardShortcutKey.Shift,
                        PcKeyboardShortcutKey.Z
                    )
                ),
                PcControlCommand.ModifierUp(PcKeyboardModifierKey.Shift),
                PcControlCommand.ModifierUp(PcKeyboardModifierKey.Ctrl)
            ),
            connector.realtimeCommands
        )
        assertEquals(emptySet<PcKeyboardModifierKey>(), viewModel.uiState.value.activeModifiers)
        assertNull(viewModel.uiState.value.message)
    }

    @Test
    fun failedShortcutLetterDoesNotClearActiveModifiers() = runTest(dispatcher) {
        val connector = FakeConnector(
            realtimeResults = mutableListOf(PcCommandResult.Ack, PcCommandResult.Failed())
        )
        val controller = connectedController(connector = connector)
        val viewModel = viewModel(controller)
        advanceUntilIdle()

        viewModel.toggleModifier(PcKeyboardModifierKey.Ctrl)
        advanceUntilIdle()
        viewModel.sendShortcutLetter(PcKeyboardShortcutKey.C)
        advanceUntilIdle()

        assertEquals(setOf(PcKeyboardModifierKey.Ctrl), viewModel.uiState.value.activeModifiers)
        assertEquals(PcMouseControlViewModel.COMMAND_FAILED_MESSAGE, viewModel.uiState.value.message)
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
    fun typedTextIsBlockedByBusyState() = runTest(dispatcher) {
        val pendingText = CompletableDeferred<PcCommandResult>()
        val connector = FakeConnector(realtimeResults = mutableListOf(pendingText))
        val controller = connectedController(connector = connector)
        val viewModel = viewModel(controller)

        viewModel.updateTypingText("First")
        viewModel.sendTypedText()
        runCurrent()
        viewModel.updateTypingText("Second")
        viewModel.sendTypedText()

        assertEquals(listOf(PcControlCommand.TypeText("First")), connector.realtimeCommands)

        pendingText.complete(PcCommandResult.Ack)
        advanceUntilIdle()
    }

    @Test
    fun sharedReconnectStateKeepsTypedTextSendBusy() = runTest(dispatcher) {
        val pendingText = CompletableDeferred<PcCommandResult>()
        val connector = FakeConnector(realtimeResults = mutableListOf(pendingText))
        val controller = connectedController(connector = connector)
        val viewModel = viewModel(controller)
        val session = PcAuthenticatedSession("desktop-1", "device-1", "AA:BB:CC:DD:EE:FF")

        viewModel.updateTypingText("Hello")
        viewModel.sendTypedText()
        runCurrent()
        PcConnectionStateHolder.setReconnecting(session, "Switchify PC")
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isBusy)
        assertEquals(PcControlCommand.TypeText("Hello"), viewModel.uiState.value.busyCommand)

        pendingText.complete(PcCommandResult.Ack)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isBusy)
        assertNull(viewModel.uiState.value.busyCommand)
    }

    @Test
    fun serviceReconnectStateKeepsTypedTextSendBusy() = runTest(dispatcher) {
        val pendingText = CompletableDeferred<PcCommandResult>()
        val connector = FakeConnector(realtimeResults = mutableListOf(pendingText))
        val controller = connectedController(connector = connector)
        val viewModel = viewModel(controller)

        viewModel.updateTypingText("Hello")
        viewModel.sendTypedText()
        runCurrent()
        connector.openedConnections.single().eventsFlow.emit(PcControlConnectionEvent.Disconnected)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isBusy)
        assertEquals(PcControlCommand.TypeText("Hello"), viewModel.uiState.value.busyCommand)

        pendingText.complete(PcCommandResult.Ack)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isBusy)
        assertNull(viewModel.uiState.value.busyCommand)
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
    fun leftClickClearsDraggingAfterSuccessfulSend() = runTest(dispatcher) {
        val connector = FakeConnector()
        val controller = connectedController(connector = connector)
        val viewModel = viewModel(controller)

        viewModel.send(PcControlCommand.DragStart())
        advanceUntilIdle()
        viewModel.send(PcControlCommand.LeftClick)
        advanceUntilIdle()

        assertEquals(
            listOf(PcControlCommand.DragStart(), PcControlCommand.LeftClick),
            connector.realtimeCommands
        )
        assertFalse(viewModel.uiState.value.isDragging)
    }

    @Test
    fun rightClickClearsDraggingAfterSuccessfulSend() = runTest(dispatcher) {
        val controller = connectedController()
        val viewModel = viewModel(controller)

        viewModel.send(PcControlCommand.DragStart())
        advanceUntilIdle()
        viewModel.send(PcControlCommand.RightClick)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isDragging)
    }

    @Test
    fun doubleClickClearsDraggingAfterSuccessfulSend() = runTest(dispatcher) {
        val controller = connectedController()
        val viewModel = viewModel(controller)

        viewModel.send(PcControlCommand.DragStart())
        advanceUntilIdle()
        viewModel.send(PcControlCommand.DoubleClick)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isDragging)
    }

    @Test
    fun failedClickDoesNotClearDraggingMirror() = runTest(dispatcher) {
        val connector = FakeConnector(
            realtimeResults = mutableListOf(PcCommandResult.Ack, PcCommandResult.Failed())
        )
        val controller = connectedController(connector = connector)
        val viewModel = viewModel(controller)

        viewModel.send(PcControlCommand.DragStart())
        advanceUntilIdle()
        viewModel.send(PcControlCommand.LeftClick)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isDragging)
        assertEquals(PcMouseControlViewModel.COMMAND_FAILED_MESSAGE, viewModel.uiState.value.message)
    }

    @Test
    fun moveDoesNotClearDragging() = runTest(dispatcher) {
        val controller = connectedController()
        val viewModel = viewModel(controller)

        viewModel.send(PcControlCommand.DragStart())
        advanceUntilIdle()
        viewModel.send(PcControlCommand.Move(dx = 10, dy = 0))
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isDragging)
    }

    @Test
    fun scrollDoesNotClearDragging() = runTest(dispatcher) {
        val controller = connectedController()
        val viewModel = viewModel(controller)

        viewModel.send(PcControlCommand.DragStart())
        advanceUntilIdle()
        viewModel.send(PcControlCommand.Scroll(dx = 0, dy = -1))
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isDragging)
    }

    @Test
    fun modifierToggleDoesNotClearDragging() = runTest(dispatcher) {
        val controller = connectedController(pointerProfile = modifierPointerProfile())
        val viewModel = viewModel(controller)
        advanceUntilIdle()

        viewModel.send(PcControlCommand.DragStart())
        advanceUntilIdle()
        viewModel.toggleModifier(PcKeyboardModifierKey.Ctrl)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isDragging)
    }

    @Test
    fun supportedProfileEnablesModifierToggles() = runTest(dispatcher) {
        val controller = connectedController(pointerProfile = modifierPointerProfile())
        val viewModel = viewModel(controller)

        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.supportsModifierToggles)
    }

    @Test
    fun unsupportedProfileDisablesModifierToggles() = runTest(dispatcher) {
        val controller = connectedController(pointerProfile = pointerProfile())
        val viewModel = viewModel(controller)

        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.supportsModifierToggles)
    }

    @Test
    fun modifierToggleSendsWithoutAdvertisedSupport() = runTest(dispatcher) {
        val connector = FakeConnector()
        val controller = connectedController(connector = connector, pointerProfile = pointerProfile())
        val viewModel = viewModel(controller)
        advanceUntilIdle()

        viewModel.toggleModifier(PcKeyboardModifierKey.Ctrl)
        advanceUntilIdle()

        assertEquals(listOf(PcControlCommand.ModifierDown(PcKeyboardModifierKey.Ctrl)), connector.realtimeCommands)
        assertEquals(setOf(PcKeyboardModifierKey.Ctrl), viewModel.uiState.value.activeModifiers)
    }

    @Test
    fun modifierToggleDownMarksActiveAfterAck() = runTest(dispatcher) {
        val connector = FakeConnector()
        val controller = connectedController(connector = connector, pointerProfile = modifierPointerProfile())
        val viewModel = viewModel(controller)
        advanceUntilIdle()

        viewModel.toggleModifier(PcKeyboardModifierKey.Ctrl)
        advanceUntilIdle()

        assertEquals(listOf(PcControlCommand.ModifierDown(PcKeyboardModifierKey.Ctrl)), connector.realtimeCommands)
        assertEquals(setOf(PcKeyboardModifierKey.Ctrl), viewModel.uiState.value.activeModifiers)
    }

    @Test
    fun modifierToggleUpClearsActiveAfterAck() = runTest(dispatcher) {
        val connector = FakeConnector()
        val controller = connectedController(connector = connector, pointerProfile = modifierPointerProfile())
        val viewModel = viewModel(controller)
        advanceUntilIdle()

        viewModel.toggleModifier(PcKeyboardModifierKey.Ctrl)
        advanceUntilIdle()
        viewModel.toggleModifier(PcKeyboardModifierKey.Ctrl)
        advanceUntilIdle()

        assertEquals(
            listOf(
                PcControlCommand.ModifierDown(PcKeyboardModifierKey.Ctrl),
                PcControlCommand.ModifierUp(PcKeyboardModifierKey.Ctrl)
            ),
            connector.realtimeCommands
        )
        assertEquals(emptySet<PcKeyboardModifierKey>(), viewModel.uiState.value.activeModifiers)
    }

    @Test
    fun failedModifierDownDoesNotMarkActive() = runTest(dispatcher) {
        val connector = FakeConnector(commandResult = PcCommandResult.Failed())
        val controller = connectedController(connector = connector, pointerProfile = modifierPointerProfile())
        val viewModel = viewModel(controller)
        advanceUntilIdle()

        viewModel.toggleModifier(PcKeyboardModifierKey.Shift)
        advanceUntilIdle()

        assertEquals(emptySet<PcKeyboardModifierKey>(), viewModel.uiState.value.activeModifiers)
    }

    @Test
    fun modifiersCoexistWithDrag() = runTest(dispatcher) {
        val connector = FakeConnector()
        val controller = connectedController(connector = connector, pointerProfile = modifierPointerProfile())
        val viewModel = viewModel(controller)
        advanceUntilIdle()

        viewModel.toggleModifier(PcKeyboardModifierKey.Ctrl)
        advanceUntilIdle()
        viewModel.send(PcControlCommand.DragStart())
        advanceUntilIdle()

        assertEquals(setOf(PcKeyboardModifierKey.Ctrl), viewModel.uiState.value.activeModifiers)
        assertTrue(viewModel.uiState.value.isDragging)
    }

    @Test
    fun reconnectClearsActiveModifiers() = runTest(dispatcher) {
        val controller = connectedController(pointerProfile = modifierPointerProfile())
        val viewModel = viewModel(controller)
        val session = PcAuthenticatedSession("desktop-1", "device-1", "AA:BB:CC:DD:EE:FF")
        advanceUntilIdle()

        viewModel.toggleModifier(PcKeyboardModifierKey.Alt)
        advanceUntilIdle()
        PcConnectionStateHolder.setReconnecting(session, "Switchify PC")
        advanceUntilIdle()

        assertEquals(emptySet<PcKeyboardModifierKey>(), viewModel.uiState.value.activeModifiers)
    }

    @Test
    fun uiPauseReleasesAndClearsActiveModifiers() = runTest(dispatcher) {
        val connector = FakeConnector()
        val controller = connectedController(connector = connector, pointerProfile = modifierPointerProfile())
        val viewModel = viewModel(controller)
        advanceUntilIdle()

        viewModel.toggleModifier(PcKeyboardModifierKey.Meta)
        advanceUntilIdle()
        viewModel.onPcUiPaused()
        advanceUntilIdle()

        assertEquals(emptySet<PcKeyboardModifierKey>(), viewModel.uiState.value.activeModifiers)
        assertEquals(
            listOf(
                PcControlCommand.ModifierDown(PcKeyboardModifierKey.Meta),
                PcControlCommand.ModifierUp(PcKeyboardModifierKey.Meta)
            ),
            connector.realtimeCommands
        )
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

    private suspend fun pairedController(
        connector: FakeConnector = FakeConnector()
    ): PcServiceConnectionController {
        val tokens = FakeTokenStore(
            mutableMapOf(
                "desktop-1" to "token-1",
                "desktop-2" to "token-2"
            )
        )
        val controller = controller(tokens, connector, FakeDiscovery(listOf(pc, officePc)))
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
        controlSurfaceStore: FakeControlSurfaceStore = FakeControlSurfaceStore(),
        typingDraftStore: FakeTypingDraftStore = FakeTypingDraftStore()
    ): PcMouseControlViewModel {
        return PcMouseControlViewModel(
            serviceControllerProvider = { controller },
            controlSurfaceStore = controlSurfaceStore,
            typingDraftStore = typingDraftStore
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
        private var defaultDesktopId: String? = null

        override fun getToken(desktopId: String): String? = tokens[desktopId]

        override fun saveToken(desktopId: String, token: String, lastEndpointId: String, serviceName: String?) {
            tokens[desktopId] = token
            lastEndpointIds[desktopId] = lastEndpointId
        }

        override fun clearToken(desktopId: String) {
            tokens.remove(desktopId)
            lastEndpointIds.remove(desktopId)
            if (defaultDesktopId == desktopId) defaultDesktopId = null
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
        override fun getDefaultDesktopId(): String? {
            val desktopId = defaultDesktopId ?: return null
            if (tokens.containsKey(desktopId)) return desktopId
            defaultDesktopId = null
            return null
        }

        override fun setDefaultDesktopId(desktopId: String) {
            if (tokens.containsKey(desktopId)) defaultDesktopId = desktopId
        }

        override fun clearDefaultDesktopId() {
            defaultDesktopId = null
        }
    }

    private object FakeIdentity : PcDeviceIdentity {
        override fun getDeviceId(): String = "device-1"
        override fun getDeviceName(): String = "Android phone"
    }

    private class FakeConnector(
        private val pingResult: PcPingResult = PcPingResult.Connected("AA:BB:CC:DD:EE:FF"),
        private val pairingResult: Any = PcPairingResult.Failed(PcErrorReason.Failed, "unused"),
        private val pingResultsByDesktop: Map<String, PcPingResult> = emptyMap(),
        private val pairingResultsByDesktop: Map<String, Any> = emptyMap(),
        private val commandResult: Any = PcCommandResult.Ack,
        private val commandResults: MutableList<Any> = mutableListOf(),
        private val realtimeResults: MutableList<Any> = mutableListOf(),
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
            return resolvePairingResult(pairingResultsByDesktop[pc.desktopId] ?: pairingResult)
        }

        override suspend fun authenticatedPing(pc: DiscoveredPc, token: String): PcPingResult {
            pingCalls++
            return pingResultsByDesktop[pc.desktopId] ?: pingResult
        }

        override suspend fun openControlSession(session: PcAuthenticatedSession): PcLiveControlResult {
            openControlSessionCalls++
            if (liveResults.isNotEmpty()) return liveResults.removeAt(0)
            val connection = FakeLiveConnection(
                pointerProfile = pointerProfile,
                onCommand = { command ->
                    commands.add(command)
                    when {
                        commandResults.isNotEmpty() -> resolveCommandResult(commandResults.removeAt(0))
                        else -> resolveCommandResult(commandResult)
                    }
                },
                onRealtimeCommand = { command ->
                    realtimeCommands.add(command)
                    when {
                        realtimeResults.isNotEmpty() -> resolveCommandResult(realtimeResults.removeAt(0))
                        else -> resolveCommandResult(commandResult)
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

        private suspend fun resolveCommandResult(result: Any): PcCommandResult {
            return when (result) {
                is CompletableDeferred<*> -> result.await() as PcCommandResult
                is PcCommandResult -> result
                else -> PcCommandResult.Ack
            }
        }

        private suspend fun resolvePairingResult(result: Any): PcPairingResult {
            return when (result) {
                is CompletableDeferred<*> -> result.await() as PcPairingResult
                is PcPairingResult -> result
                else -> PcPairingResult.Failed(PcErrorReason.Failed, "unused")
            }
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

    private fun pointerProfile(
        small: Int = 50,
        medium: Int = 130,
        large: Int = 252,
        maxDelta: Int = 500,
        capabilities: PcPointerCapabilities = PcPointerCapabilities()
    ): PcPointerMovementProfile {
        return PcPointerMovementProfile(
            displayId = "0:0:1280:720:1.5",
            scaleFactor = 1.5,
            bounds = PcPointerBounds(0, 0, 1280, 720),
            maxDelta = maxDelta,
            recommendedDeltas = PcPointerDeltas(small = small, medium = medium, large = large),
            capabilities = capabilities
        )
    }

    private fun textStreamPointerProfile(): PcPointerMovementProfile {
        return pointerProfile(
            capabilities = PcPointerCapabilities(
                noAckCommands = setOf(
                    "keyboard.textStream.char",
                    "keyboard.textStream.chunk"
                ),
                supportedCommands = setOf(
                    "keyboard.textStream.open",
                    "keyboard.textStream.chunk",
                    "keyboard.textStream.key",
                    "keyboard.textStream.close"
                )
            )
        )
    }

    private fun modifierPointerProfile(): PcPointerMovementProfile {
        return pointerProfile(
            capabilities = PcPointerCapabilities(
                noAckCommands = setOf(
                    "keyboard.modifierDown",
                    "keyboard.modifierUp"
                ),
                supportedCommands = setOf(
                    "keyboard.modifierDown",
                    "keyboard.modifierUp"
                )
            )
        )
    }

    private class FakeControlSurfaceStore(
        private var selectedSurface: PcControlSurface = PcControlSurface.Mouse
    ) : PcControlSurfaceStore {
        override fun getSelectedSurface(): PcControlSurface = selectedSurface

        override fun setSelectedSurface(surface: PcControlSurface) {
            selectedSurface = surface
        }
    }

    private class FakeTypingDraftStore(
        private var draft: String = ""
    ) : PcTypingDraftStore {
        override fun getDraft(): String = draft

        override fun setDraft(text: String) {
            draft = text
        }

        override fun clearDraft() {
            draft = ""
        }
    }

    private class FakeMouseRepeatSettings(
        var enabled: Boolean = true,
        var intervalMs: Long = 250L
    ) : PcMouseRepeatSettings {
        override fun isEnabled(): Boolean = enabled
        override fun intervalMs(): Long = intervalMs
    }
}
