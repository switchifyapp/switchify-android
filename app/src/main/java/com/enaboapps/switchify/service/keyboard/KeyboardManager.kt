package com.enaboapps.switchify.service.keyboard

import android.os.Handler
import android.os.Looper
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
 * KeyboardManager is the single source of truth for keyboard state and behavior.
 *
 * It receives keyboard visibility updates from KeyboardBridge and maintains all
 * keyboard-related state including:
 * - Keyboard visibility (isKeyboardVisible)
 * - Escape state (isEscapedFromKeyboard)
 * - Direct selection settings (isDirectlySelectKeyboardKeysEnabled)
 *
 * No other components should track keyboard visibility state independently.
 * Business logic for keyboard decisions is delegated to KeyboardSelectionPolicy.
 */
object KeyboardManager {
    private const val TAG = "KeyboardManager"
    private const val BYPASS_UPDATE_DELAY_MS = 250L

    // State tracking - single source of truth
    private var isKeyboardVisible = false
    private var isEscapedFromKeyboard = false
    private var isDirectlySelectKeyboardKeysEnabled = false

    // Policy for keyboard selection decisions
    private val selectionPolicy = KeyboardSelectionPolicy()

    // Listener for state changes
    private var keyboardStateListener: KeyboardStateListener? = null

    // Handler for delayed operations
    private val mainHandler = Handler(Looper.getMainLooper())

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
        Log.d(
            TAG,
            "Keyboard state changed: visible=$visible, wasVisible=$isKeyboardVisible, isEscaped=$isEscapedFromKeyboard"
        )

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

        isDirectlySelectKeyboardKeysEnabled = scanSettings.isDirectlySelectKeyboardKeysEnabled()
        updateBypassState()

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

        // Update bypass state after a delay to ensure keyboard state has stabilized
        mainHandler.postDelayed({
            updateBypassState()
        }, BYPASS_UPDATE_DELAY_MS)
    }

    /**
     * Gets the current keyboard state.
     */
    private fun getCurrentState(): KeyboardState {
        return KeyboardState(
            isVisible = isKeyboardVisible,
            isEscaped = isEscapedFromKeyboard,
            isDirectSelectEnabled = isDirectlySelectKeyboardKeysEnabled
        )
    }

    /**
     * Update bypass state using stored settings.
     * Delegates decision logic to KeyboardSelectionPolicy.
     */
    private fun updateBypassState() {
        val bypass = selectionPolicy.shouldBypassAutoSelect(getCurrentState())
        SelectionHandler.setBypassAutoSelect(bypass)
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
     * Delegates decision to KeyboardSelectionPolicy.
     */
    fun shouldShowScanKeyboardMenuItem(): Boolean {
        return selectionPolicy.shouldShowScanKeyboardMenuItem(getCurrentState())
    }

    /**
     * Check if keyboard escape prompt should be shown instead of menu prompt.
     * Delegates decision to KeyboardSelectionPolicy.
     */
    fun shouldShowKeyboardEscapePrompt(): Boolean {
        return selectionPolicy.shouldShowEscapePrompt(getCurrentState())
    }

    /**
     * Check if cycle break should be enabled in scanning.
     * Delegates decision to KeyboardSelectionPolicy.
     */
    fun shouldEnableCycleBreak(): Boolean {
        return selectionPolicy.shouldEnableCycleBreak(getCurrentState())
    }

    /**
     * Get current keyboard state for debugging.
     */
    fun getState(): String {
        return "KeyboardManager(visible=$isKeyboardVisible, escaped=$isEscapedFromKeyboard)"
    }
}