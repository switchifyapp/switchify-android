package com.enaboapps.switchify.service.gestures

import android.graphics.PointF
import com.enaboapps.switchify.service.gestures.data.GestureData
import com.enaboapps.switchify.service.gestures.data.GestureType
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GestureCaptureRouterTest {
    private val lockManager = GestureLockManager.instance
    private val repeatManager = GestureRepeatManager.instance
    private var autoRepeatEnabled = false
    private var autoReenableEnabled = false

    @Before
    fun setup() {
        GestureModePolicy.resetForTesting()
        lockManager.resetForTesting()
        repeatManager.resetForTesting()
        lockManager.setSuppressHudForTesting(true)
        repeatManager.setSuppressHudForTesting(true)
        autoRepeatEnabled = false
        autoReenableEnabled = false
        lockManager.setAutoReenableProviderForTesting { autoReenableEnabled }
        lockManager.setAutoReenableSetterForTesting { autoReenableEnabled = it }
        repeatManager.setAutoRepeatProviderForTesting { autoRepeatEnabled }
        repeatManager.setAutoRepeatSetterForTesting { autoRepeatEnabled = it }
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
    fun routerSendsGestureToLockAndRepeat() {
        lockManager.enableLockForNextGesture(showMessage = false)
        autoRepeatEnabled = true
        val gestureData = testGesture()

        GestureCaptureRouter.onGesturePerformed(gestureData)

        assertTrue(lockManager.isGestureLockEngaged())
        assertEquals(gestureData, lockManager.getLockedGestureData())
        assertTrue(repeatManager.isRepeating())
        assertEquals(gestureData, repeatManager.getRepeatedGestureDataForTesting())
    }

    @Test
    fun routerDoesNotRequireGestureLockForRepeat() {
        autoRepeatEnabled = true
        val gestureData = testGesture()

        GestureCaptureRouter.onGesturePerformed(gestureData)

        assertFalse(lockManager.isLocked())
        assertFalse(lockManager.isGestureLockEngaged())
        assertTrue(repeatManager.isRepeating())
        assertEquals(gestureData, repeatManager.getRepeatedGestureDataForTesting())
    }

    private fun testGesture(): GestureData {
        return GestureData(
            gestureType = GestureType.TAP,
            startPoint = PointF(10f, 20f)
        )
    }
}
