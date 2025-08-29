package com.enaboapps.switchify.service.core

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import kotlinx.coroutines.CoroutineScope
import com.enaboapps.switchify.service.techniques.nodes.NodeExaminer
import com.enaboapps.switchify.service.utils.KeyboardBridge
import com.enaboapps.switchify.service.scanning.ScanSettings

class NodeUpdateCoordinator(
    private val service: AccessibilityService,
    private val serviceScope: CoroutineScope,
    private val scanSettings: ScanSettings
) {

    suspend fun processAccessibilityUpdate() {
        NodeExaminer.examineAccessibilityTree(
            service.rootInActiveWindow,
            service.windows,
            service,
            serviceScope
        )
        KeyboardBridge.updateKeyboardState(service.windows, scanSettings)
    }
}