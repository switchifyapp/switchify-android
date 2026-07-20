package com.enaboapps.switchify.service.gestures.execution

import com.enaboapps.switchify.service.gestures.data.GestureData
import org.junit.Assert.assertEquals
import org.junit.Test

class HoldAndDragTimingTest {
    @Test
    fun holdDurationAddsRecognitionBuffer() {
        assertEquals(600L, HoldAndDragTiming.holdDuration(500L))
    }

    @Test
    fun totalDurationIncludesDragMovement() {
        assertEquals(
            600L + GestureData.DRAG_DURATION,
            HoldAndDragTiming.totalDuration(500L)
        )
    }

    @Test
    fun negativeTimeoutUsesRecognitionBufferOnly() {
        assertEquals(100L, HoldAndDragTiming.holdDuration(-1L))
    }
}
