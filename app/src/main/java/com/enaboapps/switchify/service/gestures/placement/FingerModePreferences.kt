package com.enaboapps.switchify.service.gestures.placement

import android.util.Log
import com.enaboapps.switchify.backend.preferences.PreferenceManager

/**
 * Centralized management for finger mode preferences across the gesture system.
 * 
 * This class provides a single source of truth for finger mode preference operations,
 * preventing key mismatches and ensuring consistent behavior across all components
 * that need to read or write finger mode settings.
 * 
 * ## Architecture Benefits:
 * - **Single Source of Truth**: One place to define preference key and logic
 * - **Consistency**: All components use the same preference access methods
 * - **Maintainability**: Changes to preference handling only need to be made here
 * - **Error Prevention**: Eliminates key mismatch issues between components
 * - **Type Safety**: Strong typing for FingerMode enum values
 * 
 * ## Integration Points:
 * - GestureManager: Uses this for tap gesture finger mode decisions
 * - LinearGesturePerformer: Uses this for linear gesture finger placement
 * - FingerModeMenu: Uses this for UI preference display and updates
 * - Settings screens: Uses this for user preference management
 * 
 * ## Thread Safety:
 * This class delegates to PreferenceManager which handles thread safety.
 * All preference operations are safe to call from any thread.
 */
object FingerModePreferences {
    
    private const val TAG = "FingerModePreferences"
    
    /**
     * The canonical preference key for finger mode settings.
     * This is the single source of truth - all components should use this key.
     */
    private const val FINGER_MODE_PREFERENCE_KEY = "gesture_finger_mode"
    
    /**
     * Gets the current finger mode preference from user settings.
     * 
     * This method handles all the complexity of preference retrieval including:
     * - Null/empty value handling
     * - String parsing and validation
     * - Fallback to sensible defaults
     * - Error handling and logging
     * 
     * @param preferenceManager The preference manager instance to use
     * @return Current FingerMode setting, defaults to ONE if not set or invalid
     */
    fun getCurrentFingerMode(preferenceManager: PreferenceManager): FingerMode {
        return try {
            val modeString = preferenceManager.getStringValue(FINGER_MODE_PREFERENCE_KEY)
            Log.d(TAG, "Retrieved finger mode preference: '$modeString'")
            
            val fingerMode = when {
                modeString.isNullOrEmpty() -> {
                    Log.d(TAG, "No finger mode preference set, using default: ${FingerMode.getDefault()}")
                    FingerMode.getDefault()
                }
                else -> {
                    FingerMode.fromString(modeString).also { mode ->
                        Log.d(TAG, "Parsed finger mode: $mode")
                    }
                }
            }
            
            fingerMode
            
        } catch (e: Exception) {
            Log.e(TAG, "Error retrieving finger mode preference, using default", e)
            FingerMode.getDefault()
        }
    }
    
    /**
     * Sets the finger mode preference in user settings.
     * 
     * This method handles the complexity of preference storage including:
     * - Enum to string conversion
     * - Preference validation
     * - Error handling and logging
     * - Confirmation of successful storage
     * 
     * @param preferenceManager The preference manager instance to use
     * @param fingerMode The finger mode to set
     * @return true if the preference was successfully set, false otherwise
     */
    fun setFingerMode(preferenceManager: PreferenceManager, fingerMode: FingerMode): Boolean {
        return try {
            Log.d(TAG, "Setting finger mode preference to: $fingerMode")
            
            preferenceManager.setStringValue(FINGER_MODE_PREFERENCE_KEY, fingerMode.name)
            
            // Verify the preference was actually saved
            val verification = getCurrentFingerMode(preferenceManager)
            if (verification == fingerMode) {
                Log.d(TAG, "Successfully set and verified finger mode preference to: $fingerMode")
                true
            } else {
                Log.w(TAG, "Finger mode preference verification failed - expected: $fingerMode, got: $verification")
                false
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error setting finger mode preference to: $fingerMode", e)
            false
        }
    }
    
    /**
     * Resets the finger mode preference to the default value.
     * 
     * This is useful for:
     * - User-initiated preference reset
     * - Error recovery scenarios
     * - App initialization/first run
     * 
     * @param preferenceManager The preference manager instance to use
     * @return true if the preference was successfully reset, false otherwise
     */
    fun resetToDefault(preferenceManager: PreferenceManager): Boolean {
        val defaultMode = FingerMode.getDefault()
        Log.d(TAG, "Resetting finger mode preference to default: $defaultMode")
        return setFingerMode(preferenceManager, defaultMode)
    }
    
    /**
     * Checks if a finger mode preference has been explicitly set by the user.
     * 
     * This is useful for:
     * - First-run experience logic
     * - Onboarding flows
     * - Migration scenarios
     * 
     * @param preferenceManager The preference manager instance to use
     * @return true if user has set a preference, false if using default
     */
    fun hasExplicitPreference(preferenceManager: PreferenceManager): Boolean {
        val modeString = preferenceManager.getStringValue(FINGER_MODE_PREFERENCE_KEY)
        val hasPreference = !modeString.isNullOrEmpty()
        Log.d(TAG, "Has explicit finger mode preference: $hasPreference")
        return hasPreference
    }
    
    /**
     * Gets all available finger modes for UI selection.
     * 
     * This is a convenience method that delegates to FingerMode.getAllModes()
     * but provides a centralized access point for UI components.
     * 
     * @return Complete list of all available finger modes
     */
    fun getAllModes(): List<FingerMode> = FingerMode.getAllModes()
    
    /**
     * Gets the preference key used for finger mode storage.
     * 
     * This should only be used in rare cases where direct preference access is needed.
     * In most cases, use getCurrentFingerMode() and setFingerMode() instead.
     * 
     * @return The preference key string
     */
    fun getPreferenceKey(): String = FINGER_MODE_PREFERENCE_KEY
    
    /**
     * Validates that a finger mode preference value is valid.
     * 
     * This is useful for:
     * - Data migration scenarios
     * - External preference import/export
     * - Debugging preference issues
     * 
     * @param modeString The string value to validate
     * @return true if the string represents a valid finger mode, false otherwise
     */
    fun isValidFingerMode(modeString: String?): Boolean {
        if (modeString.isNullOrEmpty()) return false
        
        return try {
            FingerMode.fromString(modeString)
            true
        } catch (e: Exception) {
            Log.w(TAG, "Invalid finger mode string: '$modeString'", e)
            false
        }
    }
    
    /**
     * Gets diagnostic information about the current finger mode preference state.
     * 
     * This is useful for debugging preference-related issues and provides
     * detailed information about the current state.
     * 
     * @param preferenceManager The preference manager instance to use
     * @return Map containing diagnostic information
     */
    fun getDiagnosticInfo(preferenceManager: PreferenceManager): Map<String, Any> {
        val rawValue = preferenceManager.getStringValue(FINGER_MODE_PREFERENCE_KEY)
        val currentMode = getCurrentFingerMode(preferenceManager)
        val hasExplicit = hasExplicitPreference(preferenceManager)
        val isValid = isValidFingerMode(rawValue)
        
        return mapOf(
            "preferenceKey" to FINGER_MODE_PREFERENCE_KEY,
            "rawValue" to (rawValue ?: "null"),
            "currentMode" to currentMode.name,
            "hasExplicitPreference" to hasExplicit,
            "isValidValue" to isValid,
            "defaultMode" to FingerMode.getDefault().name,
            "availableModes" to getAllModes().map { it.name }
        )
    }
}