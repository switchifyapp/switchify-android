package com.enaboapps.switchify.service.techniques

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.enaboapps.switchify.R
import com.enaboapps.switchify.backend.iap.IAPHandler
import com.enaboapps.switchify.backend.preferences.PreferenceManager
import com.enaboapps.switchify.service.window.ServiceMessageHUD
import com.enaboapps.switchify.utils.LogEvent
import com.enaboapps.switchify.utils.Logger
import com.enaboapps.switchify.utils.Resources

/**
 * This interface is used to observe the access technique
 */
interface AccessTechniqueObserver {
    /**
     * This function is called when the access technique is changed
     * @param accessTechnique The type of the access technique
     */
    fun onAccessTechniqueChanged(accessTechnique: String)
}

/**
 * This object is used to manage the access technique
 */
object AccessTechnique {
    private var currentTechnique: String = Technique.ITEM_SCAN
    private var preferenceManager: PreferenceManager? = null
    var observer: AccessTechniqueObserver? = null

    fun init(context: Context) {
        preferenceManager = PreferenceManager(context)
        loadCurrentTechnique()
    }

    /**
     * This enum represents the type of the scanning method
     */
    object Technique {
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

        /**
         * This type represents the menu
         */
        const val MENU = "menu"
    }

    /**
     * This function is used to get the current technique
     * @return The current technique
     */
    fun getCurrentTechnique(): String = currentTechnique

    /**
     * This function gets the name of the access technique
     * @param accessTechnique The type of the access technique
     * @return The name of the access technique
     */
    fun getName(accessTechnique: String): String {
        return when (accessTechnique) {
            Technique.CURSOR -> Resources.getString(R.string.access_technique_cursor)
            Technique.RADAR -> Resources.getString(R.string.access_technique_radar)
            Technique.ITEM_SCAN -> Resources.getString(R.string.access_technique_item_scan)
            else -> Resources.getString(R.string.unknown)
        }
    }

    /**
     * This function gets the description of the scanning method
     * @param accessTechnique The type of the scanning method
     * @return The description of the scanning method
     */
    fun getDescription(accessTechnique: String): String {
        return when (accessTechnique) {
            Technique.CURSOR -> Resources.getString(R.string.access_technique_desc_cursor)
            Technique.RADAR -> Resources.getString(R.string.access_technique_desc_radar)
            Technique.ITEM_SCAN -> Resources.getString(R.string.access_technique_desc_item_scan)
            else -> Resources.getString(R.string.unknown)
        }
    }

    /**
     * This function is used to set the current technique
     * @param value The type of the access technique
     */
    fun setCurrentTechnique(value: String) {
        currentTechnique = value
        observer?.onAccessTechniqueChanged(value)

        // If radar and not pro, start the timer to switch to cursor
        if (value == Technique.RADAR && !IAPHandler.hasPurchasedPro()) {
            startRadarTrialTimer()
        }

        saveCurrentTechnique()
    }

    /**
     * Loads the current technique from the preferences
     */
    internal fun loadCurrentTechnique() {
        preferenceManager?.let { preferenceManager ->
            val storedType = preferenceManager.getStringValue(
                PreferenceManager.PREFERENCE_KEY_ACCESS_TECHNIQUE
            )
            println("Stored type: $storedType")
            if (storedType.isNotEmpty()) {
                currentTechnique = storedType
            } else {
                // Set default technique for new users and save it
                currentTechnique = Technique.ITEM_SCAN
                saveCurrentTechnique()
            }
        }
        observer?.onAccessTechniqueChanged(currentTechnique)
    }

    /**
     * Saves the current technique to the preferences
     */
    private fun saveCurrentTechnique() {
        if (currentTechnique != Technique.MENU && preferenceManager != null) {
            preferenceManager?.setStringValue(
                PreferenceManager.PREFERENCE_KEY_ACCESS_TECHNIQUE,
                currentTechnique
            )
        }
    }

    /**
     * Starts the radar trial timer.
     * 20 seconds after the radar scan is selected, it switches to the cursor scan if the user is not a pro.
     */
    private fun startRadarTrialTimer() {
        Handler(Looper.getMainLooper()).postDelayed({
            if (getCurrentTechnique() == Technique.RADAR && !IAPHandler.hasPurchasedPro()) {
                setCurrentTechnique(Technique.CURSOR)
                ServiceMessageHUD.instance.showMessage(
                    R.string.radar_trial_timer_expired,
                    ServiceMessageHUD.MessageType.DISAPPEARING
                )
                Logger.log(LogEvent.RadarTrialExpired)
            }
        }, 20000)
    }
}