package com.enaboapps.switchify.service.core

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityWindowInfo
import com.enaboapps.switchify.service.scanning.ScanSettings
import com.enaboapps.switchify.service.scanning.ScanningManager
import com.enaboapps.switchify.service.techniques.nodes.NodeExaminer
import com.enaboapps.switchify.service.utils.KeyboardBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
class NodeUpdateCoordinator(
    private val service: AccessibilityService,
    private val scanSettings: ScanSettings,
    private val scanningManager: ScanningManager
) {

    suspend fun processAccessibilityUpdate(isCurrent: () -> Boolean) {
        if (!isCurrent()) return
        val windows = service.windows
        if (!isCurrent()) return
        val foregroundPackage = findForegroundApplicationPackage(windows)
        withContext(Dispatchers.Main.immediate) {
            if (isCurrent()) scanningManager.updateForegroundApplication(foregroundPackage)
        }
        if (!isCurrent()) return
        // KeyboardBridge first so KeyboardManager.keyboardState is current
        // before NodeExaminer reads it to pick the keyboard vs. active-window root.
        KeyboardBridge.updateKeyboardState(windows, scanSettings)
        NodeExaminer.examineAccessibilityTree(
            service.rootInActiveWindow,
            windows,
            service,
            isCurrent
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
