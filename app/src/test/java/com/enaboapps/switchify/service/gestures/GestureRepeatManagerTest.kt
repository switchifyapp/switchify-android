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
    private var repeatDelay = GestureRepeatManager.DEFAULT_REPEAT_DELAY

    @Before
    fun setup() {
        lockManager.resetForTesting()
        repeatManager.resetForTesting()
        lockManager.setSuppressHudForTesting(true)
        repeatManager.setSuppressHudForTesting(true)
        messages.clear()
        autoRepeatEnabled = false
        repeatDelay = GestureRepeatManager.DEFAULT_REPEAT_DELAY
        lockManager.setMessageRecorderForTesting { messages.add(it) }
        repeatManager.setMessageRecorderForTesting { messages.add(it) }
        repeatManager.setAutoRepeatProviderForTesting { autoRepeatEnabled }
        repeatManager.setAutoRepeatSetterForTesting { autoRepeatEnabled = it }
        repeatManager.setRepeatDelayProviderForTesting { repeatDelay }
    }

    @After
    fun tearDown() {
        lockManager.resetForTesting()
        repeatManager.resetForTesting()
    }

    @Test
    fun enablingWithSyncTurnsGestureLockOn() {
        repeatManager.toggleAutoRepeatForTesting(syncGestureLock = true)

        assertTrue(autoRepeatEnabled)
        assertTrue(lockManager.isLocked())
        assertFalse(lockManager.isGestureLockEngaged())
        assertNull(lockManager.getLockedGestureData())
        assertEquals(
            listOf(
                R.string.gesture_repeat_enabled,
                R.string.gesture_lock_enabled
            ),
            messages
        )
    }

    @Test
    fun disablingStopsRepeatAndClearsState() {
        autoRepeatEnabled = true
        lockWithGesture()
        assertTrue(repeatManager.isRepeating())
        messages.clear()

        repeatManager.toggleAutoRepeatForTesting(syncGestureLock = true)

        assertFalse(autoRepeatEnabled)
        assertFalse(repeatManager.isRepeating())
        assertFalse(lockManager.isLocked())
        assertNull(lockManager.getLockedGestureData())
        assertEquals(
            listOf(
                R.string.gesture_repeat_disabled,
                R.string.gesture_lock_disabled
            ),
            messages
        )
    }

    @Test
    fun capturedGestureStartsRepeatWhenSettingIsEnabled() {
        autoRepeatEnabled = true

        lockWithGesture()

        assertTrue(repeatManager.isRepeating())
        assertTrue(messages.contains(R.string.gesture_repeat_started))
    }

    @Test
    fun capturedGestureDoesNotStartRepeatWhenSettingIsDisabled() {
        autoRepeatEnabled = false

        lockWithGesture()

        assertFalse(repeatManager.isRepeating())
        assertFalse(messages.contains(R.string.gesture_repeat_started))
    }

    @Test
    fun stopRepeatReturnsTrueOnlyWhileActive() {
        autoRepeatEnabled = true
        lockWithGesture()

        assertTrue(repeatManager.stopRepeat())
        assertFalse(repeatManager.stopRepeat())
    }

    @Test
    fun stopRepeatForSwitchPressRearmsGestureLockWhenStillEnabled() {
        autoRepeatEnabled = true
        lockWithGesture()

        assertTrue(repeatManager.stopRepeatForSwitchPress())

        assertFalse(repeatManager.isRepeating())
        assertTrue(lockManager.isLocked())
        assertFalse(lockManager.isGestureLockEngaged())
        assertNull(lockManager.getLockedGestureData())
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

    private fun lockWithGesture() {
        lockManager.toggleGestureLock()
        lockManager.setLockedGestureData(
            GestureData(
                gestureType = GestureType.TAP,
                startPoint = PointF(10f, 20f)
            )
        )
        assertTrue(lockManager.isGestureLockEngaged())
    }
}
