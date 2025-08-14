package com.enaboapps.switchify.service.actions

import android.accessibilityservice.AccessibilityService
import android.util.Log
import com.enaboapps.switchify.service.core.SwitchifyAccessibilityService

/**
 * Centralized manager for performing global accessibility actions.
 * This object provides a single point of control for all system-wide accessibility actions.
 */
object GlobalActionManager {
    private const val TAG = "GlobalActionManager"
    private var accessibilityService: SwitchifyAccessibilityService? = null

    /**
     * Initialize the GlobalActionManager with the accessibility service.
     *
     * @param service The SwitchifyAccessibilityService instance
     */
    fun init(service: SwitchifyAccessibilityService) {
        accessibilityService = service
        Log.d(TAG, "GlobalActionManager initialized")
    }

    /**
     * Perform a global action using the accessibility service.
     *
     * @param action The global action to perform
     * @return true if the action was performed successfully, false otherwise
     */
    private fun performGlobalAction(action: Int): Boolean {
        return accessibilityService?.performGlobalAction(action) ?: run {
            Log.e(TAG, "Failed to perform global action: Accessibility service is null")
            false
        }
    }

    /**
     * Navigate to the home screen.
     * This is a pro feature.
     *
     * @return true if successful, false otherwise
     */
    fun goHome(): Boolean =
        performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)

    /**
     * Perform the back action.
     *
     * @return true if successful, false otherwise
     */
    fun goBack(): Boolean =
        performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)

    /**
     * Open the recent apps screen.
     * This is a pro feature.
     *
     * @return true if successful, false otherwise
     */
    fun openRecents(): Boolean =
        performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS)

    /**
     * Open the quick settings panel.
     * This is a pro feature.
     *
     * @return true if successful, false otherwise
     */
    fun openQuickSettings(): Boolean =
        performGlobalAction(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS)

    /**
     * Open the notifications panel.
     * This is a pro feature.
     *
     * @return true if successful, false otherwise
     */
    fun openNotifications(): Boolean =
        performGlobalAction(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)

    /**
     * Lock the device screen.
     *
     * @return true if successful, false otherwise
     */
    fun lockScreen(): Boolean =
        performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)

    /**
     * Toggle media playback using the headset hook action.
     *
     * @return true if successful, false otherwise
     */
    fun toggleMediaPlayback(): Boolean =
        performGlobalAction(AccessibilityService.GLOBAL_ACTION_KEYCODE_HEADSETHOOK)

    /**
     * Open the power dialog.
     *
     * @return true if successful, false otherwise
     */
    fun openPowerDialog(): Boolean =
        performGlobalAction(AccessibilityService.GLOBAL_ACTION_POWER_DIALOG)

    /**
     * Take a screenshot.
     *
     * @return true if successful, false otherwise
     */
    fun takeScreenshot(): Boolean =
        performGlobalAction(AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT)

    /**
     * Split the screen.
     *
     * @return true if successful, false otherwise
     */
    fun splitScreen(): Boolean =
        performGlobalAction(AccessibilityService.GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN)

    /**
     * Clear the accessibility service reference when the service is destroyed.
     */
    fun cleanup() {
        accessibilityService = null
        Log.d(TAG, "GlobalActionManager cleaned up")
    }
}