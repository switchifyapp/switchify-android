package com.enaboapps.switchify.screens.pcswitchforwarding

import com.enaboapps.switchify.pc.PcCommandResult
import com.enaboapps.switchify.pc.PcControlCommand
import com.enaboapps.switchify.pc.PcPointerMovementProfile
import com.enaboapps.switchify.pc.PcServiceConnectionState
import com.enaboapps.switchify.service.pcswitchforwarding.PcSwitchForwardingController
import com.enaboapps.switchify.service.pcswitchforwarding.PcSwitchForwardingHost
import com.enaboapps.switchify.switches.SwitchEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PcSwitchForwardingActivityPolicyTest {
    @Test
    fun onlyCurrentServiceControllerKeepsActivityOpen() = runTest {
        val first = PcSwitchForwardingController(FakeHost(), backgroundScope)
        val replacement = PcSwitchForwardingController(FakeHost(), backgroundScope)

        assertFalse(shouldCloseForStaleController(first, first))
        assertTrue(shouldCloseForStaleController(first, replacement))
        assertTrue(shouldCloseForStaleController(first, null))
        assertTrue(shouldCloseForStaleController(null, first))
    }

    private class FakeHost : PcSwitchForwardingHost {
        override val connectionState: StateFlow<PcServiceConnectionState> =
            MutableStateFlow(PcServiceConnectionState.Disconnected)

        override fun currentPointerProfile(): PcPointerMovementProfile? = null

        override fun currentPcName(): String? = null

        override fun configuredSwitches(): List<SwitchEvent> = emptyList()

        override fun holdToStopDurationMs(): Long =
            PcSwitchForwardingController.DEFAULT_HOLD_TO_STOP_MS

        override fun suspendScanning() = Unit

        override fun restoreScanning() = Unit

        override fun maintainConnection() = Unit

        override fun releaseConnection() = Unit

        override suspend fun send(command: PcControlCommand): PcCommandResult =
            PcCommandResult.Ack
    }
}
