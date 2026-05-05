package com.enaboapps.switchify.service.utils

import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityWindowInfo
import com.enaboapps.switchify.service.keyboard.KeyboardManager
import com.enaboapps.switchify.service.scanning.ScanSettings

/**
 * Stateless bridge for detecting keyboard visibility and bounds from
 * accessibility windows. Does not maintain state — delegates to
 * KeyboardManager as the single source of truth.
 */
object KeyboardBridge {
    /**
     * Detects keyboard visibility and bounds from accessibility windows
     * and notifies KeyboardManager. KeyboardManager maintains the actual state.
     */
    fun updateKeyboardState(windows: List<AccessibilityWindowInfo>, scanSettings: ScanSettings) {
        val imeWindow = windows.firstOrNull { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }
        val isVisible = imeWindow != null
        val bounds = imeWindow?.let { window ->
            Rect().also { window.getBoundsInScreen(it) }
        }
        KeyboardManager.onKeyboardStateChanged(isVisible, bounds, scanSettings)
        Log.d(
            "KeyboardBridge",
            "Keyboard detected: $isVisible, bounds: $bounds, window count: ${windows.size}"
        )
    }
}
