package com.enaboapps.switchify.service.scanning

import com.enaboapps.switchify.service.techniques.AccessTechnique

/**
 * Temporarily switches item scan to point scan while the screen has nothing to
 * scan, and restores item scan as soon as actionable nodes reappear. The stored
 * technique preference is never touched, so a lock screen or full-screen video
 * cannot permanently change how the user scans every other app.
 */
class EmptyNodesFallback internal constructor(
    private val controller: ScanModeController
) {
    private var session: TemporaryScanModeSession? = null

    val isActive: Boolean
        get() = session != null

    fun enter(): Boolean {
        if (session != null) return true
        if (controller.currentTechnique() != AccessTechnique.Technique.ITEM_SCAN) return false
        if (controller.isTemporaryTechniqueActive()) return false
        val candidate = TemporaryScanModeSession(controller, AccessTechnique.Technique.POINT_SCAN)
        if (!candidate.start()) return false
        session = candidate
        return true
    }

    fun onNodesAvailable() {
        val active = session ?: return
        session = null
        active.close()
    }

    fun drop() {
        session = null
    }
}
