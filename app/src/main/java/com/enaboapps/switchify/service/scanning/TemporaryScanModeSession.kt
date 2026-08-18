package com.enaboapps.switchify.service.scanning

import com.enaboapps.switchify.service.techniques.AccessTechnique

interface ScanModeController {
    fun currentTechnique(): String
    fun preferredTechnique(): String
    fun isTemporaryTechniqueActive(): Boolean
    fun setTemporaryTechnique(technique: String)
    fun restoreTemporaryTechnique(technique: String)
}

class ScanningManagerScanModeController(
    private val scanningManager: ScanningManager
) : ScanModeController {
    override fun currentTechnique(): String = AccessTechnique.getCurrentTechnique()
    override fun preferredTechnique(): String =
        if (currentTechnique() == AccessTechnique.Technique.MENU) {
            AccessTechnique.getStoredTechnique() ?: AccessTechnique.Technique.ITEM_SCAN
        } else {
            currentTechnique()
        }
    override fun isTemporaryTechniqueActive(): Boolean =
        AccessTechnique.isTemporaryTechniqueActive()
    override fun setTemporaryTechnique(technique: String) =
        scanningManager.setTemporaryScanType(technique)
    override fun restoreTemporaryTechnique(technique: String) =
        scanningManager.restoreTemporaryScanType(technique)
}

class TemporaryScanModeSession internal constructor(
    private val controller: ScanModeController,
    private val targetTechnique: String
) {
    constructor(
        scanningManager: ScanningManager,
        targetTechnique: String
    ) : this(ScanningManagerScanModeController(scanningManager), targetTechnique)

    private var previousTechnique: String? = null
    private var started = false
    private var overrideApplied = false

    fun start() {
        if (started) return
        started = true
        previousTechnique = controller.preferredTechnique()
        if (controller.currentTechnique() != targetTechnique) {
            controller.setTemporaryTechnique(targetTechnique)
            overrideApplied = true
        }
    }

    fun close() {
        if (!started) return
        started = false
        val previous = previousTechnique ?: return
        previousTechnique = null
        if (!overrideApplied) return
        overrideApplied = false
        if (!controller.isTemporaryTechniqueActive() || controller.currentTechnique() != targetTechnique) return
        if (previous in supportedTechniques) controller.restoreTemporaryTechnique(previous)
    }

    private companion object {
        val supportedTechniques = setOf(
            AccessTechnique.Technique.POINT_SCAN,
            AccessTechnique.Technique.RADAR,
            AccessTechnique.Technique.ITEM_SCAN
        )
    }
}
