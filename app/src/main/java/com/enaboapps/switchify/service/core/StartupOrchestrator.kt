package com.enaboapps.switchify.service.core

import android.accessibilityservice.AccessibilityService
import com.enaboapps.switchify.service.keyboard.KeyboardManager
import com.enaboapps.switchify.service.keyboard.KeyboardNodesPolicy
import com.enaboapps.switchify.service.techniques.nodes.NodeExaminer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class StartupOrchestrator(
    private val service: AccessibilityService,
    private val serviceScope: CoroutineScope
) {
    private companion object {
        private const val STARTUP_EXAM_DELAY_MS = 100L
    }

    private val keyboardNodesPolicy = KeyboardNodesPolicy()

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
                // Combine keyboard state with the node batches so the scanner
                // only sees nodes whose captured bounds match the current IME.
                // Drops the initial empty batch and any leftover from a
                // previous keyboard layout (e.g. when swapping IMEs).
                combine(
                    KeyboardManager.keyboardState,
                    NodeExaminer.keyboardNodesState
                ) { state, nodes -> state to nodes }
                    .filter { (state, nodes) ->
                        !keyboardNodesPolicy.shouldDropAsStale(nodes, state)
                    }
                    .map { (_, nodes) -> nodes.nodes }
                    .collect { scanningManager.updateKeyboardNodes(it) }
            }
        }
    }
}