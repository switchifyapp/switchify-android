package com.enaboapps.switchify.service.core

import android.accessibilityservice.AccessibilityService
import com.enaboapps.switchify.service.techniques.nodes.NodeExaminer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class StartupOrchestrator(
    private val service: AccessibilityService,
    private val serviceScope: CoroutineScope
) {
    private companion object {
        private const val STARTUP_EXAM_DELAY_MS = 100L
    }

    suspend fun executeStartupTasks(processAccessibilityEvent: suspend () -> Unit) {
        // Initial accessibility tree examination
        processAccessibilityEvent()
        delay(STARTUP_EXAM_DELAY_MS)

        // Update the SystemNodeScanner and KeyboardScanner with the current layout info
        ServiceCore.getScanningManager()?.let { scanningManager ->
            serviceScope.launch {
                NodeExaminer.getActionableNodesFlow().collect { nodes ->
                    scanningManager.updateActionableNodes(nodes)
                }
            }
            serviceScope.launch {
                NodeExaminer.keyboardNodesState.collect { state ->
                    scanningManager.updateKeyboardNodes(state.nodes)
                }
            }
        }
    }
}