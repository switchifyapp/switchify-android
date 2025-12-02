package com.enaboapps.switchify.service.utils

import android.util.Log
import android.view.accessibility.AccessibilityWindowInfo
import com.enaboapps.switchify.service.keyboard.KeyboardManager
import com.enaboapps.switchify.service.scanning.ScanSettings

/**
 * Stateless bridge for detecting keyboard visibility from accessibility windows.
 * Does not maintain state - delegates to KeyboardManager as single source of truth.
 */
object KeyboardBridge {
    /**
     * Detects keyboard visibility from accessibility windows and notifies KeyboardManager.
     * KeyboardManager maintains the actual state.
     */
    fun updateKeyboardState(windows: List<AccessibilityWindowInfo>, scanSettings: ScanSettings) {
        val isVisible = detectKeyboardVisibility(windows)
        KeyboardManager.onKeyboardStateChanged(isVisible, scanSettings)
        Log.d(
            "KeyboardBridge",
            "Keyboard detected: $isVisible, window count: ${windows.size}"
        )
    }

    /**
     * Pure detection function - checks if keyboard window is present.
     */
    private fun detectKeyboardVisibility(windows: List<AccessibilityWindowInfo>): Boolean {
        return windows.any { window ->
            window.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD
        }
    }
}