package com.enaboapps.switchify.service.gestures

import android.graphics.PointF
import com.enaboapps.switchify.R
import com.enaboapps.switchify.service.gestures.data.GestureData
import com.enaboapps.switchify.service.gestures.data.GestureType
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GestureRepeatManagerTest {
    private val repeatManager = GestureRepeatManager.instance
    private val lockManager = GestureLockManager.instance
    private val autoScrollManager = AutoScrollManager.getInstance()
    private val messages = mutableListOf<Int>()
    private var initialRepeatDelay = GestureRepeatManager.DEFAULT_INITIAL_REPEAT_DELAY
    private var repeatDelay = GestureRepeatManager.DEFAULT_REPEAT_DELAY

    @Before
    fun setup() {
        lockManager.resetForTesting()
        repeatManager.resetForTesting()
        autoScrollManager.resetForTesting()
        lockManager.setSuppressHudForTesting(true)
        repeatManager.setSuppressHudForTesting(true)
        autoScrollManager.setSuppressHudForTesting(true)
        messages.clear()
        initialRepeatDelay = GestureRepeatManager.DEFAULT_INITIAL_REPEAT_DELAY
        repeatDelay = GestureRepeatManager.DEFAULT_REPEAT_DELAY
        lockManager.setMessageRecorderForTesting { messages.add(it) }
        repeatManager.setMessageRecorderForTesting { messages.add(it) }
        autoScrollManager.setMessageRecorderForTesting { messages.add(it) }
        repeatManager.setInitialRepeatDelayProviderForTesting { initialRepeatDelay }
        repeatManager.setRepeatDelayProviderForTesting { repeatDelay }
        autoScrollManager.setAutoScrollEnabledProviderForTesting { true }
        autoScrollManager.setAutoScrollDelayProviderForTesting { 10000L }
        autoScrollManager.setAutoScrollPerformerForTesting { true }
    }

    @After
    fun tearDown() {
        lockManager.resetForTesting()
        repeatManager.resetForTesting()
        autoScrollManager.resetForTesting()
    }

    @Test
    fun repeatDefaultsOffAfterInit() {
        assertFalse(repeatManager.isAutoRepeatEnabled())
        assertFalse(repeatManager.isWaitingForGesture())
    }

    @Test
    fun turningRepeatOnDoesNotEnableGestureLock() {
        repeatManager.toggleAutoRepeatForTesting(syncGestureLock = true)

        assertTrue(repeatManager.isAutoRepeatEnabled())
        assertFalse(lockManager.isLocked())
        assertFalse(lockManager.isGestureLockEngaged())
        assertNull(lockManager.getLockedGestureData())
        assertTrue(repeatManager.isWaitingForGesture())
        assertEquals(
            listOf(
                R.string.gesture_repeat_enabled,
                R.string.gesture_repeat_waiting
            ),
            messages
        )
    }

    @Test
    fun disablingStopsRepeatAndClearsState() {
        repeatManager.setAutoRepeatEnabledForTesting(true)
        performGestureForRepeat()
        assertTrue(repeatManager.isRepeating())
        messages.clear()

        repeatManager.toggleAutoRepeatForTesting(syncGestureLock = true)

        assertFalse(repeatManager.isAutoRepeatEnabled())
        assertFalse(repeatManager.isRepeating())
        assertFalse(lockManager.isLocked())
        assertNull(lockManager.getLockedGestureData())
        assertNull(repeatManager.getRepeatedGestureDataForTesting())
        assertEquals(listOf(R.string.gesture_repeat_disabled), messages)
    }

    @Test
    fun capturedGestureStartsRepeatWhenRepeatEnabledWithoutGestureLock() {
        repeatManager.setAutoRepeatEnabledForTesting(true)

        val gestureData = performGestureForRepeat()

        assertFalse(lockManager.isLocked())
        assertTrue(repeatManager.isRepeating())
        assertTrue(repeatManager.isRepeatSessionActive())
        assertEquals(gestureData, repeatManager.getRepeatedGestureDataForTesting())
        assertTrue(messages.contains(R.string.gesture_repeat_started))
    }

    @Test
    fun repeatSessionActiveAsSoonAsGestureCaptured() {
        initialRepeatDelay = 10000L
        repeatManager.setAutoRepeatEnabledForTesting(true)

        performGestureForRepeat()

        assertTrue(repeatManager.isRepeatSessionActive())
        assertFalse(repeatManager.isWaitingForGesture())
    }

    @Test
    fun capturedGestureDoesNotStartRepeatWhenRepeatDisabled() {
        repeatManager.setAutoRepeatEnabledForTesting(false)

        repeatManager.onGesturePerformed(testGesture())

        assertFalse(repeatManager.isRepeating())
        assertFalse(messages.contains(R.string.gesture_repeat_started))
    }

    @Test
    fun stopRepeatReturnsTrueOnlyWhileActive() {
        repeatManager.setAutoRepeatEnabledForTesting(true)
        performGestureForRepeat()

        assertTrue(repeatManager.stopRepeat())
        assertFalse(repeatManager.stopRepeat())
    }

    @Test
    fun stopRepeatForSwitchPressLeavesRepeatEnabledAndWaiting() {
        repeatManager.setAutoRepeatEnabledForTesting(true)
        performGestureForRepeat()
        messages.clear()

        assertTrue(repeatManager.stopRepeatForSwitchPress())

        assertTrue(repeatManager.isAutoRepeatEnabled())
        assertFalse(repeatManager.isRepeating())
        assertFalse(repeatManager.isRepeatSessionActive())
        assertTrue(repeatManager.isWaitingForGesture())
        assertFalse(lockManager.isLocked())
        assertFalse(lockManager.isGestureLockEngaged())
        assertNull(lockManager.getLockedGestureData())
        assertEquals(
            listOf(
                R.string.gesture_repeat_stopped,
                R.string.gesture_repeat_waiting
            ),
            messages
        )
    }

    @Test
    fun stopRepeatForSwitchPressConsumesDuringInitialDelay() {
        initialRepeatDelay = 10000L
        repeatManager.setAutoRepeatEnabledForTesting(true)
        performGestureForRepeat()
        messages.clear()

        assertTrue(repeatManager.stopRepeatForSwitchPress())

        assertTrue(repeatManager.isAutoRepeatEnabled())
        assertFalse(repeatManager.isRepeatSessionActive())
        assertTrue(repeatManager.isWaitingForGesture())
        assertNull(repeatManager.getRepeatedGestureDataForTesting())
        assertEquals(
            listOf(
                R.string.gesture_repeat_stopped,
                R.string.gesture_repeat_waiting
            ),
            messages
        )
    }

    @Test
    fun turningRepeatOnBlockedWhenRearmEnabled() {
        lockManager.setAutoReenableEnabledForTesting(true)

        repeatManager.toggleAutoRepeatForTesting(syncGestureLock = false)

        assertFalse(repeatManager.isAutoRepeatEnabled())
        assertTrue(lockManager.isAutoReenableEnabled())
        assertEquals(listOf(R.string.gesture_mode_blocked_rearm_enabled_for_repeat), messages)
    }

    @Test
    fun turningRepeatOnBlockedWhenGestureLockEnabled() {
        lockManager.toggleGestureLock()
        messages.clear()

        repeatManager.toggleAutoRepeatForTesting(syncGestureLock = false)

        assertFalse(repeatManager.isAutoRepeatEnabled())
        assertTrue(lockManager.isLocked())
        assertEquals(listOf(R.string.gesture_mode_blocked_lock_enabled_for_repeat), messages)
    }

    @Test
    fun turningRepeatOnBlockedWhenAutoScrollActive() {
        assertTrue(autoScrollManager.startAutoScroll(scrollGesture()))
        messages.clear()

        repeatManager.toggleAutoRepeatForTesting(syncGestureLock = false)

        assertFalse(repeatManager.isAutoRepeatEnabled())
        assertTrue(autoScrollManager.isAutoScrolling())
        assertEquals(listOf(R.string.gesture_mode_blocked_auto_scroll_enabled_for_repeat), messages)
    }

    @Test
    fun repeatedGestureDoesNotRecaptureItself() {
        repeatManager.setAutoRepeatEnabledForTesting(true)
        val firstGesture = performGestureForRepeat()
        val secondGesture = testGesture(x = 30f, y = 40f)

        repeatManager.onGesturePerformed(secondGesture)

        assertTrue(repeatManager.isRepeating())
        assertEquals(firstGesture, repeatManager.getRepeatedGestureDataForTesting())
    }

    @Test
    fun initialDelayIsReadFromProviderAndCoerced() {
        initialRepeatDelay = -1L
        assertEquals(
            GestureRepeatManager.MIN_INITIAL_REPEAT_DELAY,
            repeatManager.getInitialRepeatDelayForTesting()
        )

        initialRepeatDelay = 12000L
        assertEquals(
            GestureRepeatManager.MAX_INITIAL_REPEAT_DELAY,
            repeatManager.getInitialRepeatDelayForTesting()
        )

        initialRepeatDelay = 750L
        assertEquals(750L, repeatManager.getInitialRepeatDelayForTesting())
    }

    @Test
    fun repeatDelayStillUsesMinimumOf250() {
        repeatDelay = 100L
        assertEquals(
            GestureRepeatManager.MIN_REPEAT_DELAY,
            repeatManager.getRepeatDelayForTesting()
        )

        repeatDelay = 12000L
        assertEquals(
            GestureRepeatManager.MAX_REPEAT_DELAY,
            repeatManager.getRepeatDelayForTesting()
        )

        repeatDelay = 750L
        assertEquals(750L, repeatManager.getRepeatDelayForTesting())
    }

    @Test
    fun turningRepeatOffClearsInitialDelayJobBeforeFirstRepeat() {
        initialRepeatDelay = 10000L
        repeatManager.setAutoRepeatEnabledForTesting(true)
        performGestureForRepeat()
        messages.clear()

        repeatManager.toggleAutoRepeatForTesting(syncGestureLock = true)

        assertFalse(repeatManager.isAutoRepeatEnabled())
        assertFalse(repeatManager.isRepeating())
        assertFalse(repeatManager.isRepeatSessionActive())
        assertNull(repeatManager.getRepeatedGestureDataForTesting())
        assertEquals(listOf(R.string.gesture_repeat_disabled), messages)
    }

    @Test
    fun turningRepeatOffClearsRepeatSession() {
        repeatManager.setAutoRepeatEnabledForTesting(true)
        performGestureForRepeat()

        repeatManager.toggleAutoRepeatForTesting(syncGestureLock = true)

        assertFalse(repeatManager.isRepeatSessionActive())
        assertFalse(repeatManager.isAutoRepeatEnabled())
    }

    @Test
    fun clearServiceStateStopsRepeatAndDisablesRepeat() {
        repeatManager.setAutoRepeatEnabledForTesting(true)
        performGestureForRepeat()

        repeatManager.clearServiceState()

        assertFalse(repeatManager.isAutoRepeatEnabled())
        assertFalse(repeatManager.isRepeating())
        assertFalse(repeatManager.isRepeatSessionActive())
        assertFalse(repeatManager.isWaitingForGesture())
        assertNull(repeatManager.getRepeatedGestureDataForTesting())
    }

    @Test
    fun clearServiceStateClearsRepeatSession() {
        repeatManager.setAutoRepeatEnabledForTesting(true)
        performGestureForRepeat()

        repeatManager.clearServiceState()

        assertFalse(repeatManager.isRepeatSessionActive())
    }

    @Test
    fun turnAutoRepeatOffForGesturePatternStartClearsWaitingRepeatSilently() {
        repeatManager.setAutoRepeatEnabledForTesting(true)
        messages.clear()

        repeatManager.turnAutoRepeatOffForGesturePatternStart()

        assertFalse(repeatManager.isAutoRepeatEnabled())
        assertFalse(repeatManager.isWaitingForGesture())
        assertEquals(emptyList<Int>(), messages)
    }

    @Test
    fun turnAutoRepeatOffForGesturePatternStartStopsActiveRepeatSilently() {
        repeatManager.setAutoRepeatEnabledForTesting(true)
        performGestureForRepeat()
        messages.clear()

        repeatManager.turnAutoRepeatOffForGesturePatternStart()

        assertFalse(repeatManager.isAutoRepeatEnabled())
        assertFalse(repeatManager.isRepeating())
        assertFalse(repeatManager.isRepeatSessionActive())
        assertNull(repeatManager.getRepeatedGestureDataForTesting())
        assertEquals(emptyList<Int>(), messages)
    }

    private fun performGestureForRepeat(): GestureData {
        val gestureData = testGesture()
        repeatManager.onGesturePerformed(gestureData)
        return gestureData
    }

    private fun testGesture(x: Float = 10f, y: Float = 20f): GestureData {
        return GestureData(
            gestureType = GestureType.TAP,
            startPoint = PointF(x, y)
        )
    }

    private fun scrollGesture(): GestureData {
        return GestureData(
            gestureType = GestureType.SCROLL_DOWN,
            startPoint = PointF(10f, 20f)
        )
    }
}
