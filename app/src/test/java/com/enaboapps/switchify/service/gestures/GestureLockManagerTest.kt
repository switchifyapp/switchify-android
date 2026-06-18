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
    private val repeatManager = GestureRepeatManager.instance
    private val messages = mutableListOf<Int>()
    private var autoReenableEnabled = false
    private var autoRepeatEnabled = false

    @Before
    fun setup() {
        manager.resetForTesting()
        repeatManager.resetForTesting()
        manager.setSuppressHudForTesting(true)
        repeatManager.setSuppressHudForTesting(true)
        messages.clear()
        autoReenableEnabled = false
        autoRepeatEnabled = false
        manager.setMessageRecorderForTesting { messages.add(it) }
        manager.setAutoReenableProviderForTesting { autoReenableEnabled }
        manager.setAutoReenableSetterForTesting { autoReenableEnabled = it }
        repeatManager.setAutoRepeatProviderForTesting { autoRepeatEnabled }
        repeatManager.setAutoRepeatSetterForTesting { autoRepeatEnabled = it }
        repeatManager.setRepeatDelayProviderForTesting { GestureRepeatManager.DEFAULT_REPEAT_DELAY }
    }

    @After
    fun tearDown() {
        manager.resetForTesting()
        repeatManager.resetForTesting()
    }

    @Test
    fun settingOffDisablingWithCapturedGestureTurnsLockOffAndClearsGesture() {
        autoReenableEnabled = false
        lockWithGesture()

        manager.disableLock()

        assertFalse(manager.isLocked())
        assertNull(manager.getLockedGestureData())
        assertFalse(messages.contains(R.string.gesture_lock_rearmed))
    }

    @Test
    fun settingOnDisablingWithCapturedGestureRearmsLockAndClearsGesture() {
        autoReenableEnabled = true
        lockWithGesture()

        manager.disableLock()

        assertTrue(manager.isLocked())
        assertFalse(manager.isGestureLockEngaged())
        assertNull(manager.getLockedGestureData())
        assertTrue(messages.contains(R.string.gesture_lock_rearmed))
    }

    @Test
    fun settingOnDisablingBeforeGestureCapturedLeavesLockOff() {
        autoReenableEnabled = true
        manager.toggleGestureLock()

        manager.disableLock()

        assertFalse(manager.isLocked())
        assertFalse(manager.isGestureLockEngaged())
        assertNull(manager.getLockedGestureData())
        assertEquals(listOf(R.string.gesture_lock_enabled), messages)
    }

    @Test
    fun manualToggleOffWithCapturedGestureRearmsWhenSettingIsOn() {
        autoReenableEnabled = true
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
        autoReenableEnabled = true
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
        autoReenableEnabled = true
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
        autoReenableEnabled = true
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

    @Test
    fun toggleAutoReenableOnWithSyncTurnsGestureLockOn() {
        autoReenableEnabled = false

        manager.toggleAutoReenableForTesting(syncGestureLock = true)

        assertTrue(autoReenableEnabled)
        assertTrue(manager.isLocked())
        assertFalse(manager.isGestureLockEngaged())
        assertNull(manager.getLockedGestureData())
        assertEquals(
            listOf(
                R.string.gesture_lock_rearm_enabled,
                R.string.gesture_lock_enabled
            ),
            messages
        )
    }

    @Test
    fun toggleAutoReenableOffWithSyncTurnsGestureLockOff() {
        autoReenableEnabled = true
        manager.toggleGestureLock()
        messages.clear()

        manager.toggleAutoReenableForTesting(syncGestureLock = true)

        assertFalse(autoReenableEnabled)
        assertFalse(manager.isLocked())
        assertNull(manager.getLockedGestureData())
        assertEquals(
            listOf(
                R.string.gesture_lock_rearm_disabled,
                R.string.gesture_lock_disabled
            ),
            messages
        )
    }

    @Test
    fun toggleAutoReenableOffWithSyncWhileLockAlreadyOffDoesNotDuplicateLockOffMessage() {
        autoReenableEnabled = true

        manager.toggleAutoReenableForTesting(syncGestureLock = true)

        assertFalse(autoReenableEnabled)
        assertFalse(manager.isLocked())
        assertNull(manager.getLockedGestureData())
        assertEquals(listOf(R.string.gesture_lock_rearm_disabled), messages)
    }

    @Test
    fun toggleAutoReenableOnWithoutSyncLeavesGestureLockUnchanged() {
        autoReenableEnabled = false

        manager.toggleAutoReenableForTesting(syncGestureLock = false)

        assertTrue(autoReenableEnabled)
        assertFalse(manager.isLocked())
        assertNull(manager.getLockedGestureData())
        assertEquals(listOf(R.string.gesture_lock_rearm_enabled), messages)
    }

    @Test
    fun toggleAutoReenableOffWithoutSyncLeavesGestureLockUnchanged() {
        autoReenableEnabled = true
        manager.toggleGestureLock()
        messages.clear()

        manager.toggleAutoReenableForTesting(syncGestureLock = false)

        assertFalse(autoReenableEnabled)
        assertTrue(manager.isLocked())
        assertNull(manager.getLockedGestureData())
        assertEquals(listOf(R.string.gesture_lock_rearm_disabled), messages)
    }

    @Test
    fun clearingLockStopsActiveRepeat() {
        autoRepeatEnabled = true
        lockWithGesture()
        assertTrue(repeatManager.isRepeating())

        manager.setLockedGestureData(null)

        assertFalse(repeatManager.isRepeating())
        assertFalse(manager.isGestureLockEngaged())
        assertNull(manager.getLockedGestureData())
    }

    @Test
    fun timeoutDoesNotInterruptActiveRepeat() {
        autoRepeatEnabled = true
        lockWithGesture()
        assertTrue(repeatManager.isRepeating())

        manager.handleLockTimeoutForTesting()

        assertTrue(repeatManager.isRepeating())
        assertTrue(manager.isLocked())
        assertTrue(manager.isGestureLockEngaged())
        assertFalse(messages.contains(R.string.gesture_lock_timeout_disabled))
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
