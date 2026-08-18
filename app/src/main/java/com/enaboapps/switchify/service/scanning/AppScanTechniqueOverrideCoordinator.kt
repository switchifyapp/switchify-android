package com.enaboapps.switchify.service.scanning

import com.enaboapps.switchify.service.remotebridge.SwitchifyRemoteLauncher
import com.enaboapps.switchify.service.techniques.AccessTechnique

internal fun interface AppScanTechniquePolicy {
    fun techniqueFor(packageName: String): String?
}

internal object DefaultAppScanTechniquePolicy : AppScanTechniquePolicy {
    override fun techniqueFor(packageName: String): String? =
        when (packageName) {
            SwitchifyRemoteLauncher.REMOTE_PACKAGE -> AccessTechnique.Technique.ITEM_SCAN
            else -> null
        }
}

internal class AppScanTechniqueOverrideCoordinator(
    private val controller: ScanModeController,
    private val policy: AppScanTechniquePolicy
) {
    private var foregroundPackage: String? = null
    private var session: TemporaryScanModeSession? = null

    fun onForegroundApplicationChanged(packageName: String?) {
        if (packageName == null || packageName == foregroundPackage) return
        session?.close()
        session = null
        foregroundPackage = packageName
        val targetTechnique = policy.techniqueFor(packageName) ?: return
        session = TemporaryScanModeSession(controller, targetTechnique).also { it.start() }
    }

    fun refreshForegroundOverride() {
        val packageName = foregroundPackage ?: return
        val targetTechnique = policy.techniqueFor(packageName) ?: return
        if (controller.isTemporaryTechniqueActive() &&
            controller.currentTechnique() == targetTechnique
        ) return
        session?.close()
        session = TemporaryScanModeSession(controller, targetTechnique).also { it.start() }
    }

    fun clear() {
        session?.close()
        session = null
        foregroundPackage = null
    }
}
