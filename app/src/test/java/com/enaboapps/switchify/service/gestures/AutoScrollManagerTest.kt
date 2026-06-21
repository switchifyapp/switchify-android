package com.enaboapps.switchify.service.gestures

import android.graphics.PointF
import com.enaboapps.switchify.R
import com.enaboapps.switchify.service.core.Tasks
import com.enaboapps.switchify.service.gestures.data.GestureData
import com.enaboapps.switchify.service.gestures.data.GestureType
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AutoScrollManagerTest {
    private val autoScrollManager = AutoScrollManager.getInstance()
    private val repeatManager = GestureRepeatManager.instance
    private val lockManager = GestureLockManager.instance
    private val messages = mutableListOf<Int>()
    private var autoScrollPreferenceEnabled = true

    @Before
    fun setup() {
        Tasks.getInstance().setOngoingTaskStartedListenerForTesting(null)
        autoScrollManager.resetForTesting()
        repeatManager.resetForTesting()
        lockManager.resetForTesting()
        autoScrollPreferenceEnabled = true
        messages.clear()
        autoScrollManager.setAutoScrollEnabledProviderForTesting { autoScrollPreferenceEnabled }
        autoScrollManager.setAutoScrollDelayProviderForTesting { 10000L }
        autoScrollManager.setAutoScrollPerformerForTesting { true }
        autoScrollManager.setSuppressHudForTesting(true)
        autoScrollManager.setMessageRecorderForTesting { messages.add(it) }
        repeatManager.setSuppressHudForTesting(true)
        lockManager.setSuppressHudForTesting(true)
    }

    @After
    fun tearDown() {
        Tasks.getInstance().setOngoingTaskStartedListenerForTesting(null)
        autoScrollManager.resetForTesting()
        repeatManager.resetForTesting()
        lockManager.resetForTesting()
    }

    @Test
    fun startAutoScrollBlockedWhenRepeatEnabled() {
        repeatManager.setAutoRepeatEnabledForTesting(true)

        assertFalse(autoScrollManager.startAutoScroll(scrollGesture()))

        assertFalse(autoScrollManager.isAutoScrolling())
        assertEquals(listOf(R.string.gesture_mode_blocked_repeat_enabled_for_auto_scroll), messages)
    }

    @Test
    fun startAutoScrollBlockedWhenRearmEnabled() {
        lockManager.setAutoReenableEnabledForTesting(true)

        assertFalse(autoScrollManager.startAutoScroll(scrollGesture()))

        assertFalse(autoScrollManager.isAutoScrolling())
        assertEquals(listOf(R.string.gesture_mode_blocked_rearm_enabled_for_auto_scroll), messages)
    }

    @Test
    fun startAutoScrollBlockedWhenGestureLockEnabled() {
        lockManager.enableLockForNextGesture(showMessage = false)

        assertFalse(autoScrollManager.startAutoScroll(scrollGesture()))

        assertFalse(autoScrollManager.isAutoScrolling())
        assertEquals(listOf(R.string.gesture_mode_blocked_lock_enabled_for_auto_scroll), messages)
    }

    @Test
    fun startAutoScrollAllowedWhenModesOffAndPreferenceEnabled() {
        assertTrue(autoScrollManager.startAutoScroll(scrollGesture()))

        assertTrue(autoScrollManager.isAutoScrolling())
        assertEquals(listOf(R.string.hud_auto_scroll_started), messages)
    }

    @Test
    fun startingAutoScrollNotifiesOngoingTaskStarted() {
        var notificationCount = 0
        Tasks.getInstance().setOngoingTaskStartedListenerForTesting { notificationCount++ }

        assertTrue(autoScrollManager.startAutoScroll(scrollGesture()))

        assertEquals(1, notificationCount)
        assertTrue(autoScrollManager.isAutoScrolling())
    }

    @Test
    fun blockedAutoScrollDoesNotNotifyOngoingTaskStarted() {
        var notificationCount = 0
        Tasks.getInstance().setOngoingTaskStartedListenerForTesting { notificationCount++ }
        repeatManager.setAutoRepeatEnabledForTesting(true)

        assertFalse(autoScrollManager.startAutoScroll(scrollGesture()))

        assertEquals(0, notificationCount)
        assertFalse(autoScrollManager.isAutoScrolling())
    }

    @Test
    fun stopAutoScrollReturnsTrueOnlyWhenActive() {
        assertTrue(autoScrollManager.startAutoScroll(scrollGesture()))
        messages.clear()

        assertTrue(autoScrollManager.stopAutoScroll())
        assertFalse(autoScrollManager.stopAutoScroll())
    }

    @Test
    fun stopAutoScrollShowsStoppedMessage() {
        assertTrue(autoScrollManager.startAutoScroll(scrollGesture()))
        messages.clear()

        autoScrollManager.stopAutoScroll()

        assertEquals(listOf(R.string.hud_auto_scroll_stopped), messages)
    }

    private fun scrollGesture(): GestureData {
        return GestureData(
            gestureType = GestureType.SCROLL_DOWN,
            startPoint = PointF(10f, 20f)
        )
    }
}
