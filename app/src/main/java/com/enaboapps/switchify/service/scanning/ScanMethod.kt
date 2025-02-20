package com.enaboapps.switchify.service.scanning

import android.os.Handler
import android.os.Looper
import com.enaboapps.switchify.R
import com.enaboapps.switchify.backend.iap.IAPHandler
import com.enaboapps.switchify.backend.preferences.PreferenceManager
import com.enaboapps.switchify.service.window.ServiceMessageHUD
import com.enaboapps.switchify.utils.Logger
import com.enaboapps.switchify.utils.Resources
import kotlin.properties.Delegates

/**
 * This interface is used to observe the scan method
 */
interface ScanMethodObserver {
    /**
     * This function is called when the scan method is changed
     * @param type The type of the scan method
     */
    fun onScanMethodChanged(type: String)

    /**
     * This function is called when the menu state is changed
     * @param isInMenu The new menu state
     */
    fun onMenuStateChanged(isInMenu: Boolean)
}

/**
 * This object is used to manage the scanning method
 */
object ScanMethod {
    var preferenceManager: PreferenceManager? = null
    var observer: ScanMethodObserver? = null

    /**
     * This variable is used to determine if the scanning is in the menu
     * Uses Kotlin's observable delegate for state management
     */
    var isInMenu: Boolean by Delegates.observable(false) { _, _, newValue ->
        observer?.onMenuStateChanged(newValue)
    }

    /**
     * This enum represents the type of the scanning method
     */
    object MethodType {
        /**
         * This type represents the cursor
         */
        const val CURSOR = "cursor"

        /**
         * This type represents the radar
         */
        const val RADAR = "radar"

        /**
         * This type represents the item scan
         * Sequentially scanning the items on the screen
         */
        const val ITEM_SCAN = "item_scan"
    }

    /**
     * This function is used to get the type of the scanning method
     */
    fun getType(): String {
        preferenceManager?.let { preferenceManager ->
            val storedType = preferenceManager.getStringValue(
                PreferenceManager.PREFERENCE_KEY_SCAN_METHOD
            )
            println("Stored type: $storedType")
            if (storedType.isNotEmpty()) {
                return storedType
            }
        }
        return MethodType.CURSOR
    }

    /**
     * This function gets the name of the scanning method
     * @param type The type of the scanning method
     * @return The name of the scanning method
     */
    fun getName(type: String): String {
        return when (type) {
            MethodType.CURSOR -> Resources.getString(R.string.scan_method_cursor)
            MethodType.RADAR -> Resources.getString(R.string.scan_method_radar)
            MethodType.ITEM_SCAN -> Resources.getString(R.string.scan_method_item_scan)
            else -> Resources.getString(R.string.unknown)
        }
    }

    /**
     * This function gets the description of the scanning method
     * @param type The type of the scanning method
     * @return The description of the scanning method
     */
    fun getDescription(type: String): String {
        return when (type) {
            MethodType.CURSOR -> Resources.getString(R.string.scan_method_desc_cursor)
            MethodType.RADAR -> Resources.getString(R.string.scan_method_desc_radar)
            MethodType.ITEM_SCAN -> Resources.getString(R.string.scan_method_desc_item_scan)
            else -> Resources.getString(R.string.unknown)
        }
    }

    /**
     * This function is used to set the type of the scanning method
     */
    fun setType(value: String) {
        preferenceManager?.setStringValue(
            PreferenceManager.PREFERENCE_KEY_SCAN_METHOD,
            value
        )
        observer?.onScanMethodChanged(value)

        // If radar and not pro, start the timer to switch to cursor
        if (value == MethodType.RADAR && !IAPHandler.hasPurchasedPro()) {
            startRadarTrialTimer()
        }
    }

    /**
     * Starts the radar trial timer.
     * 20 seconds after the radar scan is selected, it switches to the cursor scan if the user is not a pro.
     */
    private fun startRadarTrialTimer() {
        Handler(Looper.getMainLooper()).postDelayed({
            if (getType() == MethodType.RADAR && !IAPHandler.hasPurchasedPro()) {
                setType(MethodType.CURSOR)
                ServiceMessageHUD.instance.showMessage(
                    R.string.radar_trial_timer_expired,
                    ServiceMessageHUD.MessageType.DISAPPEARING
                )
                Logger.logEvent("Radar trial timer expired")
            }
        }, 20000)
    }
}