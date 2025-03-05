package com.enaboapps.switchify.service.utils

import android.util.Log
import android.view.accessibility.AccessibilityWindowInfo
import com.enaboapps.switchify.service.scanning.ScanSettings
import com.enaboapps.switchify.service.selection.SelectionHandler

interface KeyboardListener {
    fun onKeyboardStateChanged(isKeyboardVisible: Boolean)
}

object KeyboardBridge {
    var isKeyboardVisible = false
        private set

    private var keyboardListener: KeyboardListener? = null

    fun setKeyboardListener(listener: KeyboardListener) {
        keyboardListener = listener
    }

    fun removeKeyboardListener() {
        keyboardListener = null
    }

    private fun notifyKeyboardStateChanged(isKeyboardVisible: Boolean) {
        if (this.isKeyboardVisible != isKeyboardVisible) {
            this.isKeyboardVisible = isKeyboardVisible
            keyboardListener?.onKeyboardStateChanged(isKeyboardVisible)
        }
    }

    fun updateKeyboardState(windows: List<AccessibilityWindowInfo>, scanSettings: ScanSettings) {
        val keyboardWindow = windows.firstOrNull { window ->
            window.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD
        }
        if (keyboardWindow != null) {
            if (scanSettings.isDirectlySelectKeyboardKeysEnabled()) {
                SelectionHandler.setBypassAutoSelect(true)
            }

            notifyKeyboardStateChanged(true)
        } else {
            SelectionHandler.setBypassAutoSelect(false)

            notifyKeyboardStateChanged(false)
        }
        Log.d(
            "KeyboardBridge",
            "isKeyboardVisible: $isKeyboardVisible window count: ${windows.size}"
        )
    }
}