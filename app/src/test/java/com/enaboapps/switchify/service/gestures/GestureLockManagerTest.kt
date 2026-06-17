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

class GestureLockManagerTest {
    private val manager = GestureLockManager.instance
    private val messages = mutableListOf<Int>()

    @Before
    fun setup() {
        manager.resetForTesting()
        manager.setSuppressHudForTesting(true)
        messages.clear()
        manager.setMessageRecorderForTesting { messages.add(it) }
    }

    @After
    fun tearDown() {
        manager.resetForTesting()
    }

    @Test
    fun settingOffDisablingWithCapturedGestureTurnsLockOffAndClearsGesture() {
        manager.setAutoReenableProviderForTesting { false }
        lockWithGesture()

        manager.disableLock()

        assertFalse(manager.isLocked())
        assertNull(manager.getLockedGestureData())
        assertFalse(messages.contains(R.string.gesture_lock_rearmed))
    }

    @Test
    fun settingOnDisablingWithCapturedGestureRearmsLockAndClearsGesture() {
        manager.setAutoReenableProviderForTesting { true }
        lockWithGesture()

        manager.disableLock()

        assertTrue(manager.isLocked())
        assertFalse(manager.isGestureLockEngaged())
        assertNull(manager.getLockedGestureData())
        assertTrue(messages.contains(R.string.gesture_lock_rearmed))
    }

    @Test
    fun settingOnDisablingBeforeGestureCapturedLeavesLockOff() {
        manager.setAutoReenableProviderForTesting { true }
        manager.toggleGestureLock()

        manager.disableLock()

        assertFalse(manager.isLocked())
        assertFalse(manager.isGestureLockEngaged())
        assertNull(manager.getLockedGestureData())
        assertEquals(listOf(R.string.gesture_lock_enabled), messages)
    }

    @Test
    fun manualToggleOffWithCapturedGestureRearmsWhenSettingIsOn() {
        manager.setAutoReenableProviderForTesting { true }
        lockWithGesture()

        manager.toggleGestureLock()

        assertTrue(manager.isLocked())
        assertFalse(manager.isGestureLockEngaged())
        assertNull(manager.getLockedGestureData())
        assertEquals(
            listOf(
                R.string.gesture_lock_enabled,
                R.string.gesture_lock_disabled,
                R.string.gesture_lock_rearmed
            ),
            messages
        )
    }

    @Test
    fun timeoutWithCapturedGestureRearmsWhenSettingIsOn() {
        manager.setAutoReenableProviderForTesting { true }
        lockWithGesture()

        manager.handleLockTimeoutForTesting()

        assertTrue(manager.isLocked())
        assertFalse(manager.isGestureLockEngaged())
        assertNull(manager.getLockedGestureData())
        assertEquals(
            listOf(
                R.string.gesture_lock_enabled,
                R.string.gesture_lock_timeout_disabled,
                R.string.gesture_lock_rearmed
            ),
            messages
        )
    }

    @Test
    fun timeoutWithoutCapturedGestureLeavesLockOff() {
        manager.setAutoReenableProviderForTesting { true }
        manager.toggleGestureLock()

        manager.handleLockTimeoutForTesting()

        assertFalse(manager.isLocked())
        assertFalse(manager.isGestureLockEngaged())
        assertNull(manager.getLockedGestureData())
        assertEquals(
            listOf(
                R.string.gesture_lock_enabled,
                R.string.gesture_lock_timeout_disabled
            ),
            messages
        )
    }

    @Test
    fun disableWithoutAutoReenableClearsEvenWhenSettingIsOn() {
        manager.setAutoReenableProviderForTesting { true }
        lockWithGesture()

        manager.disableLock(allowAutoReenable = false)

        assertFalse(manager.isLocked())
        assertFalse(manager.isGestureLockEngaged())
        assertNull(manager.getLockedGestureData())
        assertFalse(messages.contains(R.string.gesture_lock_rearmed))
    }

    @Test
    fun manualToggleOnRecordsEnabledMessage() {
        manager.toggleGestureLock()

        assertEquals(listOf(R.string.gesture_lock_enabled), messages)
    }

    @Test
    fun manualToggleOffWithoutCapturedGestureRecordsDisabledMessage() {
        manager.toggleGestureLock()
        manager.toggleGestureLock()

        assertEquals(
            listOf(
                R.string.gesture_lock_enabled,
                R.string.gesture_lock_disabled
            ),
            messages
        )
    }

    private fun lockWithGesture() {
        manager.toggleGestureLock()
        manager.setLockedGestureData(
            GestureData(
                gestureType = GestureType.TAP,
                startPoint = PointF(10f, 20f)
            )
        )
        assertTrue(manager.isGestureLockEngaged())
    }
}
