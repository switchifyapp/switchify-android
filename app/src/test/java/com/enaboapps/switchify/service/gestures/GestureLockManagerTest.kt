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

    @Before
    fun setup() {
        manager.resetForTesting()
        repeatManager.resetForTesting()
        manager.setSuppressHudForTesting(true)
        repeatManager.setSuppressHudForTesting(true)
        repeatManager.setRepeatDelayProviderForTesting { GestureRepeatManager.DEFAULT_REPEAT_DELAY }
        manager.setMessageRecorderForTesting { messages.add(it) }
        repeatManager.setMessageRecorderForTesting { messages.add(it) }
        messages.clear()
    }

    @After
    fun tearDown() {
        manager.resetForTesting()
        repeatManager.resetForTesting()
    }

    @Test
    fun rearmDefaultsOffAfterInit() {
        assertFalse(manager.isAutoReenableEnabled())
    }

    @Test
    fun settingOffDisablingWithCapturedGestureTurnsLockOffAndClearsGesture() {
        manager.setAutoReenableEnabledForTesting(false)
        lockWithGesture()

        manager.disableLock()

        assertFalse(manager.isLocked())
        assertNull(manager.getLockedGestureData())
        assertFalse(messages.contains(R.string.gesture_lock_rearmed))
    }

    @Test
    fun settingOnDisablingWithCapturedGestureRearmsLockAndClearsGesture() {
        manager.setAutoReenableEnabledForTesting(true)
        lockWithGesture()

        manager.disableLock()

        assertTrue(manager.isLocked())
        assertFalse(manager.isGestureLockEngaged())
        assertNull(manager.getLockedGestureData())
        assertTrue(messages.contains(R.string.gesture_lock_rearmed))
    }

    @Test
    fun settingOnDisablingBeforeGestureCapturedLeavesLockOff() {
        manager.setAutoReenableEnabledForTesting(true)
        manager.enableLockForNextGesture(showMessage = true)

        manager.disableLock()

        assertFalse(manager.isLocked())
        assertFalse(manager.isGestureLockEngaged())
        assertNull(manager.getLockedGestureData())
        assertEquals(listOf(R.string.gesture_lock_enabled), messages)
    }

    @Test
    fun manualToggleOffWithCapturedGestureRearmsWhenSettingIsOn() {
        manager.setAutoReenableEnabledForTesting(true)
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
        manager.setAutoReenableEnabledForTesting(true)
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
        manager.setAutoReenableEnabledForTesting(true)
        manager.enableLockForNextGesture(showMessage = true)

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
        manager.setAutoReenableEnabledForTesting(true)
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
        manager.toggleAutoReenableForTesting(syncGestureLock = true)

        assertTrue(manager.isAutoReenableEnabled())
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
        manager.setAutoReenableEnabledForTesting(true)
        manager.enableLockForNextGesture(showMessage = true)
        messages.clear()

        manager.toggleAutoReenableForTesting(syncGestureLock = true)

        assertFalse(manager.isAutoReenableEnabled())
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
        manager.setAutoReenableEnabledForTesting(true)

        manager.toggleAutoReenableForTesting(syncGestureLock = true)

        assertFalse(manager.isAutoReenableEnabled())
        assertFalse(manager.isLocked())
        assertNull(manager.getLockedGestureData())
        assertEquals(listOf(R.string.gesture_lock_rearm_disabled), messages)
    }

    @Test
    fun toggleAutoReenableOnWithoutSyncLeavesGestureLockUnchanged() {
        manager.toggleAutoReenableForTesting(syncGestureLock = false)

        assertTrue(manager.isAutoReenableEnabled())
        assertFalse(manager.isLocked())
        assertNull(manager.getLockedGestureData())
        assertEquals(listOf(R.string.gesture_lock_rearm_enabled), messages)
    }

    @Test
    fun toggleAutoReenableOffWithoutSyncLeavesGestureLockUnchanged() {
        manager.setAutoReenableEnabledForTesting(true)
        manager.enableLockForNextGesture(showMessage = true)
        messages.clear()

        manager.toggleAutoReenableForTesting(syncGestureLock = false)

        assertFalse(manager.isAutoReenableEnabled())
        assertTrue(manager.isLocked())
        assertNull(manager.getLockedGestureData())
        assertEquals(listOf(R.string.gesture_lock_rearm_disabled), messages)
    }

    @Test
    fun clearingLockDoesNotAffectRepeatCaptureState() {
        repeatManager.setAutoRepeatEnabledForTesting(true)
        repeatManager.onGesturePerformed(testGesture())
        assertTrue(repeatManager.isRepeating())
        repeatManager.setAutoRepeatEnabledForTesting(false)
        lockWithGesture()

        manager.setLockedGestureData(null)

        assertTrue(repeatManager.isRepeating())
        assertFalse(manager.isGestureLockEngaged())
        assertNull(manager.getLockedGestureData())
    }

    @Test
    fun timeoutDoesNotAffectActiveRepeatCaptureState() {
        repeatManager.setAutoRepeatEnabledForTesting(true)
        repeatManager.onGesturePerformed(testGesture())
        assertTrue(repeatManager.isRepeating())
        repeatManager.setAutoRepeatEnabledForTesting(false)
        lockWithGesture()

        manager.handleLockTimeoutForTesting()

        assertTrue(repeatManager.isRepeating())
        assertFalse(manager.isLocked())
        assertFalse(manager.isGestureLockEngaged())
        assertTrue(messages.contains(R.string.gesture_lock_timeout_disabled))
    }

    @Test
    fun turningRearmOnBlockedWhenRepeatEnabled() {
        repeatManager.setAutoRepeatEnabledForTesting(true)
        repeatManager.onGesturePerformed(testGesture())
        messages.clear()

        manager.toggleAutoReenableForTesting(syncGestureLock = false)

        assertFalse(manager.isAutoReenableEnabled())
        assertTrue(repeatManager.isAutoRepeatEnabled())
        assertTrue(repeatManager.isRepeating())
        assertEquals(listOf(R.string.gesture_mode_blocked_repeat_enabled_for_rearm), messages)
    }

    @Test
    fun toggleGestureLockOnBlockedWhenRepeatEnabled() {
        repeatManager.setAutoRepeatEnabledForTesting(true)
        messages.clear()

        manager.toggleGestureLock()

        assertTrue(repeatManager.isAutoRepeatEnabled())
        assertFalse(repeatManager.isRepeating())
        assertTrue(repeatManager.isWaitingForGesture())
        assertFalse(manager.isLocked())
        assertFalse(manager.isGestureLockEngaged())
        assertEquals(listOf(R.string.gesture_mode_blocked_repeat_enabled), messages)
    }

    @Test
    fun toggleGestureLockOnBlockedWhenRepeatIsRunning() {
        repeatManager.setAutoRepeatEnabledForTesting(true)
        repeatManager.onGesturePerformed(testGesture())
        messages.clear()

        manager.toggleGestureLock()

        assertTrue(repeatManager.isAutoRepeatEnabled())
        assertTrue(repeatManager.isRepeating())
        assertFalse(manager.isLocked())
        assertFalse(manager.isGestureLockEngaged())
        assertEquals(listOf(R.string.gesture_mode_blocked_repeat_enabled), messages)
    }

    @Test
    fun toggleGestureLockOnBlockedWhenRearmEnabled() {
        manager.setAutoReenableEnabledForTesting(true)

        manager.toggleGestureLock()

        assertTrue(manager.isAutoReenableEnabled())
        assertFalse(manager.isLocked())
        assertEquals(listOf(R.string.gesture_mode_blocked_rearm_enabled_for_lock), messages)
    }

    @Test
    fun toggleGestureLockOffAllowedEvenIfRuntimeStateInvalid() {
        manager.toggleGestureLock()
        repeatManager.setAutoRepeatEnabledForTesting(true)
        messages.clear()

        manager.toggleGestureLock()

        assertFalse(manager.isLocked())
        assertEquals(listOf(R.string.gesture_lock_disabled), messages)
    }

    @Test
    fun turningRearmOnBlockedWhenGestureLockEnabled() {
        manager.toggleGestureLock()
        messages.clear()

        manager.toggleAutoReenableForTesting(syncGestureLock = false)

        assertFalse(manager.isAutoReenableEnabled())
        assertTrue(manager.isLocked())
        assertEquals(listOf(R.string.gesture_mode_blocked_lock_enabled_for_rearm), messages)
    }

    @Test
    fun clearServiceStateDisablesRearmAndLock() {
        manager.setAutoReenableEnabledForTesting(true)
        manager.enableLockForNextGesture(showMessage = false)

        manager.clearServiceState()

        assertFalse(manager.isAutoReenableEnabled())
        assertFalse(manager.isLocked())
        assertFalse(manager.isGestureLockEngaged())
        assertNull(manager.getLockedGestureData())
    }

    @Test
    fun bothRuntimeModesOnNormalizeToBothOff() {
        val state = GestureModePolicy.currentState(
            repeatEnabled = true,
            rearmEnabled = true
        )

        assertFalse(state.repeatEnabled)
        assertFalse(state.rearmEnabled)
    }

    private fun lockWithGesture() {
        manager.enableLockForNextGesture(showMessage = true)
        manager.setLockedGestureData(testGesture())
        assertTrue(manager.isGestureLockEngaged())
    }

    private fun testGesture(): GestureData {
        return GestureData(
            gestureType = GestureType.TAP,
            startPoint = PointF(10f, 20f)
        )
    }
}
