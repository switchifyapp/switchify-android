package com.enaboapps.switchify.service.gestures.execution

import com.enaboapps.switchify.service.gestures.data.GestureType
import org.junit.Assert.assertEquals
import org.junit.Test

class GestureExecutionPolicyTest {
    @Test
    fun holdAndDragAlwaysUsesOneFinger() {
        assertEquals(1, GestureExecutionPolicy.fingerCount(GestureType.HOLD_AND_DRAG, 5))
    }

    @Test
    fun otherGesturesKeepValidRequestedFingerCount() {
        assertEquals(4, GestureExecutionPolicy.fingerCount(GestureType.DRAG, 4))
    }
}
