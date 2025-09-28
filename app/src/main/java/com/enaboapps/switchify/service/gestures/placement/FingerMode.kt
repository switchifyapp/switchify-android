package com.enaboapps.switchify.service.gestures.placement

import android.content.Context
import com.enaboapps.switchify.R

/**
 * Enumeration of finger count modes for gesture execution.
 *
 * This enum defines user preferences for the number of fingers to use when performing
 * gestures. The extensible design allows for future finger count options without
 * requiring changes to the core algorithm or gesture execution logic.
 *
 * Design Philosophy:
 * - Extensible: New finger counts can be added without modifying existing code
 * - User-centric: Provides clear options for different user needs and preferences
 * - Future-proof: AUTO mode allows algorithm to evolve and adapt over time
 * - Accessibility-focused: Supports various motor impairment scenarios
 *
 * Mode Descriptions:
 * - ONE: Force single-finger gestures (traditional mode, maximum precision)
 * - TWO: Force two-finger gestures (enhanced stability, reduced precision requirements)
 * - AUTO: Algorithm determines optimal finger count based on context and gesture type
 * - Future extensions: THREE, FOUR, CUSTOM patterns for specialized accessibility needs
 *
 * Integration Points:
 * - FingerPlacementAlgorithm: Uses mode to determine optimal finger count
 * - PreferenceManager: Stores user's selected mode preference
 * - GestureMenuStructure: Provides UI for mode selection
 * - GestureManager: Passes current mode to algorithm for each gesture
 */
enum class FingerMode {
    /**
     * Force single-finger gestures for all compatible gesture types.
     *
     * Benefits:
     * - Maximum precision and accuracy
     * - Compatible with all gesture types
     * - Minimal screen space requirements
     * - Traditional touch interaction model
     *
     * Best for: Users who prefer precise control and have good fine motor skills
     */
    ONE,

    /**
     * Force two-finger gestures for all compatible gesture types.
     *
     * Benefits:
     * - Enhanced stability and reduced tremor impact
     * - Easier targeting for users with motor impairments
     * - More visible gesture indicators
     * - Better feedback for gesture recognition
     *
     * Best for: Users with tremors, limited precision, or who benefit from larger targets
     */
    TWO;

    /**
     * Returns the display name for the finger mode.
     * Used in UI components and preference screens.
     */
    fun getDisplayName(context: Context): String {
        return when (this) {
            ONE -> context.getString(R.string.menu_item_one_finger)
            TWO -> context.getString(R.string.menu_item_two_fingers)
        }
    }

    /**
     * Returns a description of the finger mode for help text.
     * Used in settings screens and accessibility descriptions.
     */
    fun getDescription(context: Context): String {
        return when (this) {
            ONE -> context.getString(R.string.finger_mode_description_one)
            TWO -> context.getString(R.string.finger_mode_description_two)
        }
    }

    /**
     * Converts string preference values back to enum values.
     * Used by PreferenceManager for persistence and retrieval.
     */
    companion object {
        fun fromString(value: String): FingerMode {
            return try {
                valueOf(value.uppercase())
            } catch (e: IllegalArgumentException) {
                // Default to ONE finger for unknown values
                ONE
            }
        }

        /**
         * Returns the default finger mode for new users.
         * ONE provides reliable precision for all users.
         */
        fun getDefault(): FingerMode = ONE

        /**
         * Returns all available finger modes for UI selection.
         * Currently returns ONE, TWO - simple explicit choice.
         */
        fun getAllModes(): List<FingerMode> = values().toList()
    }
}