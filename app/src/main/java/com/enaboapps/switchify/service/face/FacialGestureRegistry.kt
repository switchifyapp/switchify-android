package com.enaboapps.switchify.service.face

import android.content.Context
import com.enaboapps.switchify.R
import com.enaboapps.switchify.switches.CameraSwitchFacialGesture

/**
 * Unified registry for facial gesture metadata, UI string resources, and MediaPipe mappings.
 * Eliminates hardcoded gesture name/description mappings across the codebase.
 * 
 * This registry provides a centralized location for all facial gesture-related information
 * including display names, descriptions, string resource IDs, and MediaPipe blend shape mappings.
 */
object FacialGestureRegistry {

    /**
     * Metadata for a facial gesture including UI resources and MediaPipe mapping
     */
    data class GestureMetadata(
        val id: String,
        val nameResourceId: Int,
        val descriptionResourceId: Int,
        val isHeadTurn: Boolean,
        val isAssignableAsSwitch: Boolean,
        val mediaBlendShapes: List<String> = emptyList()
    )

    /**
     * Complete registry of all facial gestures with their metadata
     */
    private val gestureRegistry = mapOf(
        CameraSwitchFacialGesture.SMILE to GestureMetadata(
            id = CameraSwitchFacialGesture.SMILE,
            nameResourceId = R.string.gesture_smile,
            descriptionResourceId = R.string.gesture_smile_description,
            isHeadTurn = false,
            isAssignableAsSwitch = true,
            mediaBlendShapes = listOf(
                FacialExpressionConstants.BlendShapeNames.MOUTH_SMILE_LEFT,
                FacialExpressionConstants.BlendShapeNames.MOUTH_SMILE_RIGHT
            )
        ),
        
        CameraSwitchFacialGesture.LEFT_WINK to GestureMetadata(
            id = CameraSwitchFacialGesture.LEFT_WINK,
            nameResourceId = R.string.gesture_left_wink,
            descriptionResourceId = R.string.gesture_left_wink_description,
            isHeadTurn = false,
            isAssignableAsSwitch = true,
            mediaBlendShapes = listOf(
                FacialExpressionConstants.BlendShapeNames.EYE_BLINK_LEFT,
                FacialExpressionConstants.BlendShapeNames.EYE_SQUINT_LEFT
            )
        ),
        
        CameraSwitchFacialGesture.RIGHT_WINK to GestureMetadata(
            id = CameraSwitchFacialGesture.RIGHT_WINK,
            nameResourceId = R.string.gesture_right_wink,
            descriptionResourceId = R.string.gesture_right_wink_description,
            isHeadTurn = false,
            isAssignableAsSwitch = true,
            mediaBlendShapes = listOf(
                FacialExpressionConstants.BlendShapeNames.EYE_BLINK_RIGHT,
                FacialExpressionConstants.BlendShapeNames.EYE_SQUINT_RIGHT
            )
        ),
        
        CameraSwitchFacialGesture.BLINK to GestureMetadata(
            id = CameraSwitchFacialGesture.BLINK,
            nameResourceId = R.string.gesture_blink,
            descriptionResourceId = R.string.gesture_blink_description,
            isHeadTurn = false,
            isAssignableAsSwitch = true,
            mediaBlendShapes = listOf(
                FacialExpressionConstants.BlendShapeNames.EYE_BLINK_LEFT,
                FacialExpressionConstants.BlendShapeNames.EYE_BLINK_RIGHT,
                FacialExpressionConstants.BlendShapeNames.EYE_SQUINT_LEFT,
                FacialExpressionConstants.BlendShapeNames.EYE_SQUINT_RIGHT
            )
        ),
        
        CameraSwitchFacialGesture.HEAD_TURN_LEFT to GestureMetadata(
            id = CameraSwitchFacialGesture.HEAD_TURN_LEFT,
            nameResourceId = R.string.gesture_head_turn_left,
            descriptionResourceId = R.string.gesture_head_turn_left_description,
            isHeadTurn = true,
            isAssignableAsSwitch = false
        ),
        
        CameraSwitchFacialGesture.HEAD_TURN_RIGHT to GestureMetadata(
            id = CameraSwitchFacialGesture.HEAD_TURN_RIGHT,
            nameResourceId = R.string.gesture_head_turn_right,
            descriptionResourceId = R.string.gesture_head_turn_right_description,
            isHeadTurn = true,
            isAssignableAsSwitch = false
        ),
        
        CameraSwitchFacialGesture.HEAD_TURN_UP to GestureMetadata(
            id = CameraSwitchFacialGesture.HEAD_TURN_UP,
            nameResourceId = R.string.gesture_head_turn_up,
            descriptionResourceId = R.string.gesture_head_turn_up_description,
            isHeadTurn = true,
            isAssignableAsSwitch = false
        ),
        
        CameraSwitchFacialGesture.HEAD_TURN_DOWN to GestureMetadata(
            id = CameraSwitchFacialGesture.HEAD_TURN_DOWN,
            nameResourceId = R.string.gesture_head_turn_down,
            descriptionResourceId = R.string.gesture_head_turn_down_description,
            isHeadTurn = true,
            isAssignableAsSwitch = false
        )
    )

