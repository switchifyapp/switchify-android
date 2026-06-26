package com.enaboapps.switchify.pc

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PcMouseRepeatManagerTest {
    private val repeatManager = PcMouseRepeatManager.instance

    @After
    fun tearDown() {
        repeatManager.resetForTesting()
    }

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
        repeatManager.setSuppressHudForTesting(true)
        repeatManager.setIntervalProviderForTesting { 250L }

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
        repeatManager.setEnabledProviderForTesting { false }

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
    fun stopForSwitchPressStopsRepeat() = runTest {
        repeatManager.setSuppressHudForTesting(true)

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
        repeatManager.setSuppressHudForTesting(true)
        repeatManager.setIntervalProviderForTesting { 100L }

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
}
