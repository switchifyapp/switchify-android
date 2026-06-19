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
    private val messages = mutableListOf<Int>()
    private var autoRepeatEnabled = false
    private var autoReenableEnabled = false
    private var repeatDelay = GestureRepeatManager.DEFAULT_REPEAT_DELAY

    @Before
    fun setup() {
        GestureModePolicy.resetForTesting()
        lockManager.resetForTesting()
        repeatManager.resetForTesting()
        lockManager.setSuppressHudForTesting(true)
        repeatManager.setSuppressHudForTesting(true)
        messages.clear()
        autoRepeatEnabled = false
        autoReenableEnabled = false
        repeatDelay = GestureRepeatManager.DEFAULT_REPEAT_DELAY
        lockManager.setMessageRecorderForTesting { messages.add(it) }
        repeatManager.setMessageRecorderForTesting { messages.add(it) }
        repeatManager.setAutoRepeatProviderForTesting { autoRepeatEnabled }
        repeatManager.setAutoRepeatSetterForTesting { autoRepeatEnabled = it }
        repeatManager.setRepeatDelayProviderForTesting { repeatDelay }
        GestureModePolicy.setPreferenceAccessorsForTesting(
            repeatProvider = { autoRepeatEnabled },
            rearmProvider = { autoReenableEnabled },
            repeatSetter = { autoRepeatEnabled = it },
            rearmSetter = { autoReenableEnabled = it }
        )
    }

    @After
    fun tearDown() {
        lockManager.resetForTesting()
        repeatManager.resetForTesting()
        GestureModePolicy.resetForTesting()
    }

    @Test
    fun turningRepeatOnDoesNotEnableGestureLock() {
        repeatManager.toggleAutoRepeatForTesting(syncGestureLock = true)

        assertTrue(autoRepeatEnabled)
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
        autoRepeatEnabled = true
        performGestureForRepeat()
        assertTrue(repeatManager.isRepeating())
        messages.clear()

        repeatManager.toggleAutoRepeatForTesting(syncGestureLock = true)

        assertFalse(autoRepeatEnabled)
        assertFalse(repeatManager.isRepeating())
        assertFalse(lockManager.isLocked())
        assertNull(lockManager.getLockedGestureData())
        assertNull(repeatManager.getRepeatedGestureDataForTesting())
        assertEquals(listOf(R.string.gesture_repeat_disabled), messages)
    }

    @Test
    fun capturedGestureStartsRepeatWhenRepeatEnabledWithoutGestureLock() {
        autoRepeatEnabled = true

        val gestureData = performGestureForRepeat()

        assertFalse(lockManager.isLocked())
        assertTrue(repeatManager.isRepeating())
        assertEquals(gestureData, repeatManager.getRepeatedGestureDataForTesting())
        assertTrue(messages.contains(R.string.gesture_repeat_started))
    }

    @Test
    fun capturedGestureDoesNotStartRepeatWhenRepeatDisabled() {
        autoRepeatEnabled = false

        repeatManager.onGesturePerformed(testGesture())

        assertFalse(repeatManager.isRepeating())
        assertFalse(messages.contains(R.string.gesture_repeat_started))
    }

    @Test
    fun stopRepeatReturnsTrueOnlyWhileActive() {
        autoRepeatEnabled = true
        performGestureForRepeat()

        assertTrue(repeatManager.stopRepeat())
        assertFalse(repeatManager.stopRepeat())
    }

    @Test
    fun stopRepeatForSwitchPressLeavesRepeatEnabledAndWaiting() {
        autoRepeatEnabled = true
        performGestureForRepeat()
        messages.clear()

        assertTrue(repeatManager.stopRepeatForSwitchPress())

        assertTrue(autoRepeatEnabled)
        assertFalse(repeatManager.isRepeating())
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
    fun turningRepeatOnDisablesRearm() {
        autoReenableEnabled = true

        repeatManager.toggleAutoRepeatForTesting(syncGestureLock = false)

        assertTrue(autoRepeatEnabled)
        assertFalse(autoReenableEnabled)
    }

    @Test
    fun repeatedGestureDoesNotRecaptureItself() {
        autoRepeatEnabled = true
        val firstGesture = performGestureForRepeat()
        val secondGesture = testGesture(x = 30f, y = 40f)

        repeatManager.onGesturePerformed(secondGesture)

        assertTrue(repeatManager.isRepeating())
        assertEquals(firstGesture, repeatManager.getRepeatedGestureDataForTesting())
    }

    @Test
    fun delayIsReadFromProviderAndCoerced() {
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
}
