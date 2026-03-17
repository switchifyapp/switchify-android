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
        NodeExaminer.examineAccessibilityTree(
            service.rootInActiveWindow,
            service.windows,
            service
        )
        KeyboardBridge.updateKeyboardState(service.windows, scanSettings)
    }
}