package com.enaboapps.switchify.switches

import android.view.InputDevice
import android.view.MotionEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

class JoystickDirectionDetector(
    private val pressThreshold: Float = DEFAULT_PRESS_THRESHOLD,
    private val releaseThreshold: Float = DEFAULT_RELEASE_THRESHOLD
) {
    data class Edge(
        val direction: JoystickDirection,
        val pressed: Boolean,
        val downTimeMs: Long,
        val eventTimeMs: Long
    )

    private data class HeldDirection(val deviceId: Int, val direction: JoystickDirection)

    private val downTimes = mutableMapOf<HeldDirection, Long>()

    fun process(event: MotionEvent): List<Edge> {
        if (!isJoystickEvent(event) || event.actionMasked != MotionEvent.ACTION_MOVE) {
            return emptyList()
        }
        return process(event.deviceId, event.eventTime) { axis -> event.getAxisValue(axis) }
    }

    fun process(deviceId: Int, eventTimeMs: Long, axisValue: (Int) -> Float): List<Edge> {
        val releases = mutableListOf<Edge>()
        val presses = mutableListOf<Edge>()
        JoystickDirection.entries.forEach { direction ->
            val held = HeldDirection(deviceId, direction)
            val value = direction.valueFrom(axisValue)
            val downTime = downTimes[held]
            if (downTime == null && value >= pressThreshold) {
                downTimes[held] = eventTimeMs
                presses += Edge(direction, true, eventTimeMs, eventTimeMs)
            } else if (downTime != null && value < releaseThreshold) {
                downTimes.remove(held)
                releases += Edge(direction, false, downTime, eventTimeMs)
            }
        }
        return releases + presses
    }

    fun releaseAll(eventTimeMs: Long): List<Edge> {
        val releases = downTimes.map { (held, downTime) ->
            Edge(held.direction, false, downTime, eventTimeMs)
        }
        downTimes.clear()
        return releases
    }

    companion object {
        const val DEFAULT_PRESS_THRESHOLD = 0.5f
        const val DEFAULT_RELEASE_THRESHOLD = 0.3f

        fun isJoystickEvent(event: MotionEvent): Boolean =
            event.isFromSource(InputDevice.SOURCE_JOYSTICK)
    }
}

object JoystickSwitchCapture {
    private val captureCount = MutableStateFlow(0)

    val activeCaptures: StateFlow<Int> = captureCount

    fun begin() = captureCount.update { it + 1 }

    fun end() = captureCount.update { (it - 1).coerceAtLeast(0) }
}
