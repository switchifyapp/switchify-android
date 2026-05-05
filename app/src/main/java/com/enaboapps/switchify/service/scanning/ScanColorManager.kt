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
         * The scan color sets. Hex values are designer-tuned (Tailwind palette),
         * not raw web primaries.
         */
        val SCAN_COLOR_SETS = listOf(
            ScanColorSet(
                R.string.scan_color_set_default,
                R.string.scan_color_set_default_desc,
                "#2563EB",
                "#F59E0B"
            ),
            ScanColorSet(
                R.string.scan_color_set_high_contrast,
                R.string.scan_color_set_high_contrast_desc,
                "#000000",
                "#FFFFFF"
            ),
            ScanColorSet(
                R.string.scan_color_set_deuteranopia,
                R.string.scan_color_set_deuteranopia_desc,
                "#1E40AF",
                "#FCD34D"
            ),
            ScanColorSet(
                R.string.scan_color_set_tritanopia,
                R.string.scan_color_set_tritanopia_desc,
                "#DC2626",
                "#0E7490"
            ),
            ScanColorSet(
                R.string.scan_color_set_monochrome,
                R.string.scan_color_set_monochrome_desc,
                "#1F2937",
                "#6B7280"
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
