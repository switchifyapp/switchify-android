package com.enaboapps.switchify.service.keyboard

import android.util.Log
import com.enaboapps.switchify.service.scanning.ScanSettings
import com.enaboapps.switchify.service.selection.SelectionHandler

/**
 * Listener interface for keyboard state changes.
 */
interface KeyboardStateListener {
    fun onKeyboardStateChanged(isKeyboardVisible: Boolean, isEscapedFromKeyboard: Boolean)
}

/**
 * KeyboardManager manages keyboard state and behavior centrally.
 * It receives updates from KeyboardBridge and coordinates keyboard-related functionality.
 */
object KeyboardManager {
    private const val TAG = "KeyboardManager"
    
    // State tracking
    private var isKeyboardVisible = false
    private var isEscapedFromKeyboard = false
    
    // Listener for state changes
    private var keyboardStateListener: KeyboardStateListener? = null
    
    /**
     * Initialize the KeyboardManager.
     */
    fun initialize() {
        Log.d(TAG, "KeyboardManager initialized")
    }
    
    /**
     * Set the keyboard state listener.
     */
    fun setKeyboardStateListener(listener: KeyboardStateListener) {
        keyboardStateListener = listener
    }
    
    /**
     * Remove the keyboard state listener.
     */
    fun removeKeyboardStateListener() {
        keyboardStateListener = null
    }
    
    /**
     * Notify the listener of state changes.
     */
    private fun notifyStateChanged() {
        keyboardStateListener?.onKeyboardStateChanged(isKeyboardVisible, isEscapedFromKeyboard)
    }
    
    /**
     * Called when keyboard state changes from KeyboardBridge.
     */
    fun onKeyboardStateChanged(visible: Boolean, scanSettings: ScanSettings) {
        Log.d(TAG, "Keyboard state changed: visible=$visible, wasVisible=$isKeyboardVisible, isEscaped=$isEscapedFromKeyboard")
        
        val wasVisible = isKeyboardVisible
        val wasEscaped = isEscapedFromKeyboard
        isKeyboardVisible = visible
        
        var stateChanged = false
        
        when {
            // Keyboard appeared
            visible && !wasVisible -> {
                Log.d(TAG, "Keyboard appeared - switching to keyboard scanning")
                isEscapedFromKeyboard = false
                stateChanged = true
            }
            
            // Keyboard disappeared
            !visible && wasVisible -> {
                Log.d(TAG, "Keyboard disappeared - clearing escape state")
                isEscapedFromKeyboard = false
                stateChanged = true
            }
            
            // Keyboard state unchanged - no action needed
            else -> {
                Log.d(TAG, "Keyboard state unchanged")
            }
        }

        val bypass = isKeyboardVisible && scanSettings.isDirectlySelectKeyboardKeysEnabled() && !isEscapedFromKeyboard()
        SelectionHandler.setBypassAutoSelect(bypass)

        // Only notify if state actually changed
        if (stateChanged || wasEscaped != isEscapedFromKeyboard) {
            notifyStateChanged()
        }
    }
    
    /**
     * Escape from keyboard scanning mode.
     * This is called when user wants to exit keyboard scanning and access the menu.
     */
    fun escapeKeyboard() {
        if (!isKeyboardVisible) {
            Log.w(TAG, "Cannot escape - keyboard is not visible")
            return
        }
        
        if (isEscapedFromKeyboard) {
            Log.d(TAG, "Already escaped from keyboard")
            return
        }
        
        Log.d(TAG, "Escaping from keyboard scanning")
        isEscapedFromKeyboard = true

        SelectionHandler.setBypassAutoSelect(false)
    }
    
    /**
     * Return to keyboard scanning mode.
     * This is called when user selects "Scan Keyboard" from the menu.
     */
    fun returnToKeyboard() {
        if (!isKeyboardVisible) {
            Log.w(TAG, "Cannot return to keyboard - keyboard is not visible")
            return
        }
        
        if (!isEscapedFromKeyboard) {
            Log.d(TAG, "Already in keyboard scanning mode")
            return
        }
        
        Log.d(TAG, "Returning to keyboard scanning")
        isEscapedFromKeyboard = false
    }
    
    /**
     * Check if keyboard is currently visible.
     */
    fun isKeyboardVisible(): Boolean {
        return isKeyboardVisible
    }
    
    /**
     * Check if user has escaped from keyboard scanning.
     */
    fun isEscapedFromKeyboard(): Boolean {
        return isEscapedFromKeyboard
    }
    
    /**
     * Check if "Scan Keyboard" menu item should be shown.
     * Only show when keyboard is visible but user has escaped.
     */
    fun shouldShowScanKeyboardMenuItem(): Boolean {
        return isKeyboardVisible && isEscapedFromKeyboard
    }
    
    /**
     * Check if keyboard escape prompt should be shown instead of menu prompt.
     */
    fun shouldShowKeyboardEscapePrompt(): Boolean {
        return isKeyboardVisible && !isEscapedFromKeyboard
    }
    
    /**
     * Get current keyboard state for debugging.
     */
    fun getState(): String {
        return "KeyboardManager(visible=$isKeyboardVisible, escaped=$isEscapedFromKeyboard)"
    }
}