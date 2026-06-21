package com.enaboapps.switchify.service.core

import android.graphics.PointF
import com.enaboapps.switchify.R
import com.enaboapps.switchify.service.gestures.AutoScrollManager
import com.enaboapps.switchify.service.gestures.GestureLockManager
import com.enaboapps.switchify.service.gestures.GestureRepeatManager
import com.enaboapps.switchify.service.gestures.data.GestureData
import com.enaboapps.switchify.service.gestures.data.GestureType
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TasksTest {
    private val tasks = Tasks.getInstance()
    private val repeatManager = GestureRepeatManager.instance
    private val autoScrollManager = AutoScrollManager.getInstance()
    private val lockManager = GestureLockManager.instance
    private val messages = mutableListOf<Int>()

    @Before
    fun setup() {
        repeatManager.resetForTesting()
        autoScrollManager.resetForTesting()
        lockManager.resetForTesting()
        repeatManager.setSuppressHudForTesting(true)
        autoScrollManager.setSuppressHudForTesting(true)
        lockManager.setSuppressHudForTesting(true)
        repeatManager.setMessageRecorderForTesting { messages.add(it) }
        autoScrollManager.setMessageRecorderForTesting { messages.add(it) }
        repeatManager.setInitialRepeatDelayProviderForTesting { 10000L }
        repeatManager.setRepeatDelayProviderForTesting { 10000L }
        autoScrollManager.setAutoScrollEnabledProviderForTesting { true }
        autoScrollManager.setAutoScrollDelayProviderForTesting { 10000L }
        autoScrollManager.setAutoScrollPerformerForTesting { true }
        messages.clear()
    }

    @After
    fun tearDown() {
        repeatManager.resetForTesting()
        autoScrollManager.resetForTesting()
        lockManager.resetForTesting()
    }

    @Test
    fun stopOngoingTaskForSwitchPressStopsRepeatBeforeAutoScroll() {
        assertTrue(autoScrollManager.startAutoScroll(scrollGesture()))
        repeatManager.setAutoRepeatEnabledForTesting(true)
        repeatManager.onGesturePerformed(testGesture())
        messages.clear()

        assertTrue(tasks.stopOngoingTaskForSwitchPress())

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
    fun stopOngoingTaskForSwitchPressStopsRepeatDuringInitialDelay() {
        repeatManager.setAutoRepeatEnabledForTesting(true)
        repeatManager.onGesturePerformed(testGesture())
        messages.clear()

        assertTrue(tasks.stopOngoingTaskForSwitchPress())

        assertFalse(repeatManager.isRepeatSessionActive())
        assertTrue(repeatManager.isWaitingForGesture())
    }

    @Test
    fun shouldAbsorbSwitchReleaseTrueWhileRepeatSessionActive() {
        repeatManager.setAutoRepeatEnabledForTesting(true)
        repeatManager.onGesturePerformed(testGesture())

        assertTrue(tasks.shouldAbsorbSwitchRelease())
    }

    @Test
    fun shouldAbsorbSwitchReleaseTrueWhileAutoScrollActive() {
        assertTrue(autoScrollManager.startAutoScroll(scrollGesture()))

        assertTrue(tasks.shouldAbsorbSwitchRelease())
    }

    @Test
    fun shouldAbsorbSwitchReleaseDoesNotStopRepeat() {
        repeatManager.setAutoRepeatEnabledForTesting(true)
        repeatManager.onGesturePerformed(testGesture())

        assertTrue(tasks.shouldAbsorbSwitchRelease())

        assertTrue(repeatManager.isRepeatSessionActive())
    }

    @Test
    fun shouldAbsorbSwitchReleaseAfterActionTrueWhileRepeatWaiting() {
        repeatManager.setAutoRepeatEnabledForTesting(true)

        assertFalse(tasks.shouldAbsorbSwitchRelease())
        assertTrue(tasks.shouldAbsorbSwitchReleaseAfterAction())
    }

    @Test
    fun shouldAbsorbSwitchReleaseAfterActionTrueWhileGestureLockWaiting() {
        lockManager.enableLockForNextGesture(showMessage = false)

        assertFalse(tasks.shouldAbsorbSwitchRelease())
        assertTrue(tasks.shouldAbsorbSwitchReleaseAfterAction())
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
