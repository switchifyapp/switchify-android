package com.enaboapps.switchify.service.gestures.execution

import android.graphics.PathMeasure
import android.graphics.PointF
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.enaboapps.switchify.service.gestures.data.GestureData
import com.enaboapps.switchify.service.gestures.data.GestureType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HoldAndDragGesturePathTest {
    @Test
    fun continuationPreservesPointerAndStartsAtHoldPoint() {
        val start = PointF(100f, 200f)
        val end = PointF(400f, 500f)
        val sequence = GesturePathBuilder.createHoldAndDragPath(
            startPoint = start,
            endPoint = end,
            holdDuration = 600L,
            dragDuration = 1500L
        )

        val holdStroke = sequence.initial.getStroke(0)
        val dragStroke = sequence.continuation.getStroke(0)
        val dragStartPosition = FloatArray(2)
        val dragEndPosition = FloatArray(2)
        val holdPoints = holdStroke.path.approximate(0.5f)
        val dragMeasure = PathMeasure(dragStroke.path, false)
        dragMeasure.getPosTan(0f, dragStartPosition, null)
        dragMeasure.getPosTan(dragMeasure.length, dragEndPosition, null)

        assertTrue(holdStroke.willContinue())
        assertFalse(dragStroke.willContinue())
        assertEquals(start.x, holdPoints[holdPoints.size - 2], 0.01f)
        assertEquals(start.y, holdPoints[holdPoints.size - 1], 0.01f)
        assertEquals(start.x, dragStartPosition[0], 0.01f)
        assertEquals(start.y, dragStartPosition[1], 0.01f)
        assertEquals(end.x, dragEndPosition[0], 0.01f)
        assertEquals(end.y, dragEndPosition[1], 0.01f)
        assertEquals(
            HoldAndDragTiming.systemHoldDuration() + GestureData.DRAG_DURATION,
            GestureData(GestureType.HOLD_AND_DRAG, start, end).duration()
        )
    }
}
