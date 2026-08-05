package com.enaboapps.switchify.service.face

import com.enaboapps.switchify.switches.CameraSwitchFacialGesture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FacialGestureRegistryTest {
    @Test
    fun allCameraGesturesAreAssignableAsSwitches() {
        val expected = setOf(
            CameraSwitchFacialGesture.SMILE,
            CameraSwitchFacialGesture.LEFT_WINK,
            CameraSwitchFacialGesture.RIGHT_WINK,
            CameraSwitchFacialGesture.BLINK,
            CameraSwitchFacialGesture.PUCKER,
            CameraSwitchFacialGesture.HEAD_TURN_LEFT,
            CameraSwitchFacialGesture.HEAD_TURN_RIGHT,
            CameraSwitchFacialGesture.HEAD_TURN_UP,
            CameraSwitchFacialGesture.HEAD_TURN_DOWN
        )

        assertEquals(expected, FacialGestureRegistry.switchAssignableIds().toSet())
    }

    @Test
    fun headTurnsRemainAssignableCameraGestures() {
        listOf(
            CameraSwitchFacialGesture.HEAD_TURN_LEFT,
            CameraSwitchFacialGesture.HEAD_TURN_RIGHT,
            CameraSwitchFacialGesture.HEAD_TURN_UP,
            CameraSwitchFacialGesture.HEAD_TURN_DOWN
        ).forEach { gesture ->
            assertTrue(FacialGestureRegistry.isHeadTurn(gesture))
            assertTrue(FacialGestureRegistry.isAssignableAsSwitch(gesture))
        }
    }
}
