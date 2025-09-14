package com.enaboapps.switchify.service.face

import com.enaboapps.switchify.R
import com.enaboapps.switchify.switches.CameraSwitchFacialGesture
import com.enaboapps.switchify.utils.Resources

object FacialGestureRegistry {
    data class Meta(
        val id: String,
        val nameResId: Int?,
        val fallbackName: String,
        val isHeadTurn: Boolean,
        val assignableAsSwitch: Boolean
    )

    private val entries: Map<String, Meta> = listOf(
        Meta(
            id = CameraSwitchFacialGesture.SMILE,
            nameResId = R.string.head_control_gesture_smile,
            fallbackName = "Smile",
            isHeadTurn = false,
            assignableAsSwitch = true
        ),
        Meta(
            id = CameraSwitchFacialGesture.LEFT_WINK,
            nameResId = R.string.head_control_gesture_left_wink,
            fallbackName = "Left Wink",
            isHeadTurn = false,
            assignableAsSwitch = true
        ),
        Meta(
            id = CameraSwitchFacialGesture.RIGHT_WINK,
            nameResId = R.string.head_control_gesture_right_wink,
            fallbackName = "Right Wink",
            isHeadTurn = false,
            assignableAsSwitch = true
        ),
        Meta(
            id = CameraSwitchFacialGesture.BLINK,
            nameResId = R.string.head_control_gesture_blink,
            fallbackName = "Blink",
            isHeadTurn = false,
            assignableAsSwitch = true
        ),
        Meta(
            id = CameraSwitchFacialGesture.HEAD_TURN_LEFT,
            nameResId = null,
            fallbackName = "Head Turn Left",
            isHeadTurn = true,
            assignableAsSwitch = false
        ),
        Meta(
            id = CameraSwitchFacialGesture.HEAD_TURN_RIGHT,
            nameResId = null,
            fallbackName = "Head Turn Right",
            isHeadTurn = true,
            assignableAsSwitch = false
        ),
        Meta(
            id = CameraSwitchFacialGesture.HEAD_TURN_UP,
            nameResId = null,
            fallbackName = "Head Turn Up",
            isHeadTurn = true,
            assignableAsSwitch = false
        ),
        Meta(
            id = CameraSwitchFacialGesture.HEAD_TURN_DOWN,
            nameResId = null,
            fallbackName = "Head Turn Down",
            isHeadTurn = true,
            assignableAsSwitch = false
        )
    ).associateBy { it.id }

    fun getName(id: String): String {
        val meta = entries[id] ?: return ""
        return meta.nameResId?.let { Resources.getString(it) } ?: meta.fallbackName
    }

    fun isHeadTurn(id: String): Boolean = entries[id]?.isHeadTurn == true

    fun isAssignableAsSwitch(id: String): Boolean = entries[id]?.assignableAsSwitch == true

    fun allIds(): List<String> = entries.keys.toList()

    fun switchAssignableIds(): List<String> = entries.values.filter { it.assignableAsSwitch }.map { it.id }
}
