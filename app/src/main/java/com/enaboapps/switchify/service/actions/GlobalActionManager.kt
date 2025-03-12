package com.enaboapps.switchify.service.actions

import android.accessibilityservice.AccessibilityService
import android.util.Log
import com.enaboapps.switchify.R
import com.enaboapps.switchify.backend.iap.IAPHandler
import com.enaboapps.switchify.service.core.SwitchifyAccessibilityService
import com.enaboapps.switchify.service.window.ServiceMessageHUD

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
     * Shows a pro feature message using the ServiceMessageHUD.
     *
     * @param feature The name of the pro feature being accessed
     */
    private fun showProFeatureMessage(feature: String) {
        ServiceMessageHUD.Companion.instance.showMessage(
            R.string.pro_feature_message,
            arrayOf(feature),
            ServiceMessageHUD.MessageType.DISAPPEARING
        )
    }

    /**
     * Navigate to the home screen.
     * This is a pro feature.
     *
     * @return true if successful, false otherwise
     */
    fun goHome(): Boolean =
        if (IAPHandler.hasPurchasedPro()) {
            performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
        } else {
            showProFeatureMessage("Home")
            false
        }

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
        if (IAPHandler.hasPurchasedPro()) {
            performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS)
        } else {
            showProFeatureMessage("Recent Apps")
            false
        }

    /**
     * Open the quick settings panel.
     * This is a pro feature.
     *
     * @return true if successful, false otherwise
     */
    fun openQuickSettings(): Boolean =
        if (IAPHandler.hasPurchasedPro()) {
            performGlobalAction(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS)
        } else {
            showProFeatureMessage("Quick Settings")
            false
        }

    /**
     * Open the notifications panel.
     * This is a pro feature.
     *
     * @return true if successful, false otherwise
     */
    fun openNotifications(): Boolean =
        if (IAPHandler.hasPurchasedPro()) {
            performGlobalAction(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)
        } else {
            showProFeatureMessage("Notifications")
            false
        }

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