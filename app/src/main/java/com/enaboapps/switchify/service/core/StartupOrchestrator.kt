package com.enaboapps.switchify.service.core

import android.accessibilityservice.AccessibilityService
import com.enaboapps.switchify.service.keyboard.KeyboardManager
import com.enaboapps.switchify.service.keyboard.KeyboardNodesPolicy
import com.enaboapps.switchify.service.techniques.nodes.NodeExaminer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class StartupOrchestrator(
    private val service: AccessibilityService,
    private val serviceScope: CoroutineScope
) {
    private companion object {
        private const val STARTUP_EXAM_DELAY_MS = 100L

        // The first AccessibilityEvent for an appearing IME often fires while
        // the keyboard is still animating up: keys aren't fully laid out,
        // bounds are stale, and the scan tree gets built from an incomplete
        // snapshot. Schedule one extra examination this long after the
        // keyboard appears so we get a guaranteed post-settle snapshot even
        // if Android doesn't emit another event. Mirrors
        // [com.enaboapps.switchify.service.keyboard.KeyboardManager.BYPASS_UPDATE_DELAY_MS]
        // — same root cause (PR #1508), same value.
        private const val KEYBOARD_SETTLE_DELAY_MS = 250L
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
            serviceScope.launch {
                // Re-examine the accessibility tree shortly after the keyboard
                // first appears. Android's initial IME AccessibilityEvent
                // often fires mid-animation when the key layout isn't settled
                // yet, leaving the scan tree built from a stale snapshot. By
                // running a second examination after the IME has stabilised
                // we guarantee a correct post-settle snapshot — the standard
                // KeyboardNodesPolicy.shouldDropAsStale filter handles edge
                // cases (keyboard dismissed during the window, bounds changed
                // mid-flight).
                KeyboardManager.keyboardState
                    .map { it.isVisible }
                    .distinctUntilChanged()
                    .filter { it }
                    .collect {
                        delay(KEYBOARD_SETTLE_DELAY_MS)
                        processAccessibilityEvent()
                    }
            }
        }
    }
}