package com.enaboapps.switchify.service.core

import android.graphics.PointF
import com.enaboapps.switchify.R
import com.enaboapps.switchify.pc.PcCommandResult
import com.enaboapps.switchify.pc.PcControlCommand
import com.enaboapps.switchify.pc.PcMouseRepeatManager
import com.enaboapps.switchify.service.gestures.AutoScrollManager
import com.enaboapps.switchify.service.gestures.GestureLockManager
import com.enaboapps.switchify.service.gestures.GestureRepeatManager
import com.enaboapps.switchify.service.gestures.data.GestureData
import com.enaboapps.switchify.service.gestures.data.GestureType
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlinx.coroutines.test.runTest

class TasksTest {
    private val tasks = Tasks.getInstance()
    private val mouseRepeatManager = PcMouseRepeatManager.instance
    private val repeatManager = GestureRepeatManager.instance
    private val autoScrollManager = AutoScrollManager.getInstance()
    private val lockManager = GestureLockManager.instance
    private val messages = mutableListOf<Int>()

    @Before
    fun setup() {
        mouseRepeatManager.resetForTesting()
        repeatManager.resetForTesting()
        autoScrollManager.resetForTesting()
        lockManager.resetForTesting()
        mouseRepeatManager.setSuppressHudForTesting(true)
        repeatManager.setSuppressHudForTesting(true)
        autoScrollManager.setSuppressHudForTesting(true)
        lockManager.setSuppressHudForTesting(true)
        repeatManager.setMessageRecorderForTesting { messages.add(it) }
        autoScrollManager.setMessageRecorderForTesting { messages.add(it) }
        mouseRepeatManager.setIntervalProviderForTesting { 10000L }
        repeatManager.setInitialRepeatDelayProviderForTesting { 10000L }
        repeatManager.setRepeatDelayProviderForTesting { 10000L }
        autoScrollManager.setAutoScrollEnabledProviderForTesting { true }
        autoScrollManager.setAutoScrollDelayProviderForTesting { 10000L }
        autoScrollManager.setAutoScrollPerformerForTesting { true }
        messages.clear()
    }

    @After
    fun tearDown() {
        mouseRepeatManager.resetForTesting()
        repeatManager.resetForTesting()
        autoScrollManager.resetForTesting()
        lockManager.resetForTesting()
    }

    @Test
    fun hasActiveStoppableTaskFalseForGestureLockWaiting() {
        lockManager.enableLockForNextGesture(showMessage = false)

        assertFalse(tasks.hasActiveStoppableTask())
    }

    @Test
    fun hasActiveStoppableTaskFalseForGestureLockCaptured() {
        lockManager.enableLockForNextGesture(showMessage = false)
        lockManager.setLockedGestureData(testGesture())

        assertFalse(tasks.hasActiveStoppableTask())
    }

    @Test
    fun hasActiveStoppableTaskTrueForRepeatSession() {
        repeatManager.setAutoRepeatEnabledForTesting(true)
        repeatManager.onGesturePerformed(testGesture())

        assertTrue(tasks.hasActiveStoppableTask())
    }

    @Test
    fun hasActiveStoppableTaskTrueForMouseRepeat() = runTest {
        assertTrue(
            mouseRepeatManager.start(PcControlCommand.Move(10, 0), this) {
                PcCommandResult.Ack
            }
        )

        assertTrue(tasks.hasActiveStoppableTask())
        mouseRepeatManager.stop(showMessage = false)
    }

    @Test
    fun hasActiveStoppableTaskTrueForAutoScroll() {
        assertTrue(autoScrollManager.startAutoScroll(scrollGesture()))

        assertTrue(tasks.hasActiveStoppableTask())
    }

    @Test
    fun stopActiveStoppableTaskStopsRepeatFirst() {
        assertTrue(autoScrollManager.startAutoScroll(scrollGesture()))
        repeatManager.setAutoRepeatEnabledForTesting(true)
        repeatManager.onGesturePerformed(testGesture())
        messages.clear()

        assertTrue(tasks.stopActiveStoppableTask())

        assertFalse(repeatManager.isRepeatSessionActive())
        assertTrue(autoScrollManager.isAutoScrolling())
        assertEquals(
            listOf(
                R.string.gesture_repeat_stopped,
                R.string.gesture_repeat_waiting
            ),
            messages
        )
    }

    @Test
    fun stopActiveStoppableTaskStopsMouseRepeatFirst() = runTest {
        assertTrue(autoScrollManager.startAutoScroll(scrollGesture()))
        repeatManager.setAutoRepeatEnabledForTesting(true)
        repeatManager.onGesturePerformed(testGesture())
        assertTrue(
            mouseRepeatManager.start(PcControlCommand.Scroll(0, 5), this) {
                PcCommandResult.Ack
            }
        )

        assertTrue(tasks.stopActiveStoppableTask())

        assertFalse(mouseRepeatManager.isRepeating())
        assertTrue(repeatManager.isRepeatSessionActive())
        assertTrue(autoScrollManager.isAutoScrolling())
    }

    @Test
    fun stopActiveStoppableTaskStopsRepeatDuringInitialDelay() {
        repeatManager.setAutoRepeatEnabledForTesting(true)
        repeatManager.onGesturePerformed(testGesture())

        assertTrue(tasks.stopActiveStoppableTask())

        assertFalse(repeatManager.isRepeatSessionActive())
        assertTrue(repeatManager.isWaitingForGesture())
    }

    @Test
    fun stopActiveStoppableTaskStopsAutoScroll() {
        assertTrue(autoScrollManager.startAutoScroll(scrollGesture()))
        messages.clear()

        assertTrue(tasks.stopActiveStoppableTask())

        assertFalse(autoScrollManager.isAutoScrolling())
        assertEquals(listOf(R.string.hud_auto_scroll_stopped), messages)
    }

    @Test
    fun stopActiveStoppableTaskDoesNotDisableGestureLock() {
        lockManager.enableLockForNextGesture(showMessage = false)
        lockManager.setLockedGestureData(testGesture())

        assertFalse(tasks.stopActiveStoppableTask())

        assertTrue(lockManager.isGestureLockEngaged())
        assertNotNull(lockManager.getLockedGestureData())
    }

    @Test
    fun stopActiveStoppableTaskIgnoresWaitingModes() {
        repeatManager.setAutoRepeatEnabledForTesting(true)
        lockManager.enableLockForNextGesture(showMessage = false)

        assertFalse(tasks.stopActiveStoppableTask())

        assertTrue(repeatManager.isWaitingForGesture())
        assertTrue(lockManager.isLocked())
        assertFalse(lockManager.isGestureLockEngaged())
    }

    @Test
    fun compatibilityWrapperStopsActiveTask() {
        repeatManager.setAutoRepeatEnabledForTesting(true)
        repeatManager.onGesturePerformed(testGesture())

        assertTrue(tasks.stopOngoingTaskForSwitchPress())
        assertFalse(repeatManager.isRepeatSessionActive())
    }

    private fun testGesture(): GestureData {
        return GestureData(
            gestureType = GestureType.TAP,
            startPoint = PointF(10f, 20f)
        )
    }

    private fun scrollGesture(): GestureData {
        return GestureData(
            gestureType = GestureType.SCROLL_DOWN,
            startPoint = PointF(10f, 20f)
        )
    }
}
