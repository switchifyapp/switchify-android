package com.enaboapps.switchify.service.core

import android.accessibilityservice.AccessibilityService
import com.enaboapps.switchify.service.scanning.ScanSettings
import com.enaboapps.switchify.service.techniques.nodes.NodeExaminer
import com.enaboapps.switchify.service.utils.KeyboardBridge
class NodeUpdateCoordinator(
    private val service: AccessibilityService,
    private val scanSettings: ScanSettings
) {

    suspend fun processAccessibilityUpdate() {
        // KeyboardBridge first so KeyboardManager.keyboardState is current
        // before NodeExaminer reads it to pick the keyboard vs. active-window root.
        KeyboardBridge.updateKeyboardState(service.windows, scanSettings)
        NodeExaminer.examineAccessibilityTree(
            service.rootInActiveWindow,
            service.windows,
            service
        )
    }
}