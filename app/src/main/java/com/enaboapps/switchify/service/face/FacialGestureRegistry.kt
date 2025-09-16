package com.enaboapps.switchify.service.face

import com.enaboapps.switchify.R
import com.enaboapps.switchify.switches.CameraSwitchFacialGesture
import com.enaboapps.switchify.utils.Resources

object FacialGestureRegistry {
    data class Meta(
        val id: String,
        val nameResId: Int?,
        val descriptionResId: Int?,
        val fallbackName: String,
        val fallbackDescription: String,
        val isHeadTurn: Boolean,
        val assignableAsSwitch: Boolean
    )

    private val entries: Map<String, Meta> = listOf(
        Meta(
            id = CameraSwitchFacialGesture.SMILE,
            nameResId = R.string.head_control_gesture_smile,
            descriptionResId = R.string.head_control_gesture_smile_description,
            fallbackName = "Smile",
            fallbackDescription = "Smile",
            isHeadTurn = false,
            assignableAsSwitch = true
        ),
        Meta(
            id = CameraSwitchFacialGesture.LEFT_WINK,
            nameResId = R.string.head_control_gesture_left_wink,
            descriptionResId = R.string.head_control_gesture_left_wink_description,
            fallbackName = "Left Wink",
            fallbackDescription = "Wink with your left eye",
            isHeadTurn = false,
            assignableAsSwitch = true
        ),
        Meta(
            id = CameraSwitchFacialGesture.RIGHT_WINK,
            nameResId = R.string.head_control_gesture_right_wink,
            descriptionResId = R.string.head_control_gesture_right_wink_description,
            fallbackName = "Right Wink",
            fallbackDescription = "Wink with your right eye",
            isHeadTurn = false,
            assignableAsSwitch = true
        ),
        Meta(
            id = CameraSwitchFacialGesture.BLINK,
            nameResId = R.string.head_control_gesture_blink,
            descriptionResId = R.string.head_control_gesture_blink_description,
            fallbackName = "Blink",
            fallbackDescription = "Blink with your eyes",
            isHeadTurn = false,
            assignableAsSwitch = true
        ),
        Meta(
            id = CameraSwitchFacialGesture.HEAD_TURN_LEFT,
            nameResId = R.string.gesture_head_turn_left,
            descriptionResId = R.string.gesture_head_turn_left_description,
            fallbackName = "Head Turn Left",
            fallbackDescription = "Turn your head to the left",
            isHeadTurn = true,
            assignableAsSwitch = false
        ),
        Meta(
            id = CameraSwitchFacialGesture.HEAD_TURN_RIGHT,
            nameResId = R.string.gesture_head_turn_right,
            descriptionResId = R.string.gesture_head_turn_right_description,
            fallbackName = "Head Turn Right",
            fallbackDescription = "Turn your head to the right",
            isHeadTurn = true,
            assignableAsSwitch = false
        ),
        Meta(
            id = CameraSwitchFacialGesture.HEAD_TURN_UP,
            nameResId = R.string.gesture_head_turn_up,
            descriptionResId = R.string.gesture_head_turn_up_description,
            fallbackName = "Head Turn Up",
            fallbackDescription = "Turn your head up",
            isHeadTurn = true,
            assignableAsSwitch = false
        ),
        Meta(
            id = CameraSwitchFacialGesture.HEAD_TURN_DOWN,
            nameResId = R.string.gesture_head_turn_down,
            descriptionResId = R.string.gesture_head_turn_down_description,
            fallbackName = "Head Turn Down",
            fallbackDescription = "Turn your head down",
            isHeadTurn = true,
            assignableAsSwitch = false
        )
    ).associateBy { it.id }

    fun getName(id: String): String {
        val meta = entries[id] ?: return ""
        return meta.nameResId?.let { Resources.getString(it) } ?: meta.fallbackName
    }

    fun getDescription(id: String): String {
        val meta = entries[id] ?: return ""
        return meta.descriptionResId?.let { Resources.getString(it) } ?: meta.fallbackDescription
    }

    fun isHeadTurn(id: String): Boolean = entries[id]?.isHeadTurn == true

    fun isAssignableAsSwitch(id: String): Boolean = entries[id]?.assignableAsSwitch == true

    fun allIds(): List<String> = entries.keys.toList()

    fun switchAssignableIds(): List<String> = entries.values.filter { it.assignableAsSwitch }.map { it.id }
}