    /**
     * Gets gesture metadata by ID
     * 
     * @param gestureId The gesture identifier
     * @return GestureMetadata or null if not found
     */
    fun getGestureMetadata(gestureId: String): GestureMetadata? {
        return gestureRegistry[gestureId]
    }

    /**
     * Gets localized gesture name from string resources
     * 
     * @param context Android context for string resource access
     * @param gestureId The gesture identifier
     * @return Localized name or "Unknown" if not found
     */
    fun getGestureName(context: Context, gestureId: String): String {
        return getGestureMetadata(gestureId)?.let { metadata ->
            context.getString(metadata.nameResourceId)
        } ?: context.getString(R.string.gesture_unknown)
    }

    /**
     * Gets localized gesture description from string resources
     * 
     * @param context Android context for string resource access
     * @param gestureId The gesture identifier
     * @return Localized description or "Unknown" if not found
     */
    fun getGestureDescription(context: Context, gestureId: String): String {
        return getGestureMetadata(gestureId)?.let { metadata ->
            context.getString(metadata.descriptionResourceId)
        } ?: context.getString(R.string.gesture_unknown_description)
    }

    /**
     * Checks if a gesture is a head turn gesture
     * 
     * @param gestureId The gesture identifier
     * @return true if it's a head turn gesture, false otherwise
     */
    fun isHeadTurnGesture(gestureId: String): Boolean {
        return getGestureMetadata(gestureId)?.isHeadTurn ?: false
    }

    /**
     * Checks if a gesture can be assigned as a switch
     * 
     * @param gestureId The gesture identifier
     * @return true if assignable as switch, false otherwise
     */
    fun isAssignableAsSwitch(gestureId: String): Boolean {
        return getGestureMetadata(gestureId)?.isAssignableAsSwitch ?: false
    }

    /**
     * Gets MediaPipe blend shapes associated with a gesture
     * 
     * @param gestureId The gesture identifier
     * @return List of MediaPipe blend shape names
     */
    fun getMediaPipeBlendShapes(gestureId: String): List<String> {
        return getGestureMetadata(gestureId)?.mediaBlendShapes ?: emptyList()
    }

    /**
     * Gets all registered gesture IDs
     * 
     * @return Set of all gesture IDs
     */
    fun getAllGestureIds(): Set<String> {
        return gestureRegistry.keys
    }

    /**
     * Gets all gestures that can be assigned as switches
     * 
     * @return List of switch-assignable gesture IDs
     */
    fun getAssignableGestures(): List<String> {
        return gestureRegistry.values
            .filter { it.isAssignableAsSwitch }
            .map { it.id }
    }

    /**
     * Gets all head turn gestures
     * 
     * @return List of head turn gesture IDs
     */
    fun getHeadTurnGestures(): List<String> {
        return gestureRegistry.values
            .filter { it.isHeadTurn }
            .map { it.id }
    }

    /**
     * Creates a CameraSwitchFacialGesture with registry validation
     * 
     * @param gestureId The gesture identifier
     * @return CameraSwitchFacialGesture instance
     * @throws IllegalArgumentException if gesture ID is not registered
     */
    fun createValidatedGesture(gestureId: String): CameraSwitchFacialGesture {
        require(gestureRegistry.containsKey(gestureId)) {
            "Unknown gesture ID: $gestureId. Valid IDs: ${getAllGestureIds()}"
        }
        return CameraSwitchFacialGesture(gestureId)
    }
}