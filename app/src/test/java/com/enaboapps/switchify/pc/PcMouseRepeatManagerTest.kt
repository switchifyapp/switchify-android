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
    fun firstCommandSendsImmediatelyThenRepeatsAfterInterval() = runTest {
        val commands = mutableListOf<PcControlCommand>()
        val repeatManager = repeatManager(intervalMs = 250L)

        assertTrue(
            repeatManager.start(PcControlCommand.Move(5, 0), this) {
                commands += it
                PcCommandResult.Ack
            }
        )
        runCurrent()

        assertEquals(listOf(PcControlCommand.Move(5, 0)), commands)

        advanceTimeBy(249)
        runCurrent()

        assertEquals(listOf(PcControlCommand.Move(5, 0)), commands)

        advanceTimeBy(1)
        runCurrent()

        assertEquals(
            listOf(
                PcControlCommand.Move(5, 0),
                PcControlCommand.Move(5, 0)
            ),
            commands
        )
        repeatManager.stop(showMessage = false)
    }

    @Test
    fun disabledSettingDoesNotStartRepeat() = runTest {
        val commands = mutableListOf<PcControlCommand>()
        val repeatManager = repeatManager(enabled = false)

        assertFalse(
            repeatManager.start(PcControlCommand.Scroll(0, 5), this) {
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

        assertTrue(
            repeatManager.start(PcControlCommand.Scroll(0, 5), this) {
                commands += it
                PcCommandResult.Ack
            }
        )
        runCurrent()
        settings.enabled = false

        advanceTimeBy(100)
        runCurrent()

        assertEquals(listOf(PcControlCommand.Scroll(0, 5)), commands)
        assertFalse(repeatManager.isRepeating())
    }

    @Test
    fun stopForSwitchPressStopsRepeatOnlyWhenActive() = runTest {
        val repeatManager = repeatManager()

        assertFalse(repeatManager.stopForSwitchPress())
        assertTrue(
            repeatManager.start(PcControlCommand.Scroll(0, 5), this) {
                PcCommandResult.Ack
            }
        )

        assertTrue(repeatManager.isRepeating())
        assertTrue(repeatManager.stopForSwitchPress())
        assertFalse(repeatManager.isRepeating())
    }

    @Test
    fun commandFailureStopsRepeat() = runTest {
        val results = ArrayDeque<PcCommandResult>().apply {
            add(PcCommandResult.Ack)
            add(PcCommandResult.Failed())
        }
        val repeatManager = repeatManager(intervalMs = 100L)

        assertTrue(
            repeatManager.start(PcControlCommand.Move(5, 0), this) {
                results.removeFirst()
            }
        )
        runCurrent()
        assertTrue(repeatManager.isRepeating())

        advanceTimeBy(100)
        runCurrent()

        assertFalse(repeatManager.isRepeating())
    }

    @Test
    fun authFailureStopsRepeat() = runTest {
        val results = ArrayDeque<PcCommandResult>().apply {
            add(PcCommandResult.Ack)
            add(PcCommandResult.AuthFailed())
        }
        val repeatManager = repeatManager(intervalMs = 100L)

        assertTrue(
            repeatManager.start(PcControlCommand.Move(5, 0), this) {
                results.removeFirst()
            }
        )
        runCurrent()

        advanceTimeBy(100)
        runCurrent()

        assertFalse(repeatManager.isRepeating())
    }

    @Test
    fun startAndStopMessagesAreRecordedForUserObservableStops() = runTest {
        val messages = mutableListOf<Int>()
        val repeatManager = repeatManager(messages = messages)

        assertTrue(
            repeatManager.start(PcControlCommand.Move(5, 0), this) {
                PcCommandResult.Ack
            }
        )
        runCurrent()
        repeatManager.stopForSwitchPress()

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
