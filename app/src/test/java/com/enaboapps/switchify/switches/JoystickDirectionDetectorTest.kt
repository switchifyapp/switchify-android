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

    private fun JoystickDirectionDetector.centre(deviceId: Int = 1, eventTimeMs: Long = 0) =
        process(deviceId, eventTimeMs, axisValue = axes())

    private fun JoystickDirectionDetector.move(
        eventTimeMs: Long,
        vararg values: Pair<Int, Float>,
        deviceId: Int = 1
    ) = process(deviceId, eventTimeMs, axisValue = axes(*values))

    @Test
    fun pressesOnceWhenStickPassesThreshold() {
        val detector = JoystickDirectionDetector()
        detector.centre()

        val first = detector.move(10, MotionEvent.AXIS_Y to -0.6f)
        val second = detector.move(20, MotionEvent.AXIS_Y to -0.9f)

        assertEquals(
            listOf(JoystickDirectionDetector.Edge(JoystickDirection.LEFT_STICK_UP, true, 10, 10)),
            first
        )
        assertTrue(second.isEmpty())
    }

    @Test
    fun ignoresStickAlreadyPushedWhenFirstSeen() {
        val detector = JoystickDirectionDetector()

        val pushed = detector.move(10, MotionEvent.AXIS_Z to -1f)
        val stillPushed = detector.move(20, MotionEvent.AXIS_Z to -0.9f)
        detector.move(30, MotionEvent.AXIS_Z to 0f)
        val pushedAgain = detector.move(40, MotionEvent.AXIS_Z to -1f)

        assertTrue(pushed.isEmpty())
        assertTrue(stillPushed.isEmpty())
        assertEquals(listOf(JoystickDirection.RIGHT_STICK_LEFT), pushedAgain.map { it.direction })
    }

    @Test
    fun pressesHatOnFirstEvent() {
        val detector = JoystickDirectionDetector()

        val edges = detector.move(10, MotionEvent.AXIS_HAT_Y to -1f)

        assertEquals(listOf(JoystickDirection.HAT_UP), edges.map { it.direction })
    }

    @Test
    fun releasesOnlyBelowReleaseThreshold() {
        val detector = JoystickDirectionDetector()
        detector.centre()
        detector.move(10, MotionEvent.AXIS_X to 1f)

        val stillHeld = detector.move(20, MotionEvent.AXIS_X to 0.4f)
        val released = detector.move(30, MotionEvent.AXIS_X to 0.1f)

        assertTrue(stillHeld.isEmpty())
        assertEquals(
            listOf(JoystickDirectionDetector.Edge(JoystickDirection.LEFT_STICK_RIGHT, false, 10, 30)),
            released
        )
    }

    @Test
    fun diagonalPushPressesNothing() {
        val detector = JoystickDirectionDetector()
        detector.centre()

        val edges = detector.move(10, MotionEvent.AXIS_X to 0.71f, MotionEvent.AXIS_Y to -0.71f)

        assertTrue(edges.isEmpty())
    }

    @Test
    fun mostlyStraightPushPressesStrongestDirectionOnly() {
        val detector = JoystickDirectionDetector()
        detector.centre()

        val edges = detector.move(10, MotionEvent.AXIS_X to 0.9f, MotionEvent.AXIS_Y to -0.4f)

        assertEquals(listOf(JoystickDirection.LEFT_STICK_RIGHT), edges.map { it.direction })
    }

    @Test
    fun keepsOneDirectionPerStickWhileRollingAround() {
        val detector = JoystickDirectionDetector()
        detector.centre()
        detector.move(10, MotionEvent.AXIS_X to -1f)

        val diagonal = detector.move(20, MotionEvent.AXIS_X to -0.71f, MotionEvent.AXIS_Y to -0.71f)
        val up = detector.move(30, MotionEvent.AXIS_X to -0.2f, MotionEvent.AXIS_Y to -0.98f)

        assertTrue(diagonal.isEmpty())
        assertEquals(
            listOf(
                JoystickDirectionDetector.Edge(JoystickDirection.LEFT_STICK_LEFT, false, 10, 30),
                JoystickDirectionDetector.Edge(JoystickDirection.LEFT_STICK_UP, true, 30, 30)
            ),
            up
        )
    }

    @Test
    fun releasesBeforePressingWhenHatFlipsDirection() {
        val detector = JoystickDirectionDetector()
        detector.move(10, MotionEvent.AXIS_HAT_X to -1f)

        val edges = detector.move(20, MotionEvent.AXIS_HAT_X to 1f)

        assertEquals(
            listOf(
                JoystickDirectionDetector.Edge(JoystickDirection.HAT_LEFT, false, 10, 20),
                JoystickDirectionDetector.Edge(JoystickDirection.HAT_RIGHT, true, 20, 20)
            ),
            edges
        )
    }

    @Test
    fun readsFallbackAxesWhenPrimaryIsMissing() {
        val detector = JoystickDirectionDetector()
        val reported = setOf(MotionEvent.AXIS_BRAKE, MotionEvent.AXIS_RX, MotionEvent.AXIS_RY)
        val hasAxis: (Int) -> Boolean = { it in reported }
        detector.process(1, 0, hasAxis, axes())

        val trigger = detector.process(1, 10, hasAxis, axes(MotionEvent.AXIS_BRAKE to 0.8f))
        val stick = detector.process(1, 20, hasAxis, axes(MotionEvent.AXIS_BRAKE to 0.8f, MotionEvent.AXIS_RY to 1f))

        assertEquals(listOf(JoystickDirection.LEFT_TRIGGER), trigger.map { it.direction })
        assertEquals(listOf(JoystickDirection.RIGHT_STICK_DOWN), stick.map { it.direction })
    }

    @Test
    fun ignoresFallbackAxesWhenPrimaryIsReported() {
        val detector = JoystickDirectionDetector()
        val reported = setOf(MotionEvent.AXIS_Z, MotionEvent.AXIS_RZ, MotionEvent.AXIS_RX, MotionEvent.AXIS_RY)
        val hasAxis: (Int) -> Boolean = { it in reported }
        val restingTriggers = arrayOf(MotionEvent.AXIS_RX to -1f, MotionEvent.AXIS_RY to -1f)
        detector.process(1, 0, hasAxis, axes(*restingTriggers))

        val edges = detector.process(1, 10, hasAxis, axes(*restingTriggers, MotionEvent.AXIS_Z to 1f))

        assertEquals(listOf(JoystickDirection.RIGHT_STICK_RIGHT), edges.map { it.direction })
    }

    @Test
    fun tracksDevicesSeparately() {
        val detector = JoystickDirectionDetector()
        detector.centre(deviceId = 1)
        detector.centre(deviceId = 2)
        detector.move(10, MotionEvent.AXIS_Y to 1f, deviceId = 1)

        val otherDevice = detector.move(20, MotionEvent.AXIS_Y to 1f, deviceId = 2)

        assertEquals(listOf(JoystickDirection.LEFT_STICK_DOWN), otherDevice.map { it.direction })
    }

    @Test
    fun releaseDeviceReleasesOnlyThatDevice() {
        val detector = JoystickDirectionDetector()
        detector.move(10, MotionEvent.AXIS_HAT_Y to 1f, deviceId = 1)
        detector.move(10, MotionEvent.AXIS_HAT_Y to 1f, deviceId = 2)

        val released = detector.releaseDevice(1, 50)
        val remaining = detector.releaseAll(60)

        assertEquals(
            listOf(JoystickDirectionDetector.Edge(JoystickDirection.HAT_DOWN, false, 10, 50)),
            released
        )
        assertEquals(1, remaining.size)
    }

    @Test
    fun releaseAllReleasesHeldDirections() {
        val detector = JoystickDirectionDetector()
        detector.move(10, MotionEvent.AXIS_RTRIGGER to 1f)

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
