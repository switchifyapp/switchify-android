package com.enaboapps.switchify.service.face

import com.enaboapps.switchify.R
import com.enaboapps.switchify.switches.CameraSwitchFacialGesture
import com.enaboapps.switchify.utils.Resources

object FacialGestureRegistry {
    data class Meta(
        val id: String,
        val nameResId: Int,
        val isHeadTurn: Boolean,
        val assignableAsSwitch: Boolean
    )

    private val entries: Map<String, Meta> = listOf(
        Meta(
            id = CameraSwitchFacialGesture.SMILE,
            nameResId = R.string.head_control_gesture_smile,
            isHeadTurn = false,
            assignableAsSwitch = true
        ),
        Meta(
            id = CameraSwitchFacialGesture.LEFT_WINK,
            nameResId = R.string.head_control_gesture_left_wink,
            isHeadTurn = false,
            assignableAsSwitch = true
        ),
        Meta(
            id = CameraSwitchFacialGesture.RIGHT_WINK,
            nameResId = R.string.head_control_gesture_right_wink,
            isHeadTurn = false,
            assignableAsSwitch = true
        ),
        Meta(
            id = CameraSwitchFacialGesture.BLINK,
            nameResId = R.string.head_control_gesture_blink,
            isHeadTurn = false,
            assignableAsSwitch = true
        ),
        Meta(
            id = CameraSwitchFacialGesture.HEAD_TURN_LEFT,
            nameResId = R.string.gesture_head_turn_left,
            isHeadTurn = true,
            assignableAsSwitch = false
        ),
        Meta(
            id = CameraSwitchFacialGesture.HEAD_TURN_RIGHT,
            nameResId = R.string.gesture_head_turn_right,
            isHeadTurn = true,
            assignableAsSwitch = false
        ),
        Meta(
            id = CameraSwitchFacialGesture.HEAD_TURN_UP,
            nameResId = R.string.gesture_head_turn_up,
            isHeadTurn = true,
            assignableAsSwitch = false
        ),
        Meta(
            id = CameraSwitchFacialGesture.HEAD_TURN_DOWN,
            nameResId = R.string.gesture_head_turn_down,
            isHeadTurn = true,
            assignableAsSwitch = false
        )
    ).associateBy { it.id }

    fun getName(id: String): String {
        val meta = entries[id]
        return if (meta != null) Resources.getString(meta.nameResId) else ""
    }

    fun isHeadTurn(id: String): Boolean = entries[id]?.isHeadTurn == true

    fun isAssignableAsSwitch(id: String): Boolean = entries[id]?.assignableAsSwitch == true

    fun allIds(): List<String> = entries.keys.toList()

    fun switchAssignableIds(): List<String> = entries.values.filter { it.assignableAsSwitch }.map { it.id }
}

