package com.enaboapps.switchify.switches

import android.view.MotionEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JoystickDirectionDetectorTest {

    private fun axes(vararg values: Pair<Int, Float>): (Int) -> Float {
        val map = values.toMap()
        return { axis -> map[axis] ?: 0f }
    }

    @Test
    fun pressesOnceWhenStickPassesThreshold() {
        val detector = JoystickDirectionDetector()

        val first = detector.process(1, 10, axes(MotionEvent.AXIS_Y to -0.6f))
        val second = detector.process(1, 20, axes(MotionEvent.AXIS_Y to -0.9f))

        assertEquals(
            listOf(JoystickDirectionDetector.Edge(JoystickDirection.LEFT_STICK_UP, true, 10, 10)),
            first
        )
        assertTrue(second.isEmpty())
    }

    @Test
    fun releasesOnlyBelowReleaseThreshold() {
        val detector = JoystickDirectionDetector()
        detector.process(1, 10, axes(MotionEvent.AXIS_X to 1f))

        val stillHeld = detector.process(1, 20, axes(MotionEvent.AXIS_X to 0.4f))
        val released = detector.process(1, 30, axes(MotionEvent.AXIS_X to 0.1f))

        assertTrue(stillHeld.isEmpty())
        assertEquals(
            listOf(JoystickDirectionDetector.Edge(JoystickDirection.LEFT_STICK_RIGHT, false, 10, 30)),
            released
        )
    }

    @Test
    fun releasesBeforePressingWhenHatFlipsDirection() {
        val detector = JoystickDirectionDetector()
        detector.process(1, 10, axes(MotionEvent.AXIS_HAT_X to -1f))

        val edges = detector.process(1, 20, axes(MotionEvent.AXIS_HAT_X to 1f))

        assertEquals(
            listOf(
                JoystickDirectionDetector.Edge(JoystickDirection.HAT_LEFT, false, 10, 20),
                JoystickDirectionDetector.Edge(JoystickDirection.HAT_RIGHT, true, 20, 20)
            ),
            edges
        )
    }

    @Test
    fun readsTriggerFromBrakeAxis() {
        val detector = JoystickDirectionDetector()

        val edges = detector.process(1, 10, axes(MotionEvent.AXIS_BRAKE to 0.8f))

        assertEquals(listOf(JoystickDirection.LEFT_TRIGGER), edges.map { it.direction })
    }

    @Test
    fun tracksDevicesSeparately() {
        val detector = JoystickDirectionDetector()
        detector.process(1, 10, axes(MotionEvent.AXIS_Y to 1f))

        val otherDevice = detector.process(2, 20, axes(MotionEvent.AXIS_Y to 1f))

        assertEquals(listOf(JoystickDirection.LEFT_STICK_DOWN), otherDevice.map { it.direction })
    }

    @Test
    fun releaseAllReleasesHeldDirections() {
        val detector = JoystickDirectionDetector()
        detector.process(1, 10, axes(MotionEvent.AXIS_RTRIGGER to 1f))

        val released = detector.releaseAll(50)
        val afterwards = detector.releaseAll(60)

        assertEquals(
            listOf(JoystickDirectionDetector.Edge(JoystickDirection.RIGHT_TRIGGER, false, 10, 50)),
            released
        )
        assertTrue(afterwards.isEmpty())
    }

    @Test
    fun directionCodesAreUniqueAndOutsideKeyCodeRange() {
        val codes = JoystickDirection.entries.map { it.code }
        assertEquals(codes.size, codes.toSet().size)
        assertTrue(codes.all { it > 100_000 })
        assertEquals(JoystickDirection.HAT_UP, JoystickDirection.fromCode("100009"))
        assertEquals("D-pad Up", SwitchNameGenerator.forKeyCode(100_009))
    }
}
