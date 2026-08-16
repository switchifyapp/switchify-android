package com.enaboapps.switchify.screens.pcswitchforwarding

import android.content.Context
import android.os.SystemClock
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.enaboapps.switchify.pc.PcCommandResult
import com.enaboapps.switchify.pc.PcControlCommand
import com.enaboapps.switchify.pc.PcPointerBounds
import com.enaboapps.switchify.pc.PcPointerCapabilities
import com.enaboapps.switchify.pc.PcPointerDeltas
import com.enaboapps.switchify.pc.PcPointerMovementProfile
import com.enaboapps.switchify.pc.PcProfileCatalogResult
import com.enaboapps.switchify.pc.PcServiceConnectionState
import com.enaboapps.switchify.pc.PcSwitchBindingSummary
import com.enaboapps.switchify.pc.PcSwitchProfileCatalog
import com.enaboapps.switchify.pc.PcSwitchProfileSummary
import com.enaboapps.switchify.service.core.ServiceCore
import com.enaboapps.switchify.service.pcswitchforwarding.PcSwitchForwardingController
import com.enaboapps.switchify.service.pcswitchforwarding.PcSwitchForwardingHost
import com.enaboapps.switchify.switches.SWITCH_EVENT_TYPE_EXTERNAL
import com.enaboapps.switchify.switches.SwitchAction
import com.enaboapps.switchify.switches.SwitchEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PcSwitchForwardingActivityInstrumentedTest {
    @get:Rule
    val timeout: Timeout = Timeout.seconds(20)

    @After
    fun tearDown() {
        wakeDevice()
        runBlocking {
            ServiceCore.takePcSwitchForwardingController()?.destroy()
        }
    }

    @Test
    fun recreationRetainsChooserWithoutReloadingCatalog() {
        val host = FakeHost()
        installController(host)

        ActivityScenario.launch<PcSwitchForwardingActivity>(intent()).use { scenario ->
            waitForIdle()
            assertEquals(1, host.catalogRequestCount)

            scenario.recreate()
            waitForIdle()

            assertEquals(1, host.catalogRequestCount)
        }
    }

    @Test
    fun inactivityTimeoutFinishesActivity() {
        val host = FakeHost()
        val controller = installController(host, inactivityTimeoutMs = 100L)
        runBlocking {
            val catalog = controller.loadProfileCatalog() as
                com.enaboapps.switchify.service.pcswitchforwarding.PcSwitchCatalogResult.Loaded
            controller.start(catalog.catalog.profiles.single(), usesLegacyGridProtocol = false)
        }

        ActivityScenario.launch<PcSwitchForwardingActivity>(intent()).use { scenario ->
            awaitDestroyed(scenario)
        }
    }

    @Test
    fun screenLockFinishesActivity() {
        installController(FakeHost())

        ActivityScenario.launch<PcSwitchForwardingActivity>(intent()).use { scenario ->
            executeShellCommand("input keyevent 26")
            awaitDestroyed(scenario)
        }
    }

    private fun installController(
        host: FakeHost,
        inactivityTimeoutMs: Long = PcSwitchForwardingController.INACTIVITY_TIMEOUT_MS
    ): PcSwitchForwardingController {
        val controller = PcSwitchForwardingController(
            host = host,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
            inactivityTimeoutMs = inactivityTimeoutMs
        )
        ServiceCore.setPcSwitchForwardingController(controller)
        return controller
    }

    private fun awaitDestroyed(scenario: ActivityScenario<PcSwitchForwardingActivity>) {
        repeat(40) {
            waitForIdle()
            if (scenario.state == Lifecycle.State.DESTROYED) return
            SystemClock.sleep(50L)
        }
        assertEquals(Lifecycle.State.DESTROYED, scenario.state)
    }

    private fun intent() = PcSwitchForwardingActivity.createIntent(targetContext())

    private fun waitForIdle() {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    }

    private fun wakeDevice() {
        executeShellCommand("input keyevent 224")
        executeShellCommand("wm dismiss-keyguard")
    }

    private fun executeShellCommand(command: String) {
        InstrumentationRegistry.getInstrumentation()
            .uiAutomation
            .executeShellCommand(command)
            .close()
    }

    private fun targetContext(): Context =
        InstrumentationRegistry.getInstrumentation().targetContext

    private class FakeHost : PcSwitchForwardingHost {
        override val connectionState: StateFlow<PcServiceConnectionState> =
            MutableStateFlow(PcServiceConnectionState.Disconnected)
        var catalogRequestCount = 0

        override fun currentPointerProfile() = PcPointerMovementProfile(
            displayId = "display",
            scaleFactor = 1.0,
            bounds = PcPointerBounds(0, 0, 1920, 1080),
            maxDelta = 500,
            recommendedDeltas = PcPointerDeltas(50, 100, 200),
            capabilities = PcPointerCapabilities(
                supportedCommands = PcSwitchForwardingController.GENERIC_COMMANDS
            )
        )

        override fun currentPcName(): String = "Office PC"

        override fun configuredSwitches(): List<SwitchEvent> = listOf(
            SwitchEvent(
                type = SWITCH_EVENT_TYPE_EXTERNAL,
                name = "Primary",
                code = "20",
                pressAction = SwitchAction(0),
                holdActions = emptyList()
            )
        )

        override fun holdToStopDurationMs(): Long =
            PcSwitchForwardingController.DEFAULT_HOLD_TO_STOP_MS

        override fun suspendScanning() = Unit

        override fun restoreScanning() = Unit

        override fun maintainConnection() = Unit

        override fun releaseConnection() = Unit

        override suspend fun requestProfileCatalog(): PcProfileCatalogResult {
            catalogRequestCount++
            return PcProfileCatalogResult.Loaded(
                PcSwitchProfileCatalog(
                    catalogRevision = 1,
                    profiles = listOf(
                        PcSwitchProfileSummary(
                            id = "builtin.keyboard",
                            version = 1,
                            name = "Generic keyboard",
                            kind = "mapped",
                            bindings = listOf(
                                PcSwitchBindingSummary(1, "Space", "stateful")
                            )
                        )
                    )
                )
            )
        }

        override suspend fun send(command: PcControlCommand): PcCommandResult =
            PcCommandResult.Ack
    }
}
