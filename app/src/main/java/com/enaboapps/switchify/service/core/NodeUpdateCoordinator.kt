package com.enaboapps.switchify.service.core

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityWindowInfo
import com.enaboapps.switchify.service.scanning.ScanSettings
import com.enaboapps.switchify.service.scanning.ScanningManager
import com.enaboapps.switchify.service.techniques.nodes.NodeExaminer
import com.enaboapps.switchify.service.utils.KeyboardBridge
class NodeUpdateCoordinator(
    private val service: AccessibilityService,
    private val scanSettings: ScanSettings,
    private val scanningManager: ScanningManager
) {

    suspend fun processAccessibilityUpdate() {
        val windows = service.windows
        scanningManager.updateForegroundApplication(findForegroundApplicationPackage(windows))
        // KeyboardBridge first so KeyboardManager.keyboardState is current
        // before NodeExaminer reads it to pick the keyboard vs. active-window root.
        KeyboardBridge.updateKeyboardState(windows, scanSettings)
        NodeExaminer.examineAccessibilityTree(
            service.rootInActiveWindow,
            windows,
            service
        )
    }

    private fun findForegroundApplicationPackage(
        windows: List<AccessibilityWindowInfo>
    ): String? = windows
        .asSequence()
        .filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
        .sortedWith(
            compareByDescending<AccessibilityWindowInfo> { it.isActive }
                .thenByDescending { it.isFocused }
                .thenByDescending { it.layer }
        )
        .mapNotNull { it.root?.packageName?.toString() }
        .firstOrNull()
}
