package com.enaboapps.switchify.service.core

import android.accessibilityservice.AccessibilityService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.enaboapps.switchify.service.utils.QuickAppsManager
import com.enaboapps.switchify.service.techniques.nodes.NodeExaminer
import com.enaboapps.switchify.service.scanning.ScanningManager

class StartupOrchestrator(
    private val service: AccessibilityService,
    private val serviceScope: CoroutineScope
) {
    private companion object {
        private const val STARTUP_EXAM_DELAY_MS = 100L
        private const val QUICK_APPS_PRELOAD_DELAY_MS = 50L
    }

    suspend fun executeStartupTasks(processAccessibilityEvent: suspend () -> Unit) {
        // Initial accessibility tree examination
        processAccessibilityEvent()
        delay(STARTUP_EXAM_DELAY_MS)

        // Preload quick apps for faster menu access
        QuickAppsManager(service).preloadApps { /* Cache warmed up */ }
        delay(QUICK_APPS_PRELOAD_DELAY_MS)

        // Update the SystemNodeScanner and KeyboardScanner with the current layout info
        ServiceCore.getScanningManager()?.let { scanningManager ->
            serviceScope.launch {
                NodeExaminer.getActionableNodesFlow().collect { nodes ->
                    scanningManager.updateActionableNodes(nodes)
                }
            }
            serviceScope.launch {
                NodeExaminer.getKeyboardNodesFlow().collect { nodes ->
                    scanningManager.updateKeyboardNodes(nodes)
                }
            }
        }
    }
}