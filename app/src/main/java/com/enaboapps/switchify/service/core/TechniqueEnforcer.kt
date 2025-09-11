package com.enaboapps.switchify.service.core

import android.util.Log
import com.enaboapps.switchify.service.scanning.ScanSettings
import com.enaboapps.switchify.service.techniques.AccessTechnique

class TechniqueEnforcer(
    private val scanSettings: ScanSettings
) {
    private companion object {
        private const val TAG = "TechniqueEnforcer"
    }

    /**
     * Enforces technique compatibility based on scan mode.
     *
     * Compatibility Rules:
     * - Point Scan & Radar: Only work with Linear/Auto scanning modes
     * - Head Control: Works with Directional scanning mode
     * - Item Scan: Works with all scanning modes (universal fallback)
     */
    fun enforceCompatibility() {
        val currentTechnique = AccessTechnique.getCurrentTechnique()
        val isDirectionalMode = scanSettings.isDirectionalScanMode()

        val shouldSwitch = when {
            // Point Scan and Radar don't work in Directional mode
            isDirectionalMode && (currentTechnique == AccessTechnique.Technique.POINT_SCAN ||
                    currentTechnique == AccessTechnique.Technique.RADAR) -> {
                Log.d(
                    TAG,
                    "Switching from $currentTechnique to Item Scan - incompatible with directional mode"
                )
                true
            }


            else -> false
        }

        if (shouldSwitch) {
            // Item Scan works with all modes, so use it as universal fallback
            AccessTechnique.setCurrentTechnique(AccessTechnique.Technique.ITEM_SCAN)
        }
    }

}