package com.enaboapps.switchify.screens.pcswitchforwarding

import com.enaboapps.switchify.R
import com.enaboapps.switchify.pc.DiscoveredPc
import com.enaboapps.switchify.pc.PcApprovalCodeState
import com.enaboapps.switchify.pc.PcAuthenticatedSession
import com.enaboapps.switchify.pc.PcBluetoothEndpoint
import com.enaboapps.switchify.pc.PcErrorReason
import com.enaboapps.switchify.pc.PcServiceConnectResult
import com.enaboapps.switchify.pc.PcServiceConnectionState
import com.enaboapps.switchify.pc.PcSwitchProfileCatalog
import com.enaboapps.switchify.pc.PcSwitchProfileSummary
import com.enaboapps.switchify.pc.PcSwitcherConnectionHost
import com.enaboapps.switchify.service.pcswitchforwarding.PcSwitchCatalogResult
import com.enaboapps.switchify.service.pcswitchforwarding.PcSwitchForwardingChooserHost
import com.enaboapps.switchify.service.pcswitchforwarding.PcSwitchForwardingStartResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PcSwitchForwardingChooserViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun catalogLoadsOnceAndSelectionRemainsInViewModelState() = runTest(dispatcher) {
        val host = FakeChooserHost()
        val viewModel = viewModel(host)
        runCurrent()

        val ready = viewModel.state.value as PcSwitchChooserState.Ready
        viewModel.select(host.profiles.last())

        assertEquals(1, host.loadCount)
        assertEquals(host.profiles.last(), (viewModel.state.value as PcSwitchChooserState.Ready).selected)
        assertEquals(ready.catalog, (viewModel.state.value as PcSwitchChooserState.Ready).catalog)
    }

    @Test
    fun startContinuesInViewModelScopeAndRemembersSuccessfulProfile() = runTest(dispatcher) {
        val allowStart = CompletableDeferred<Unit>()
        val host = FakeChooserHost(allowStart = allowStart)
        val remembered = mutableListOf<Pair<String, String>>()
        val viewModel = viewModel(host, rememberProfile = { pcId, profileId ->
            remembered += pcId to profileId
        })
        runCurrent()

        viewModel.start()
        runCurrent()
        assertTrue(viewModel.state.value is PcSwitchChooserState.Starting)

        allowStart.complete(Unit)
        runCurrent()

        assertTrue(viewModel.state.value is PcSwitchChooserState.Ready)
        assertEquals(listOf("pc" to "builtin.keyboard"), remembered)
    }

    @Test
    fun noExternalSwitchRaceShowsExplicitWarning() = runTest(dispatcher) {
        val host = FakeChooserHost(
            startResult = PcSwitchForwardingStartResult.NoExternalSwitches
        )
        val viewModel = viewModel(host)
        runCurrent()

        viewModel.start()
        runCurrent()

        val notice = (viewModel.state.value as PcSwitchChooserState.Ready).notice
        assertEquals(PcSwitchNoticeSeverity.Warning, notice?.severity)
        assertEquals(
            "message-${R.string.pc_switch_forwarding_no_external_switches}",
            notice?.text
        )
    }

    @Test
    fun retryReplacesErrorWithFreshCatalog() = runTest(dispatcher) {
        val host = FakeChooserHost(
            catalogResult = PcSwitchCatalogResult.Failed("Unavailable")
        )
        val viewModel = viewModel(host)
        runCurrent()
        assertTrue(viewModel.state.value is PcSwitchChooserState.Error)

        host.catalogResult = PcSwitchCatalogResult.Loaded(host.catalog)
        viewModel.retry()
        runCurrent()

        assertTrue(viewModel.state.value is PcSwitchChooserState.Ready)
        assertEquals(2, host.loadCount)
    }

    @Test
    fun openingPcSwitcherCancelsStartAndPreparesController() = runTest(dispatcher) {
        val allowStart = CompletableDeferred<Unit>()
        val host = FakeChooserHost(allowStart = allowStart)
        val viewModel = viewModel(host)
        runCurrent()
        viewModel.start()
        runCurrent()
        assertTrue(viewModel.state.value is PcSwitchChooserState.Starting)

        viewModel.openPcSwitcher()
        runCurrent()

        assertEquals(1, host.prepareCount)
        assertTrue(viewModel.state.value is PcSwitchChooserState.Ready)
        assertTrue(viewModel.pcSwitcherState.value.visible)
    }

    @Test
    fun successfulPcSwitchReloadsProfileCatalog() = runTest(dispatcher) {
        val host = FakeChooserHost()
        val switcherHost = FakeSwitcherHost()
        val viewModel = viewModel(host, switcherHost = switcherHost)
        runCurrent()
        assertEquals(1, host.loadCount)

        viewModel.openPcSwitcher()
        runCurrent()
        viewModel.switchToPc("second")
        runCurrent()

        assertEquals(2, host.loadCount)
        assertEquals("Second PC", viewModel.pcSwitcherState.value.connectedDisplayName)
        assertTrue(viewModel.state.value is PcSwitchChooserState.Ready)
    }

    private fun viewModel(
        host: FakeChooserHost,
        rememberProfile: (String, String) -> Unit = { _, _ -> },
        switcherHost: PcSwitcherConnectionHost = EmptySwitcherHost
    ) = PcSwitchForwardingChooserViewModel(
        host = host,
        rememberedProfileId = { null },
        rememberProfile = rememberProfile,
        message = { resourceId, _ -> "message-$resourceId" },
        switcherConnectionHost = switcherHost
    )

    private object EmptySwitcherHost : PcSwitcherConnectionHost {
        override val connectionState = null
        override fun currentDesktopId(): String? = null
        override fun currentDisplayName(): String? = null
        override suspend fun discoverPairedPcs(): List<DiscoveredPc> = emptyList()
        override suspend fun connectTo(
            pc: DiscoveredPc,
            onWaitingForApproval: (PcApprovalCodeState) -> Unit
        ) = PcServiceConnectResult.Failed(PcErrorReason.Failed, "Unavailable")
        override fun cancelConnectionAttempt() = Unit
    }

    private class FakeSwitcherHost : PcSwitcherConnectionHost {
        private val first = pc("first", "First PC")
        private val second = pc("second", "Second PC")
        private var connected = first
        override val connectionState =
            MutableStateFlow<PcServiceConnectionState>(
                PcServiceConnectionState.Connected(
                    PcAuthenticatedSession("first", "android", "first"),
                    "First PC",
                    pointerProfile = null
                )
            )

        override fun currentDesktopId(): String = connected.desktopId

        override fun currentDisplayName(): String = connected.controlDeviceName

        override suspend fun discoverPairedPcs(): List<DiscoveredPc> =
            listOf(first, second)

        override suspend fun connectTo(
            pc: DiscoveredPc,
            onWaitingForApproval: (PcApprovalCodeState) -> Unit
        ): PcServiceConnectResult {
            connected = pc
            val session = PcAuthenticatedSession(pc.desktopId, "android", pc.desktopId)
            connectionState.value = PcServiceConnectionState.Connected(
                session,
                pc.controlDeviceName,
                pointerProfile = null
            )
            return PcServiceConnectResult.Connected(session, pc.controlDeviceName)
        }

        override fun cancelConnectionAttempt() = Unit

        companion object {
            private fun pc(desktopId: String, name: String) = DiscoveredPc(
                serviceName = name,
                desktopId = desktopId,
                bluetoothEndpoint = PcBluetoothEndpoint(
                    deviceAddress = desktopId,
                    deviceName = name,
                    desktopId = desktopId,
                    displayName = name
                )
            )
        }
    }

    private class FakeChooserHost(
        var catalogResult: PcSwitchCatalogResult? = null,
        private val startResult: PcSwitchForwardingStartResult =
            PcSwitchForwardingStartResult.Started,
        private val allowStart: CompletableDeferred<Unit>? = null
    ) : PcSwitchForwardingChooserHost {
        val profiles = listOf(
            PcSwitchProfileSummary(
                id = "builtin.keyboard",
                version = 1,
                name = "Generic keyboard",
                kind = "mapped",
                bindings = emptyList()
            ),
            PcSwitchProfileSummary(
                id = "custom",
                version = 1,
                name = "Custom",
                kind = "mapped",
                bindings = emptyList()
            )
        )
        val catalog = PcSwitchProfileCatalog(
            catalogRevision = 1,
            profiles = profiles
        )
        var loadCount = 0
        var prepareCount = 0

        override suspend fun loadProfileCatalog(): PcSwitchCatalogResult {
            loadCount++
            return catalogResult ?: PcSwitchCatalogResult.Loaded(catalog)
        }

        override fun currentPcId(): String = "pc"

        override fun configuredExternalSwitchCount(): Int = 1

        override suspend fun prepareForPcSwitcher() {
            prepareCount++
        }

        override suspend fun start(
            profile: PcSwitchProfileSummary,
            usesLegacyGridProtocol: Boolean
        ): PcSwitchForwardingStartResult {
            allowStart?.await()
            return startResult
        }
    }
}
