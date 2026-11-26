package com.enaboapps.switchify.switches

import android.view.MotionEvent

enum class JoystickDirection(
    val code: Int,
    val displayName: String,
    private val positive: Boolean,
    private vararg val axes: Int
) {
    LEFT_STICK_UP(100_001, "Left Stick Up", false, MotionEvent.AXIS_Y),
    LEFT_STICK_DOWN(100_002, "Left Stick Down", true, MotionEvent.AXIS_Y),
    LEFT_STICK_LEFT(100_003, "Left Stick Left", false, MotionEvent.AXIS_X),
    LEFT_STICK_RIGHT(100_004, "Left Stick Right", true, MotionEvent.AXIS_X),
    RIGHT_STICK_UP(100_005, "Right Stick Up", false, MotionEvent.AXIS_RZ),
    RIGHT_STICK_DOWN(100_006, "Right Stick Down", true, MotionEvent.AXIS_RZ),
    RIGHT_STICK_LEFT(100_007, "Right Stick Left", false, MotionEvent.AXIS_Z),
    RIGHT_STICK_RIGHT(100_008, "Right Stick Right", true, MotionEvent.AXIS_Z),
    HAT_UP(100_009, "D-pad Up", false, MotionEvent.AXIS_HAT_Y),
    HAT_DOWN(100_010, "D-pad Down", true, MotionEvent.AXIS_HAT_Y),
    HAT_LEFT(100_011, "D-pad Left", false, MotionEvent.AXIS_HAT_X),
    HAT_RIGHT(100_012, "D-pad Right", true, MotionEvent.AXIS_HAT_X),
    LEFT_TRIGGER(100_013, "Left Trigger", true, MotionEvent.AXIS_LTRIGGER, MotionEvent.AXIS_BRAKE),
    RIGHT_TRIGGER(100_014, "Right Trigger", true, MotionEvent.AXIS_RTRIGGER, MotionEvent.AXIS_GAS);

    fun valueFrom(axisValue: (Int) -> Float): Float =
        axes.maxOf { axis -> axisValue(axis).let { if (positive) it else -it } }

    companion object {
        private val byCode = entries.associateBy { it.code }

        fun fromCode(code: Int?): JoystickDirection? = code?.let { byCode[it] }

        fun fromCode(code: String?): JoystickDirection? = fromCode(code?.toIntOrNull())
    }
}
