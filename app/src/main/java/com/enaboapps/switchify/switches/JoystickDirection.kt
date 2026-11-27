package com.enaboapps.switchify.switches

import android.view.MotionEvent

enum class JoystickControl(val armsOnFirstEvent: Boolean) {
    LEFT_STICK(false),
    RIGHT_STICK(false),
    HAT(true),
    LEFT_TRIGGER(true),
    RIGHT_TRIGGER(true)
}

enum class JoystickDirection(
    val code: Int,
    val displayName: String,
    val control: JoystickControl,
    private val positive: Boolean,
    private vararg val axes: Int
) {
    LEFT_STICK_UP(100_001, "Left Stick Up", JoystickControl.LEFT_STICK, false, MotionEvent.AXIS_Y),
    LEFT_STICK_DOWN(100_002, "Left Stick Down", JoystickControl.LEFT_STICK, true, MotionEvent.AXIS_Y),
    LEFT_STICK_LEFT(100_003, "Left Stick Left", JoystickControl.LEFT_STICK, false, MotionEvent.AXIS_X),
    LEFT_STICK_RIGHT(100_004, "Left Stick Right", JoystickControl.LEFT_STICK, true, MotionEvent.AXIS_X),
    RIGHT_STICK_UP(100_005, "Right Stick Up", JoystickControl.RIGHT_STICK, false, MotionEvent.AXIS_RZ, MotionEvent.AXIS_RY),
    RIGHT_STICK_DOWN(100_006, "Right Stick Down", JoystickControl.RIGHT_STICK, true, MotionEvent.AXIS_RZ, MotionEvent.AXIS_RY),
    RIGHT_STICK_LEFT(100_007, "Right Stick Left", JoystickControl.RIGHT_STICK, false, MotionEvent.AXIS_Z, MotionEvent.AXIS_RX),
    RIGHT_STICK_RIGHT(100_008, "Right Stick Right", JoystickControl.RIGHT_STICK, true, MotionEvent.AXIS_Z, MotionEvent.AXIS_RX),
    HAT_UP(100_009, "D-pad Up", JoystickControl.HAT, false, MotionEvent.AXIS_HAT_Y),
    HAT_DOWN(100_010, "D-pad Down", JoystickControl.HAT, true, MotionEvent.AXIS_HAT_Y),
    HAT_LEFT(100_011, "D-pad Left", JoystickControl.HAT, false, MotionEvent.AXIS_HAT_X),
    HAT_RIGHT(100_012, "D-pad Right", JoystickControl.HAT, true, MotionEvent.AXIS_HAT_X),
    LEFT_TRIGGER(100_013, "Left Trigger", JoystickControl.LEFT_TRIGGER, true, MotionEvent.AXIS_LTRIGGER, MotionEvent.AXIS_BRAKE),
    RIGHT_TRIGGER(100_014, "Right Trigger", JoystickControl.RIGHT_TRIGGER, true, MotionEvent.AXIS_RTRIGGER, MotionEvent.AXIS_GAS);

    fun valueFrom(axisValue: (Int) -> Float, hasAxis: (Int) -> Boolean): Float {
        val axis = axes.firstOrNull(hasAxis) ?: return 0f
        return axisValue(axis).let { if (positive) it else -it }
    }

    companion object {
        private val byCode = entries.associateBy { it.code }

        fun fromCode(code: Int?): JoystickDirection? = code?.let { byCode[it] }

        fun fromCode(code: String?): JoystickDirection? = fromCode(code?.toIntOrNull())
    }
}
