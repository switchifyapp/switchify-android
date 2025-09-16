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

    fun getDescription(): String = FacialGestureRegistry.getDescription(id)

    fun isHeadTurn(): Boolean = FacialGestureRegistry.isHeadTurn(id)

    fun isAssignableAsSwitch(): Boolean = FacialGestureRegistry.isAssignableAsSwitch(id)
}
