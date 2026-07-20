package com.enaboapps.switchify.service.gestures.execution

import com.enaboapps.switchify.service.gestures.data.GestureData
import org.junit.Assert.assertEquals
import org.junit.Test

class HoldAndDragTimingTest {
    @Test
    fun holdDurationUsesOneSecondMinimum() {
        assertEquals(1000L, HoldAndDragTiming.holdDuration(500L))
    }

    @Test
    fun totalDurationIncludesDragMovement() {
        assertEquals(
            1000L + GestureData.DRAG_DURATION,
            HoldAndDragTiming.totalDuration(500L)
        )
    }

    @Test
    fun longerSystemTimeoutKeepsRecognitionBuffer() {
        assertEquals(1600L, HoldAndDragTiming.holdDuration(1500L))
    }
}
