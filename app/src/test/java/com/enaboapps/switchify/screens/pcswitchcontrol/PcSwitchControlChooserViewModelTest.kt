package com.enaboapps.switchify.screens.pcswitchcontrol

import com.enaboapps.switchify.R
import com.enaboapps.switchify.pc.PcSwitchProfileCatalog
import com.enaboapps.switchify.pc.PcSwitchProfileSummary
import com.enaboapps.switchify.service.pcswitchcontrol.PcSwitchCatalogResult
import com.enaboapps.switchify.service.pcswitchcontrol.PcSwitchControlChooserHost
import com.enaboapps.switchify.service.pcswitchcontrol.PcSwitchControlStartResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
class PcSwitchControlChooserViewModelTest {
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
            startResult = PcSwitchControlStartResult.NoExternalSwitches
        )
        val viewModel = viewModel(host)
        runCurrent()

        viewModel.start()
        runCurrent()

        val notice = (viewModel.state.value as PcSwitchChooserState.Ready).notice
        assertEquals(PcSwitchNoticeSeverity.Warning, notice?.severity)
        assertEquals(
            "message-${R.string.pc_switch_control_no_external_switches}",
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

    private fun viewModel(
        host: FakeChooserHost,
        rememberProfile: (String, String) -> Unit = { _, _ -> }
    ) = PcSwitchControlChooserViewModel(
        host = host,
        rememberedProfileId = { null },
        rememberProfile = rememberProfile,
        message = { resourceId, _ -> "message-$resourceId" }
    )

    private class FakeChooserHost(
        var catalogResult: PcSwitchCatalogResult? = null,
        private val startResult: PcSwitchControlStartResult =
            PcSwitchControlStartResult.Started,
        private val allowStart: CompletableDeferred<Unit>? = null
    ) : PcSwitchControlChooserHost {
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

        override suspend fun loadProfileCatalog(): PcSwitchCatalogResult {
            loadCount++
            return catalogResult ?: PcSwitchCatalogResult.Loaded(catalog)
        }

        override fun currentPcId(): String = "pc"

        override fun configuredExternalSwitchCount(): Int = 1

        override suspend fun start(
            profile: PcSwitchProfileSummary,
            usesLegacyGridProtocol: Boolean
        ): PcSwitchControlStartResult {
            allowStart?.await()
            return startResult
        }
    }
}
