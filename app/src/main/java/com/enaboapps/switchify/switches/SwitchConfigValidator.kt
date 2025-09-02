package com.enaboapps.switchify.switches

import android.content.Context
import android.util.Log
import com.enaboapps.switchify.service.scanning.ScanMode
import com.enaboapps.switchify.service.scanning.ScanSettings

/**
 * Utility class to validate switch configuration based on scan mode requirements
 */
class SwitchConfigValidator(private val context: Context) {

    private val scanSettings = ScanSettings(context)
    private val switchEventStore = SwitchEventStore.getInstance()

    /**
     * Check if the current switch configuration is valid for the selected scan mode
     * @return true if configuration is valid, false otherwise
     */
    fun isConfigurationValid(): Boolean {
        // Defensive check: ensure switch store is initialized
        if (!switchEventStore.isInitialized()) {
            Log.w(
                "SwitchConfigValidator",
                "Switch store not initialized when checking configuration validity. " +
                        "This may cause incorrect validation results. Use SwitchEventStore.initializeAsync() first."
            )
        }

        // Get all configured switch actions
        val configuredActions = getConfiguredActions()
        val allowed = SupportedActionsPolicy.supportedActionIds(context) + SwitchAction.ACTION_NONE
        if (!(configuredActions subtract allowed).isEmpty()) {
            return false
        }

        return when {
            scanSettings.isAutoScanMode() -> isValidForAutoScan(configuredActions)
            scanSettings.isManualScanMode() -> isValidForManualScan(configuredActions)
            scanSettings.isDirectionalScanMode() -> isValidForDirectionalScan(configuredActions)
            else -> false
        }
    }

    /**
     * Check if configuration is valid for auto scan mode
     * Auto scan requires SELECT action
     */
    private fun isValidForAutoScan(configuredActions: Set<Int>): Boolean {
        return configuredActions.contains(SwitchAction.ACTION_SELECT)
    }

    /**
     * Check if configuration is valid for manual scan mode
     * Manual scan requires NEXT, PREVIOUS, and SELECT actions
     */
    private fun isValidForManualScan(configuredActions: Set<Int>): Boolean {
        return configuredActions.containsAll(
            setOf(
                SwitchAction.ACTION_SELECT,
                SwitchAction.ACTION_MOVE_TO_NEXT_ITEM,
                SwitchAction.ACTION_MOVE_TO_PREVIOUS_ITEM
            )
        )
    }

    /**
     * Check if configuration is valid for directional scan mode
     * Directional scan requires UP, DOWN, LEFT, RIGHT, and SELECT actions
     */
    private fun isValidForDirectionalScan(configuredActions: Set<Int>): Boolean {
        return configuredActions.containsAll(
            setOf(
                SwitchAction.ACTION_SELECT,
                SwitchAction.ACTION_MOVE_UP,
                SwitchAction.ACTION_MOVE_DOWN,
                SwitchAction.ACTION_MOVE_LEFT,
                SwitchAction.ACTION_MOVE_RIGHT
            )
        )
    }

    /**
     * Get all switch actions that are currently configured
     * @return Set of action IDs that are configured
     */
    private fun getConfiguredActions(): Set<Int> {
        val configuredActions = mutableSetOf<Int>()

        // Get all switches and their actions
        val switchEvents = switchEventStore.getSwitchEvents()
        for (switchEvent in switchEvents) {
            // Add press action
            configuredActions.add(switchEvent.pressAction.id)

            // Add hold actions
            switchEvent.holdActions.forEach { holdAction ->
                configuredActions.add(holdAction.id)
            }
        }

        return configuredActions
    }

    /**
     * Get missing actions for the current scan mode
     * @return Set of missing action IDs
     */
    fun getMissingActions(): Set<Int> {
        // Defensive check: ensure switch store is initialized
        if (!switchEventStore.isInitialized()) {
            Log.w(
                "SwitchConfigValidator",
                "Switch store not initialized when getting missing actions. " +
                        "This may cause incorrect results. Use SwitchEventStore.initializeAsync() first."
            )
        }

        val configuredActions = getConfiguredActions()
        val requiredActions = when {
            scanSettings.isAutoScanMode() -> setOf(SwitchAction.ACTION_SELECT)
            scanSettings.isManualScanMode() -> setOf(
                SwitchAction.ACTION_SELECT,
                SwitchAction.ACTION_MOVE_TO_NEXT_ITEM,
                SwitchAction.ACTION_MOVE_TO_PREVIOUS_ITEM
            )

            scanSettings.isDirectionalScanMode() -> setOf(
                SwitchAction.ACTION_SELECT,
                SwitchAction.ACTION_MOVE_UP,
                SwitchAction.ACTION_MOVE_DOWN,
                SwitchAction.ACTION_MOVE_LEFT,
                SwitchAction.ACTION_MOVE_RIGHT
            )

            else -> emptySet()
        }

        return requiredActions - configuredActions
    }

    /**
     * Get current scan mode name for display
     */
    fun getCurrentScanModeName(): String {
        return when {
            scanSettings.isAutoScanMode() -> ScanMode(ScanMode.Modes.MODE_AUTO).getModeName()
            scanSettings.isManualScanMode() -> ScanMode(ScanMode.Modes.MODE_MANUAL).getModeName()
            scanSettings.isDirectionalScanMode() -> ScanMode(ScanMode.Modes.MODE_DIRECTIONAL).getModeName()
            else -> "Unknown"
        }
    }
}
