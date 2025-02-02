package com.enaboapps.switchify.service.utils

import android.util.Log
import android.view.accessibility.AccessibilityWindowInfo
import com.enaboapps.switchify.service.scanning.ScanMethod

object KeyboardBridge {
    var isKeyboardVisible = false

    // Track last scan type to go back to it after keyboard is dismissed
    private var lastScanType: String = ScanMethod.getType()

    fun updateKeyboardState(windows: List<AccessibilityWindowInfo>) {
        val keyboardWindow = windows.firstOrNull { window ->
            window.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD
        }
        if (keyboardWindow != null) {
            if (!isKeyboardVisible) {
                lastScanType = ScanMethod.getType()
                ScanMethod.setType(ScanMethod.MethodType.ITEM_SCAN)
            }

            isKeyboardVisible = true
        } else {
            if (isKeyboardVisible) {
                // Go back to last scan type
                ScanMethod.setType(lastScanType)
            }

            isKeyboardVisible = false
        }
        Log.d(
            "KeyboardBridge",
            "isKeyboardVisible: $isKeyboardVisible window count: ${windows.size}"
        )
    }
}