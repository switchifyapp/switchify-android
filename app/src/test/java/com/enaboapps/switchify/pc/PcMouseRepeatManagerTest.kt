package com.enaboapps.switchify.pc

import com.enaboapps.switchify.R
import com.enaboapps.switchify.service.window.MessageSeverity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PcMouseRepeatManagerTest {
    @Test
    fun onlyMovementAndScrollCommandsAreRepeatable() {
        assertTrue(PcMouseRepeatManager.isRepeatable(PcControlCommand.Move(1, 0)))
        assertTrue(PcMouseRepeatManager.isRepeatable(PcControlCommand.Scroll(0, 1)))
        assertFalse(PcMouseRepeatManager.isRepeatable(PcControlCommand.LeftClick))
        assertFalse(PcMouseRepeatManager.isRepeatable(PcControlCommand.DoubleClick))
        assertFalse(PcMouseRepeatManager.isRepeatable(PcControlCommand.RightClick))
        assertFalse(PcMouseRepeatManager.isRepeatable(PcControlCommand.DragStart()))
    }

    @Test
    fun canRepeatFalseWhenDisabled() {
        val repeatManager = repeatManager(enabled = false)

        assertFalse(repeatManager.canRepeat(PcControlCommand.Move(5, 0)))
    }

    @Test
    fun armForInitialSendMakesRepeatImmediatelyStoppable() {
        val repeatManager = repeatManager()

        assertTrue(repeatManager.armForInitialSend(PcControlCommand.Move(5, 0)))

        assertTrue(repeatManager.isRepeating())
        assertTrue(repeatManager.stopForSwitchPress())
        assertFalse(repeatManager.isRepeating())
    }

    @Test
    fun armForInitialSendDoesNotScheduleRepeats() = runTest {
        val commands = mutableListOf<PcControlCommand>()
        val repeatManager = repeatManager(intervalMs = 100L)

        assertTrue(repeatManager.armForInitialSend(PcControlCommand.Move(5, 0)))

        advanceTimeBy(250)
        runCurrent()

        assertEquals(emptyList<PcControlCommand>(), commands)
        repeatManager.stop(showMessage = false)
    }

    @Test
    fun startAfterInitialSendDoesNotSendBeforeInterval() = runTest {
        val commands = mutableListOf<PcControlCommand>()
        val repeatManager = repeatManager(intervalMs = 250L)
        val command = PcControlCommand.Move(5, 0)

        assertTrue(repeatManager.armForInitialSend(command))
        assertTrue(
            repeatManager.startAfterInitialSend(command, this) {
                commands += it
                PcCommandResult.Ack
            }
        )
        runCurrent()

        assertEquals(emptyList<PcControlCommand>(), commands)

        advanceTimeBy(249)
        runCurrent()

        assertEquals(emptyList<PcControlCommand>(), commands)
        repeatManager.stop(showMessage = false)
    }

    @Test
    fun startAfterInitialSendSendsFirstRepeatAfterInterval() = runTest {
        val commands = mutableListOf<PcControlCommand>()
        val repeatManager = repeatManager(intervalMs = 250L)
        val command = PcControlCommand.Move(5, 0)

        assertTrue(repeatManager.armForInitialSend(command))
        assertTrue(
            repeatManager.startAfterInitialSend(command, this) {
                commands += it
                PcCommandResult.Ack
            }
        )

        advanceTimeBy(250)
        runCurrent()

        assertEquals(listOf(PcControlCommand.Move(5, 0)), commands)
        repeatManager.stop(showMessage = false)
    }

    @Test
    fun disabledSettingDoesNotStartRepeat() = runTest {
        val commands = mutableListOf<PcControlCommand>()
        val repeatManager = repeatManager(enabled = false)

        assertFalse(repeatManager.armForInitialSend(PcControlCommand.Scroll(0, 5)))
        assertFalse(
            repeatManager.startAfterInitialSend(PcControlCommand.Scroll(0, 5), this) {
                commands += it
                PcCommandResult.Ack
            }
        )
        runCurrent()

        assertEquals(emptyList<PcControlCommand>(), commands)
    }

    @Test
    fun disablingSettingWhileActiveStopsBeforeNextRepeat() = runTest {
        val commands = mutableListOf<PcControlCommand>()
        val settings = FakeMouseRepeatSettings(intervalMs = 100L)
        val repeatManager = repeatManager(settings)
        val command = PcControlCommand.Scroll(0, 5)

        assertTrue(repeatManager.armForInitialSend(command))
        assertTrue(
            repeatManager.startAfterInitialSend(command, this) {
                commands += it
                PcCommandResult.Ack
            }
        )
        settings.enabled = false

        advanceTimeBy(100)
        runCurrent()

        assertEquals(emptyList<PcControlCommand>(), commands)
        assertFalse(repeatManager.isRepeating())
    }

    @Test
    fun stopForSwitchPressStopsRepeatOnlyWhenActive() = runTest {
        val repeatManager = repeatManager()
        val command = PcControlCommand.Scroll(0, 5)

        assertFalse(repeatManager.stopForSwitchPress())
        assertTrue(repeatManager.armForInitialSend(command))
        assertTrue(
            repeatManager.startAfterInitialSend(command, this) {
                PcCommandResult.Ack
            }
        )

        assertTrue(repeatManager.isRepeating())
        assertTrue(repeatManager.stopForSwitchPress())
        assertFalse(repeatManager.isRepeating())
    }

    @Test
    fun commandFailureStopsRepeat() = runTest {
        val repeatManager = repeatManager(intervalMs = 100L)
        val command = PcControlCommand.Move(5, 0)

        assertTrue(repeatManager.armForInitialSend(command))
        assertTrue(
            repeatManager.startAfterInitialSend(command, this) {
                PcCommandResult.Failed()
            }
        )
        assertTrue(repeatManager.isRepeating())

        advanceTimeBy(100)
        runCurrent()

        assertFalse(repeatManager.isRepeating())
    }

    @Test
    fun pauseForReconnectKeepsRepeatArmedWithoutSending() = runTest {
        val commands = mutableListOf<PcControlCommand>()
        val repeatManager = repeatManager(intervalMs = 100L)
        val command = PcControlCommand.Move(5, 0)

        assertTrue(repeatManager.armForInitialSend(command))
        assertTrue(
            repeatManager.startAfterInitialSend(command, this) {
                commands += it
                PcCommandResult.Ack
            }
        )
        assertTrue(repeatManager.pauseForReconnect(this))

        advanceTimeBy(PcMouseRepeatManager.RECONNECT_GRACE_MS - 1)
        runCurrent()

        assertTrue(repeatManager.isRepeating())
        assertTrue(repeatManager.isPausedForReconnect())
        assertEquals(emptyList<PcControlCommand>(), commands)
        repeatManager.stop(showMessage = false)
    }

    @Test
    fun resumeAfterReconnectRestartsRepeatAfterInterval() = runTest {
        val commands = mutableListOf<PcControlCommand>()
        val repeatManager = repeatManager(intervalMs = 100L)
        val command = PcControlCommand.Scroll(0, 5)

        assertTrue(repeatManager.armForInitialSend(command))
        assertTrue(
            repeatManager.startAfterInitialSend(command, this) {
                commands += it
                PcCommandResult.Ack
            }
        )
        assertTrue(repeatManager.pauseForReconnect(this))

        assertTrue(
            repeatManager.resumeAfterReconnect(this) {
                commands += it
                PcCommandResult.Ack
            }
        )
        advanceTimeBy(99)
        runCurrent()
        assertEquals(emptyList<PcControlCommand>(), commands)

        advanceTimeBy(1)
        runCurrent()

        assertFalse(repeatManager.isPausedForReconnect())
        assertEquals(listOf(command), commands)
        repeatManager.stop(showMessage = false)
    }

    @Test
    fun pauseForReconnectStopsAfterGraceExpires() = runTest {
        val messages = mutableListOf<Int>()
        val repeatManager = repeatManager(intervalMs = 100L, messages = messages)
        val command = PcControlCommand.Move(5, 0)

        assertTrue(repeatManager.armForInitialSend(command))
        assertTrue(
            repeatManager.startAfterInitialSend(command, this) {
                PcCommandResult.Ack
            }
        )
        assertTrue(repeatManager.pauseForReconnect(this))

        advanceTimeBy(PcMouseRepeatManager.RECONNECT_GRACE_MS)
        runCurrent()

        assertFalse(repeatManager.isRepeating())
        assertFalse(repeatManager.isPausedForReconnect())
        assertEquals(
            listOf(
                R.string.pc_mouse_repeat_started,
                R.string.pc_mouse_repeat_stopped
            ),
            messages
        )
    }

    @Test
    fun stopForSwitchPressStopsPausedReconnectRepeat() = runTest {
        val repeatManager = repeatManager(intervalMs = 100L)
        val command = PcControlCommand.Move(5, 0)

        assertTrue(repeatManager.armForInitialSend(command))
        assertTrue(
            repeatManager.startAfterInitialSend(command, this) {
                PcCommandResult.Ack
            }
        )
        assertTrue(repeatManager.pauseForReconnect(this))

        assertTrue(repeatManager.stopForSwitchPress())

        assertFalse(repeatManager.isRepeating())
        assertFalse(repeatManager.isPausedForReconnect())
    }

    @Test
    fun failedRepeatCommandPausesWhenReconnectPredicateIsTrue() = runTest {
        val repeatManager = repeatManager(intervalMs = 100L)
        val command = PcControlCommand.Move(5, 0)

        assertTrue(repeatManager.armForInitialSend(command))
        assertTrue(
            repeatManager.startAfterInitialSend(
                command = command,
                scope = this,
                sendRepeatedCommand = { PcCommandResult.Failed() },
                shouldPauseForReconnect = { true }
            )
        )

        advanceTimeBy(100)
        runCurrent()

        assertTrue(repeatManager.isRepeating())
        assertTrue(repeatManager.isPausedForReconnect())
        repeatManager.stop(showMessage = false)
    }

    @Test
    fun failedRepeatCommandStopsWhenReconnectPredicateIsFalse() = runTest {
        val repeatManager = repeatManager(intervalMs = 100L)
        val command = PcControlCommand.Move(5, 0)

        assertTrue(repeatManager.armForInitialSend(command))
        assertTrue(
            repeatManager.startAfterInitialSend(
                command = command,
                scope = this,
                sendRepeatedCommand = { PcCommandResult.Failed() },
                shouldPauseForReconnect = { false }
            )
        )

        advanceTimeBy(100)
        runCurrent()

        assertFalse(repeatManager.isRepeating())
        assertFalse(repeatManager.isPausedForReconnect())
    }

    @Test
    fun authFailureStopsRepeat() = runTest {
        val repeatManager = repeatManager(intervalMs = 100L)
        val command = PcControlCommand.Move(5, 0)

        assertTrue(repeatManager.armForInitialSend(command))
        assertTrue(
            repeatManager.startAfterInitialSend(command, this) {
                PcCommandResult.AuthFailed()
            }
        )

        advanceTimeBy(100)
        runCurrent()

        assertFalse(repeatManager.isRepeating())
    }

    @Test
    fun armForInitialSendRecordsStartedMessage() {
        val messages = mutableListOf<Int>()
        val repeatManager = repeatManager(messages = messages)

        assertTrue(repeatManager.armForInitialSend(PcControlCommand.Move(5, 0)))

        assertEquals(listOf(R.string.pc_mouse_repeat_started), messages)
        repeatManager.stop(showMessage = false)
    }

    @Test
    fun stopForSwitchPressRecordsStoppedMessage() = runTest {
        val messages = mutableListOf<Int>()
        val repeatManager = repeatManager(messages = messages)
        val command = PcControlCommand.Move(5, 0)

        assertTrue(repeatManager.armForInitialSend(command))
        assertTrue(
            repeatManager.startAfterInitialSend(command, this) {
                PcCommandResult.Ack
            }
        )
        repeatManager.stopForSwitchPress()

        assertEquals(
            listOf(
                R.string.pc_mouse_repeat_started,
                R.string.pc_mouse_repeat_stopped
            ),
            messages
        )
    }

    @Test
    fun startAfterInitialSendDoesNotStartIfStoppedBeforeAck() = runTest {
        val commands = mutableListOf<PcControlCommand>()
        val repeatManager = repeatManager(intervalMs = 100L)
        val command = PcControlCommand.Move(5, 0)

        assertTrue(repeatManager.armForInitialSend(command))
        assertTrue(repeatManager.stopForSwitchPress())

        assertFalse(
            repeatManager.startAfterInitialSend(command, this) {
                commands += it
                PcCommandResult.Ack
            }
        )
        advanceTimeBy(100)
        runCurrent()

        assertEquals(emptyList<PcControlCommand>(), commands)
    }

    @Test
    fun startAfterInitialSendUsesExistingArmedCommand() = runTest {
        val commands = mutableListOf<PcControlCommand>()
        val repeatManager = repeatManager(intervalMs = 100L)
        val command = PcControlCommand.Scroll(0, 5)

        assertTrue(repeatManager.armForInitialSend(command))
        assertTrue(
            repeatManager.startAfterInitialSend(command, this) {
                commands += it
                PcCommandResult.Ack
            }
        )
        advanceTimeBy(99)
        runCurrent()

        assertEquals(emptyList<PcControlCommand>(), commands)

        advanceTimeBy(1)
        runCurrent()

        assertEquals(listOf(command), commands)
        repeatManager.stop(showMessage = false)
    }

    @Test
    fun cancelPendingStartClearsArmedRepeatSilently() {
        val messages = mutableListOf<Int>()
        val repeatManager = repeatManager(messages = messages)

        assertTrue(repeatManager.armForInitialSend(PcControlCommand.Move(5, 0)))
        messages.clear()

        assertTrue(repeatManager.cancelPendingStart(showMessage = false))

        assertFalse(repeatManager.isRepeating())
        assertEquals(emptyList<Int>(), messages)
    }

    @Test
    fun stopForSwitchPressRecordsStoppedMessageWhenPending() {
        val messages = mutableListOf<Int>()
        val repeatManager = repeatManager(messages = messages)

        assertTrue(repeatManager.armForInitialSend(PcControlCommand.Move(5, 0)))
        assertTrue(repeatManager.stopForSwitchPress())

        assertEquals(
            listOf(
                R.string.pc_mouse_repeat_started,
                R.string.pc_mouse_repeat_stopped
            ),
            messages
        )
    }

    private fun repeatManager(
        enabled: Boolean = true,
        intervalMs: Long = 250L,
        messages: MutableList<Int> = mutableListOf()
    ): PcMouseRepeatManager {
        return repeatManager(FakeMouseRepeatSettings(enabled, intervalMs), messages)
    }

    private fun repeatManager(
        settings: PcMouseRepeatSettings,
        messages: MutableList<Int> = mutableListOf()
    ): PcMouseRepeatManager {
        return PcMouseRepeatManager(settings) { messageResId, _: MessageSeverity ->
            messages += messageResId
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
