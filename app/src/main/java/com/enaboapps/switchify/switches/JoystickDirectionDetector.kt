package com.enaboapps.switchify.switches

import android.view.InputDevice
import android.view.MotionEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

class JoystickDirectionDetector(
    private val pressThreshold: Float = DEFAULT_PRESS_THRESHOLD,
    private val releaseThreshold: Float = DEFAULT_RELEASE_THRESHOLD,
    private val dominanceRatio: Float = DEFAULT_DOMINANCE_RATIO
) {
    data class Edge(
        val direction: JoystickDirection,
        val pressed: Boolean,
        val downTimeMs: Long,
        val eventTimeMs: Long
    )

    private data class ControlKey(val deviceId: Int, val control: JoystickControl)

    private data class DirectionKey(val deviceId: Int, val direction: JoystickDirection)

    private data class HeldDirection(val direction: JoystickDirection, val downTimeMs: Long)

    private val held = mutableMapOf<ControlKey, HeldDirection>()
    private val armed = mutableSetOf<DirectionKey>()
    private val seen = mutableSetOf<ControlKey>()

    fun process(event: MotionEvent): List<Edge> {
        if (!isJoystickEvent(event) || event.actionMasked != MotionEvent.ACTION_MOVE) {
            return emptyList()
        }
        val device = event.device
        return process(
            deviceId = event.deviceId,
            eventTimeMs = event.eventTime,
            hasAxis = { axis -> device == null || device.getMotionRange(axis, InputDevice.SOURCE_JOYSTICK) != null },
            axisValue = { axis -> event.getAxisValue(axis) }
        )
    }

    fun process(
        deviceId: Int,
        eventTimeMs: Long,
        hasAxis: (Int) -> Boolean = { true },
        axisValue: (Int) -> Float
    ): List<Edge> {
        val releases = mutableListOf<Edge>()
        val presses = mutableListOf<Edge>()
        directionsByControl.forEach { (control, directions) ->
            val controlKey = ControlKey(deviceId, control)
            val values = directions.associateWith { it.valueFrom(axisValue, hasAxis) }
            if (seen.add(controlKey) && control.armsOnFirstEvent) {
                directions.forEach { armed += DirectionKey(deviceId, it) }
            }
            values.forEach { (direction, value) ->
                if (value < releaseThreshold) armed += DirectionKey(deviceId, direction)
            }

            held[controlKey]?.let { current ->
                if (values.getValue(current.direction) < releaseThreshold) {
                    held.remove(controlKey)
                    releases += Edge(current.direction, false, current.downTimeMs, eventTimeMs)
                }
            }
            if (controlKey in held) return@forEach

            val ranked = values.entries.sortedByDescending { it.value }
            val strongest = ranked.first()
            val runnerUp = ranked.getOrNull(1)?.value?.coerceAtLeast(0f) ?: 0f
            val isDominant = strongest.value >= runnerUp * dominanceRatio
            if (strongest.value >= pressThreshold && isDominant &&
                DirectionKey(deviceId, strongest.key) in armed
            ) {
                armed -= DirectionKey(deviceId, strongest.key)
                held[controlKey] = HeldDirection(strongest.key, eventTimeMs)
                presses += Edge(strongest.key, true, eventTimeMs, eventTimeMs)
            }
        }
        return releases + presses
    }

    fun releaseDevice(deviceId: Int, eventTimeMs: Long): List<Edge> {
        armed.removeAll { it.deviceId == deviceId }
        seen.removeAll { it.deviceId == deviceId }
        val releasedKeys = held.keys.filter { it.deviceId == deviceId }
        return releasedKeys.mapNotNull { key ->
            held.remove(key)?.let { Edge(it.direction, false, it.downTimeMs, eventTimeMs) }
        }
    }

    fun releaseAll(eventTimeMs: Long): List<Edge> {
        val releases = held.values.map { Edge(it.direction, false, it.downTimeMs, eventTimeMs) }
        held.clear()
        armed.clear()
        seen.clear()
        return releases
    }

    companion object {
        const val DEFAULT_PRESS_THRESHOLD = 0.5f
        const val DEFAULT_RELEASE_THRESHOLD = 0.3f
        const val DEFAULT_DOMINANCE_RATIO = 1.5f

        private val directionsByControl = JoystickDirection.entries.groupBy { it.control }

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
