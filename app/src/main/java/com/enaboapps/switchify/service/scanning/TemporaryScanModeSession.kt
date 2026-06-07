package com.enaboapps.switchify.service.scanning

import com.enaboapps.switchify.service.techniques.AccessTechnique

interface ScanModeController {
    fun currentTechnique(): String
    fun setPointScanType()
    fun setRadarType()
    fun setItemScanType()
}

class ScanningManagerScanModeController(
    private val scanningManager: ScanningManager
) : ScanModeController {
    override fun currentTechnique(): String = AccessTechnique.getCurrentTechnique()
    override fun setPointScanType() = scanningManager.setPointScanType()
    override fun setRadarType() = scanningManager.setRadarType()
    override fun setItemScanType() = scanningManager.setItemScanType()
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

    fun start() {
        if (started) return
        started = true
        val current = controller.currentTechnique()
        previousTechnique = current
        if (current != targetTechnique) {
            controller.setItemScanType()
        }
    }

    fun close() {
        if (!started) return
        started = false
        val previous = previousTechnique ?: return
        previousTechnique = null
        if (controller.currentTechnique() != targetTechnique || previous == targetTechnique) return
        when (previous) {
            AccessTechnique.Technique.POINT_SCAN -> controller.setPointScanType()
            AccessTechnique.Technique.RADAR -> controller.setRadarType()
            AccessTechnique.Technique.ITEM_SCAN -> Unit
            else -> controller.setPointScanType()
        }
    }
}
