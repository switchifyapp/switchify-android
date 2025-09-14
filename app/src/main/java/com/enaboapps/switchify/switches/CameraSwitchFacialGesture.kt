package com.enaboapps.switchify.switches

import android.content.Context
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

    /**
     * Gets the localized name of the gesture using the unified registry
     * @param context Android context for string resource access
     * @return Localized gesture name
     */
    fun getName(context: Context): String {
        return FacialGestureRegistry.getGestureName(context, id)
    }

    /**
     * Gets the gesture name without localization support
     * @return Non-localized gesture name from hardcoded fallbacks
     */
    fun getName(): String {
        return when (id) {
            SMILE -> "Smile"
            LEFT_WINK -> "Left Wink" 
            RIGHT_WINK -> "Right Wink"
            BLINK -> "Blink"
            HEAD_TURN_LEFT -> "Head Turn Left"
            HEAD_TURN_RIGHT -> "Head Turn Right"
            HEAD_TURN_UP -> "Head Turn Up"
            HEAD_TURN_DOWN -> "Head Turn Down"
            else -> "Unknown"
        }
    }

    /**
     * Gets the localized description of the gesture using the unified registry
     * @param context Android context for string resource access
     * @return Localized gesture description
     */
    fun getDescription(context: Context): String {
        return FacialGestureRegistry.getGestureDescription(context, id)
    }


    /**
     * Checks if this gesture is a head turn gesture using the unified registry
     * @return true if it's a head turn gesture, false otherwise
     */
    fun isHeadTurn(): Boolean {
        return FacialGestureRegistry.isHeadTurnGesture(id)
    }

    /**
     * Checks if this gesture can be assigned as a switch using the unified registry
     * @return true if assignable as switch, false otherwise
     */
    fun isAssignableAsSwitch(): Boolean {
        return FacialGestureRegistry.isAssignableAsSwitch(id)
    }
}