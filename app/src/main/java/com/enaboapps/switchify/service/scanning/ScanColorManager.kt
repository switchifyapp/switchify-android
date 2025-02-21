package com.enaboapps.switchify.service.scanning

import android.content.Context
import com.enaboapps.switchify.R
import com.enaboapps.switchify.backend.preferences.PreferenceManager
import com.enaboapps.switchify.utils.Resources

/**
 * This class manages the scan colors
 */
class ScanColorManager {
    /**
     * This object represents a scan color set
     * @param nameResId The string resource ID for the name of the color set
     * @param descriptionResId The string resource ID for the description of the color set
     * @param primaryColor The primary color
     * @param secondaryColor The secondary color
     */
    data class ScanColorSet(
        val nameResId: Int,
        val descriptionResId: Int,
        val primaryColor: String,
        val secondaryColor: String
    ) {
        fun getName(): String = Resources.getString(nameResId)
        fun getDescription(): String = Resources.getString(descriptionResId)
    }

    companion object {
        /**
         * The scan color sets
         */
        val SCAN_COLOR_SETS = listOf(
            ScanColorSet(
                R.string.scan_color_set_blue_red,
                R.string.scan_color_set_blue_red_desc,
                "#0000FF",
                "#FF0000"
            ),
            ScanColorSet(
                R.string.scan_color_set_green_yellow,
                R.string.scan_color_set_green_yellow_desc,
                "#00FF00",
                "#FFFF00"
            ),
            ScanColorSet(
                R.string.scan_color_set_purple_orange,
                R.string.scan_color_set_purple_orange_desc,
                "#800080",
                "#FFA500"
            ),
            ScanColorSet(
                R.string.scan_color_set_black_white,
                R.string.scan_color_set_black_white_desc,
                "#000000",
                "#FFFFFF"
            ),
            ScanColorSet(
                R.string.scan_color_set_red_blue,
                R.string.scan_color_set_red_blue_desc,
                "#FF0000",
                "#0000FF"
            ),
            ScanColorSet(
                R.string.scan_color_set_yellow_green,
                R.string.scan_color_set_yellow_green_desc,
                "#FFFF00",
                "#00FF00"
            ),
            ScanColorSet(
                R.string.scan_color_set_orange_purple,
                R.string.scan_color_set_orange_purple_desc,
                "#FFA500",
                "#800080"
            ),
            ScanColorSet(
                R.string.scan_color_set_white_black,
                R.string.scan_color_set_white_black_desc,
                "#FFFFFF",
                "#000000"
            )
        )

        /**
         * Get the scan color set by name
         * @param name The name of the color set
         */
        fun getScanColorSetByName(name: String): ScanColorSet {
            SCAN_COLOR_SETS.forEach {
                if (it.getName() == name) {
                    return it
                }
            }
            return SCAN_COLOR_SETS[0]
        }

        /**
         * Get scan color set from preferences
         */
        fun getScanColorSetFromPreferences(context: Context): ScanColorSet {
            val preferenceManager = PreferenceManager(context)
            val scanColorSetName =
                preferenceManager.getStringValue(PreferenceManager.Keys.PREFERENCE_KEY_SCAN_COLOR_SET)
            return getScanColorSetByName(scanColorSetName)
        }

        /**
         * Set scan color set to preferences
         * @param context The context of the caller
         * @param scanColorSetName The name of the color set
         */
        fun setScanColorSetToPreferences(context: Context, scanColorSetName: String) {
            val preferenceManager = PreferenceManager(context)
            preferenceManager.setStringValue(
                PreferenceManager.Keys.PREFERENCE_KEY_SCAN_COLOR_SET,
                scanColorSetName
            )
        }
    }
}