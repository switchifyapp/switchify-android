package com.enaboapps.switchify.screens.pcswitchcontrol

import com.enaboapps.switchify.pc.PcCommandResult
import com.enaboapps.switchify.pc.PcControlCommand
import com.enaboapps.switchify.pc.PcPointerMovementProfile
import com.enaboapps.switchify.pc.PcServiceConnectionState
import com.enaboapps.switchify.service.pcswitchcontrol.PcSwitchControlForwarder
import com.enaboapps.switchify.service.pcswitchcontrol.PcSwitchControlHost
import com.enaboapps.switchify.switches.SwitchEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PcSwitchControlActivityPolicyTest {
    @Test
    fun onlyCurrentServiceForwarderKeepsActivityOpen() = runTest {
        val first = PcSwitchControlForwarder(FakeHost(), backgroundScope)
        val replacement = PcSwitchControlForwarder(FakeHost(), backgroundScope)

        assertFalse(shouldCloseForStaleForwarder(first, first))
        assertTrue(shouldCloseForStaleForwarder(first, replacement))
        assertTrue(shouldCloseForStaleForwarder(first, null))
        assertTrue(shouldCloseForStaleForwarder(null, first))
    }

    private class FakeHost : PcSwitchControlHost {
        override val connectionState: StateFlow<PcServiceConnectionState> =
            MutableStateFlow(PcServiceConnectionState.Disconnected)

        override fun currentPointerProfile(): PcPointerMovementProfile? = null

        override fun currentPcName(): String? = null

        override fun configuredSwitches(): List<SwitchEvent> = emptyList()

        override fun holdToStopDurationMs(): Long =
            PcSwitchControlForwarder.DEFAULT_HOLD_TO_STOP_MS

        override fun suspendScanning() = Unit

        override fun restoreScanning() = Unit

        override fun maintainConnection() = Unit

        override fun releaseConnection() = Unit

        override suspend fun send(command: PcControlCommand): PcCommandResult =
            PcCommandResult.Ack
    }
}
