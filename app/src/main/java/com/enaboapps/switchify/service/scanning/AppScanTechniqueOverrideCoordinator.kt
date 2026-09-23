package com.enaboapps.switchify.service.scanning

import com.enaboapps.switchify.service.techniques.AccessTechnique
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicLong

internal fun interface AppScanTechniquePolicy {
    fun techniqueFor(packageName: String): String?
}

internal class AppScanTechniqueOverrideCoordinator(
    private val controller: ScanModeController,
    private val policy: AppScanTechniquePolicy,
    private val menuActions: AppScanTechniqueOverrideMenuActions = AppScanTechniqueOverrideMenuActions(),
    private val uiDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate
) {
    private val requestedGeneration = AtomicLong()
    private var foregroundPackage: String? = null
    private var previousTechnique: String? = null
    private var visitTechnique: String? = null
    private var manualOverride = false
    private var changingForeground = false

    suspend fun onForegroundApplicationChanged(packageName: String?) {
        if (packageName == null) return
        val generation = requestedGeneration.incrementAndGet()
        withContext(uiDispatcher) {
            if (generation != requestedGeneration.get() || packageName == foregroundPackage) return@withContext
            changingForeground = true
            try {
                if (previousTechnique != null || policy.techniqueFor(packageName) != null) {
                    menuActions.closeIfOpen { generation == requestedGeneration.get() }
                }
                if (generation != requestedGeneration.get()) return@withContext
                restoreDefault()
                foregroundPackage = packageName
                manualOverride = false
            } finally {
                changingForeground = false
            }
            refreshForegroundOverride()
        }
    }

    fun selectForCurrentVisit(technique: String): Boolean {
        val packageName = foregroundPackage ?: return false
        if (policy.techniqueFor(packageName) == null) return false
        if (previousTechnique == null) previousTechnique = controller.preferredTechnique()
        manualOverride = true
        visitTechnique = technique
        applyTechnique(technique)
        return true
    }

    fun refreshForegroundOverride() {
        if (changingForeground) return
        val packageName = foregroundPackage ?: return
        val rule = policy.techniqueFor(packageName)
        if (rule == null) {
            restoreDefault()
            return
        }
        if (controller.currentTechnique() == AccessTechnique.Technique.MENU) return
        if (previousTechnique == null) previousTechnique = controller.preferredTechnique()
        if (!manualOverride) visitTechnique = rule
        visitTechnique?.let(::applyTechnique)
    }

    fun clear() {
        requestedGeneration.incrementAndGet()
        restoreDefault()
        foregroundPackage = null
        previousTechnique = null
        visitTechnique = null
        manualOverride = false
        changingForeground = false
    }

    private fun applyTechnique(technique: String) {
        if (controller.currentTechnique() != technique || !controller.isTemporaryTechniqueActive()) {
            controller.setTemporaryTechnique(technique)
        }
    }

    private fun restoreDefault() {
        val previous = previousTechnique ?: return
        if (controller.currentTechnique() == AccessTechnique.Technique.MENU) return
        if (!controller.isTemporaryTechniqueActive()) controller.setTemporaryTechnique(previous)
        controller.restoreTemporaryTechnique(previous)
        previousTechnique = null
        visitTechnique = null
        manualOverride = false
    }
}
