package com.enaboapps.switchify.service.utils

import android.util.Log
import android.view.accessibility.AccessibilityWindowInfo
import com.enaboapps.switchify.service.keyboard.KeyboardManager
import com.enaboapps.switchify.service.scanning.ScanSettings
import com.enaboapps.switchify.service.selection.SelectionHandler



object KeyboardBridge {
    var isKeyboardVisible = false
        private set

    

    fun updateKeyboardState(windows: List<AccessibilityWindowInfo>, scanSettings: ScanSettings) {
        val keyboardWindow = windows.firstOrNull { window ->
            window.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD
        }
        if (keyboardWindow != null) {
            isKeyboardVisible = true
            KeyboardManager.onKeyboardStateChanged(true, scanSettings)
        } else {
            isKeyboardVisible = false
            KeyboardManager.onKeyboardStateChanged(false, scanSettings)
        }
        Log.d(
            "KeyboardBridge",
            "isKeyboardVisible: $isKeyboardVisible window count: ${windows.size}"
        )
    }
}