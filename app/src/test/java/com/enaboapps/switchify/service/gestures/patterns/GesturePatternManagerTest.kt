package com.enaboapps.switchify.service.gestures.patterns

import android.graphics.PointF
import com.enaboapps.switchify.service.gestures.GestureRepeatManager
import com.enaboapps.switchify.service.gestures.data.GestureData
import com.enaboapps.switchify.service.gestures.data.GestureType
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import sun.misc.Unsafe

class GesturePatternManagerTest {
    private val repeatManager = GestureRepeatManager.instance
    private val messages = mutableListOf<Int>()

    @Before
    fun setup() {
        repeatManager.resetForTesting()
        repeatManager.setSuppressHudForTesting(true)
        repeatManager.setMessageRecorderForTesting { messages.add(it) }
        messages.clear()
        GesturePatternManager.resetForTesting()
    }

    @After
    fun tearDown() {
        repeatManager.resetForTesting()
        GesturePatternManager.resetForTesting()
    }

    @Test
    fun registerExecutorTurnsGestureRepeatOffWhenWaiting() {
        repeatManager.setAutoRepeatEnabledForTesting(true)
        val executor = testExecutor()

        GesturePatternManager.registerExecutor(executor)

        assertFalse(repeatManager.isAutoRepeatEnabled())
        assertFalse(repeatManager.isWaitingForGesture())
        assertEquals(emptyList<Int>(), messages)
    }

    @Test
    fun registerExecutorStopsActiveGestureRepeat() {
        repeatManager.setAutoRepeatEnabledForTesting(true)
        repeatManager.onGesturePerformed(testGesture())
        messages.clear()
        val executor = testExecutor()

        GesturePatternManager.registerExecutor(executor)

        assertFalse(repeatManager.isAutoRepeatEnabled())
        assertFalse(repeatManager.isRepeatSessionActive())
        assertFalse(repeatManager.isRepeating())
        assertNull(repeatManager.getRepeatedGestureDataForTesting())
        assertEquals(emptyList<Int>(), messages)
    }

    @Test
    fun unregisterExecutorDoesNotRestoreGestureRepeat() {
        repeatManager.setAutoRepeatEnabledForTesting(true)
        val executor = testExecutor()
        GesturePatternManager.registerExecutor(executor)

        GesturePatternManager.unregisterExecutor(executor)

        assertFalse(repeatManager.isAutoRepeatEnabled())
    }

    @Test
    fun registerExecutorKeepsPatternActive() {
        val executor = testExecutor()

        GesturePatternManager.registerExecutor(executor)

        assertTrue(GesturePatternManager.isGesturePatternActive())

        GesturePatternManager.unregisterExecutor(executor)

        assertFalse(GesturePatternManager.isGesturePatternActive())
    }

    private fun testExecutor(): GesturePatternExecutor {
        val unsafeField = Unsafe::class.java.getDeclaredField("theUnsafe")
        unsafeField.isAccessible = true
        val unsafe = unsafeField.get(null) as Unsafe
        return unsafe.allocateInstance(GesturePatternExecutor::class.java) as GesturePatternExecutor
    }

    private fun testGesture(): GestureData {
        return GestureData(
            gestureType = GestureType.TAP,
            startPoint = PointF(10f, 20f)
        )
    }
}
