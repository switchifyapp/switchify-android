package com.enaboapps.switchify.service.gestures

import android.graphics.PointF
import com.enaboapps.switchify.service.gestures.data.GestureData
import com.enaboapps.switchify.service.gestures.data.GestureType
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GestureLockManagerTest {
    private val manager = GestureLockManager.instance

    @Before
    fun setup() {
        manager.resetForTesting()
        manager.setSuppressHudForTesting(true)
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
    }

    @Test
    fun settingOnDisablingWithCapturedGestureRearmsLockAndClearsGesture() {
        manager.setAutoReenableProviderForTesting { true }
        lockWithGesture()

        manager.disableLock()

        assertTrue(manager.isLocked())
        assertFalse(manager.isGestureLockEngaged())
        assertNull(manager.getLockedGestureData())
    }

    @Test
    fun settingOnDisablingBeforeGestureCapturedLeavesLockOff() {
        manager.setAutoReenableProviderForTesting { true }
        manager.toggleGestureLock()

        manager.disableLock()

        assertFalse(manager.isLocked())
        assertFalse(manager.isGestureLockEngaged())
        assertNull(manager.getLockedGestureData())
    }

    @Test
    fun manualToggleOffWithCapturedGestureRearmsWhenSettingIsOn() {
        manager.setAutoReenableProviderForTesting { true }
        lockWithGesture()

        manager.toggleGestureLock()

        assertTrue(manager.isLocked())
        assertFalse(manager.isGestureLockEngaged())
        assertNull(manager.getLockedGestureData())
    }

    @Test
    fun timeoutWithCapturedGestureRearmsWhenSettingIsOn() {
        manager.setAutoReenableProviderForTesting { true }
        lockWithGesture()

        manager.handleLockTimeoutForTesting()

        assertTrue(manager.isLocked())
        assertFalse(manager.isGestureLockEngaged())
        assertNull(manager.getLockedGestureData())
    }

    @Test
    fun timeoutWithoutCapturedGestureLeavesLockOff() {
        manager.setAutoReenableProviderForTesting { true }
        manager.toggleGestureLock()

        manager.handleLockTimeoutForTesting()

        assertFalse(manager.isLocked())
        assertFalse(manager.isGestureLockEngaged())
        assertNull(manager.getLockedGestureData())
    }

    @Test
    fun disableWithoutAutoReenableClearsEvenWhenSettingIsOn() {
        manager.setAutoReenableProviderForTesting { true }
        lockWithGesture()

        manager.disableLock(allowAutoReenable = false)

        assertFalse(manager.isLocked())
        assertFalse(manager.isGestureLockEngaged())
        assertNull(manager.getLockedGestureData())
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
