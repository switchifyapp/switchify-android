package com.enaboapps.switchify.service.utils

import android.content.Context
import com.enaboapps.switchify.service.switches.SwitchEventProvider
import com.enaboapps.switchify.service.techniques.AccessTechnique
import com.enaboapps.switchify.service.techniques.headcontrol.HeadControlSettings
import com.enaboapps.switchify.switches.CameraSwitchFacialGesture

/**
 * Utility class for detecting and managing conflicts between head control selection gestures
 * and camera switch assignments.
 */
class GestureConflictDetector(private val context: Context) {
    
    private val headControlSettings = HeadControlSettings(context)
    
    companion object {
        private const val TAG = "GestureConflictDetector"
        
        /**
         * Gestures that can be used for both head control selection and switch assignments
         */
        val CONFLICTABLE_GESTURES = setOf(
            CameraSwitchFacialGesture.SMILE,
            CameraSwitchFacialGesture.LEFT_WINK,
            CameraSwitchFacialGesture.RIGHT_WINK,
            CameraSwitchFacialGesture.BLINK
        )
    }
    
    /**
     * Checks if the given gesture conflicts with head control selection when head control is active.
     * 
     * @param gestureId The gesture ID to check
     * @param switchEventProvider Provider to check switch assignments
     * @return true if there's a conflict that should prioritize head control
     */
    fun shouldPrioritizeHeadControl(gestureId: String, switchEventProvider: SwitchEventProvider?): Boolean {
        // Respect user-configured priority preference
        if (!headControlSettings.isHeadControlPriorityEnabled()) {
            return false
        }
        
        // Head control is now independent - TODO: check if head control is enabled
        // For now, assume head control could be active
        // if (!headControlEnabled) return false
        
        // Head control gesture selection must be enabled
        if (!headControlSettings.isGestureSelectionEnabled()) {
            return false
        }
        
        // Check if this gesture is the configured head control selection gesture
        val headControlGesture = headControlSettings.selectGesture()
        if (gestureId != headControlGesture) {
            return false
        }
        
        // Check if this gesture is also assigned to a switch
        return switchEventProvider?.isFacialGestureAssigned(gestureId) == true
    }
    
    /**
     * Detects all gesture conflicts between head control and switch assignments.
     * 
     * @param switchEventProvider Provider to check switch assignments
     * @return Set of gesture IDs that have conflicts
     */
    fun detectConflicts(switchEventProvider: SwitchEventProvider?): Set<String> {
        val conflicts = mutableSetOf<String>()
        
        if (!headControlSettings.isGestureSelectionEnabled() || switchEventProvider == null) {
            return conflicts
        }
        
        val headControlGesture = headControlSettings.selectGesture()
        
        // Check if the head control selection gesture is assigned to a switch
        if (CONFLICTABLE_GESTURES.contains(headControlGesture) && 
            switchEventProvider.isFacialGestureAssigned(headControlGesture)) {
            conflicts.add(headControlGesture)
        }
        
        return conflicts
    }
    
    /**
     * Gets the current head control selection gesture.
     * 
     * @return The gesture ID configured for head control selection, or null if disabled
     */
    fun getHeadControlSelectionGesture(): String? {
        return if (headControlSettings.isGestureSelectionEnabled()) {
            headControlSettings.selectGesture()
        } else {
            null
        }
    }
    
    /**
     * Checks if a specific gesture is valid for head control selection.
     * 
     * @param gestureId The gesture ID to validate
     * @return true if the gesture can be used for head control selection
     */
    fun isValidHeadControlGesture(gestureId: String): Boolean {
        return headControlSettings.isValidSelectGesture(gestureId)
    }
    
    /**
     * Gets information about a gesture conflict.
     * 
     * @param gestureId The gesture to get conflict info for
     * @param switchEventProvider Provider to check switch assignments
     * @return ConflictInfo describing the conflict, or null if no conflict
     */
    fun getConflictInfo(gestureId: String, switchEventProvider: SwitchEventProvider?): ConflictInfo? {
        if (!shouldPrioritizeHeadControl(gestureId, switchEventProvider)) {
            return null
        }
        
        return ConflictInfo(
            gestureId = gestureId,
            isHeadControlGesture = gestureId == headControlSettings.selectGesture(),
            isSwitchAssigned = switchEventProvider?.isFacialGestureAssigned(gestureId) == true,
            headControlEnabled = headControlSettings.isGestureSelectionEnabled(),
            currentTechnique = AccessTechnique.getCurrentTechnique()
        )
    }
    
    /**
     * Data class containing information about a gesture conflict.
     */
    data class ConflictInfo(
        val gestureId: String,
        val isHeadControlGesture: Boolean,
        val isSwitchAssigned: Boolean,
        val headControlEnabled: Boolean,
        val currentTechnique: String
    ) {
        /**
         * Returns true if head control should take priority over switch actions.
         */
        fun shouldPrioritizeHeadControl(): Boolean {
            return isHeadControlGesture && 
                   isSwitchAssigned && 
                   headControlEnabled
                   // Head control is now independent of access technique
        }
    }
}
