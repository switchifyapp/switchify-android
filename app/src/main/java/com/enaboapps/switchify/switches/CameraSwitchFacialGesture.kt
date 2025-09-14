package com.enaboapps.switchify.switches

import com.enaboapps.switchify.service.face.FacialGestureRegistry

/**
 * A class representing a camera switch facial gesture.
 * Used to identify and react to facial gestures in the camera switch.
 *
 * @property id The unique identifier of the gesture.
 */
class CameraSwitchFacialGesture(val id: String) {
    companion object {
        const val SMILE = "smile"
        const val LEFT_WINK = "left_wink"
        const val RIGHT_WINK = "right_wink"
        const val BLINK = "blink"
        const val HEAD_TURN_LEFT = "head_turn_left"
        const val HEAD_TURN_RIGHT = "head_turn_right"
        const val HEAD_TURN_UP = "head_turn_up"
        const val HEAD_TURN_DOWN = "head_turn_down"
    }

    fun getName(): String = FacialGestureRegistry.getName(id)

    fun getDescription(): String {
        return when (id) {
            SMILE -> "Smile"
            LEFT_WINK -> "Wink with your left eye"
            RIGHT_WINK -> "Wink with your right eye"
            BLINK -> "Blink with your eyes"
            HEAD_TURN_LEFT -> "Turn your head to the left"
            HEAD_TURN_RIGHT -> "Turn your head to the right"
            HEAD_TURN_UP -> "Turn your head up"
            HEAD_TURN_DOWN -> "Turn your head down"
            else -> "Unknown"
        }
    }

    fun isHeadTurn(): Boolean = FacialGestureRegistry.isHeadTurn(id)

    fun isAssignableAsSwitch(): Boolean = FacialGestureRegistry.isAssignableAsSwitch(id)
}
