package com.enaboapps.switchify.service.core

import com.enaboapps.switchify.service.scanning.ScanSettings
import com.enaboapps.switchify.service.techniques.AccessTechnique

class TechniqueEnforcer(
    private val scanSettings: ScanSettings
) {

    fun enforceDirectionalCompatibility() {
        if (scanSettings.isDirectionalScanMode()) {
            val currentTechnique = AccessTechnique.getCurrentTechnique()
            if (currentTechnique == AccessTechnique.Technique.POINT_SCAN ||
                currentTechnique == AccessTechnique.Technique.RADAR
            ) {
                AccessTechnique.setCurrentTechnique(AccessTechnique.Technique.DIRECT_CONTROL)
            }
        }
    }
}