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
        GestureModePolicy.resetForTesting()
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
        GestureModePolicy.setPreferenceAccessorsForTesting(
            repeatProvider = { autoRepeatEnabled },
            rearmProvider = { autoReenableEnabled },
            repeatSetter = { autoRepeatEnabled = it },
            rearmSetter = { autoReenableEnabled = it }
        )
    }

    @After
    fun tearDown() {
        manager.resetForTesting()
        repeatManager.resetForTesting()
        GestureModePolicy.resetForTesting()
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
    fun clearingLockDoesNotAffectRepeatCaptureState() {
        autoRepeatEnabled = true
        repeatManager.onGesturePerformed(testGesture())
        assertTrue(repeatManager.isRepeating())
        autoRepeatEnabled = false
        lockWithGesture()

        manager.setLockedGestureData(null)

        assertTrue(repeatManager.isRepeating())
        assertFalse(manager.isGestureLockEngaged())
        assertNull(manager.getLockedGestureData())
    }

    @Test
    fun timeoutDoesNotAffectActiveRepeatCaptureState() {
        autoRepeatEnabled = true
        repeatManager.onGesturePerformed(testGesture())
        assertTrue(repeatManager.isRepeating())
        autoRepeatEnabled = false
        lockWithGesture()

        manager.handleLockTimeoutForTesting()

        assertTrue(repeatManager.isRepeating())
        assertFalse(manager.isLocked())
        assertFalse(manager.isGestureLockEngaged())
        assertTrue(messages.contains(R.string.gesture_lock_timeout_disabled))
    }

    @Test
    fun turningRearmOnDisablesRepeat() {
        autoRepeatEnabled = true
        repeatManager.onGesturePerformed(testGesture())

        manager.toggleAutoReenableForTesting(syncGestureLock = false)

        assertTrue(autoReenableEnabled)
        assertFalse(autoRepeatEnabled)
        assertFalse(repeatManager.isRepeating())
    }

    @Test
    fun manualToggleGestureLockTurnsRepeatOffFirst() {
        autoRepeatEnabled = true
        repeatManager.toggleAutoRepeatForTesting(syncGestureLock = false)
        messages.clear()

        manager.toggleGestureLock()

        assertFalse(autoRepeatEnabled)
        assertFalse(repeatManager.isRepeating())
        assertTrue(manager.isLocked())
        assertFalse(manager.isGestureLockEngaged())
        assertEquals(listOf(R.string.gesture_lock_enabled), messages)
    }

    @Test
    fun manualToggleGestureLockStopsActiveRepeatAndTurnsRepeatOff() {
        autoRepeatEnabled = true
        repeatManager.onGesturePerformed(testGesture())
        messages.clear()

        manager.toggleGestureLock()

        assertFalse(autoRepeatEnabled)
        assertFalse(repeatManager.isRepeating())
        assertTrue(manager.isLocked())
        assertFalse(manager.isGestureLockEngaged())
        assertEquals(listOf(R.string.gesture_lock_enabled), messages)
    }

    @Test
    fun autoReenableDoesNotRunWhenRepeatIsEnabledBecauseModesAreExclusive() {
        autoRepeatEnabled = true
        autoReenableEnabled = true

        GestureModePolicy.normalizeForTesting()
        lockWithGesture()
        manager.disableLock()

        assertFalse(autoRepeatEnabled)
        assertFalse(autoReenableEnabled)
        assertFalse(manager.isLocked())
        assertFalse(messages.contains(R.string.gesture_lock_rearmed))
    }

    @Test
    fun bothPreferencesOnNormalizeToBothOff() {
        autoRepeatEnabled = true
        autoReenableEnabled = true

        val state = GestureModePolicy.normalizeForTesting()

        assertFalse(state.repeatEnabled)
        assertFalse(state.rearmEnabled)
        assertFalse(autoRepeatEnabled)
        assertFalse(autoReenableEnabled)
    }

    private fun lockWithGesture() {
        manager.toggleGestureLock()
        manager.setLockedGestureData(
            testGesture()
        )
        assertTrue(manager.isGestureLockEngaged())
    }

    private fun testGesture(): GestureData {
        return GestureData(
            gestureType = GestureType.TAP,
            startPoint = PointF(10f, 20f)
        )
    }
}
