package com.enaboapps.switchify.service.keyboard

import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.enaboapps.switchify.service.scanning.CycleBreakListener
import com.enaboapps.switchify.service.scanning.ScanSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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
 *
 * Implements CycleBreakListener to handle cycle break events from scanners,
 * providing loose coupling between scanning and keyboard management.
 */
object KeyboardManager : CycleBreakListener {
    private const val TAG = "KeyboardManager"

    /**
     * Delay (in milliseconds) before updating bypass state when returning to keyboard.
     *
     * This delay is necessary to ensure the keyboard state has stabilized before
     * re-enabling auto-select bypass. Without this delay, the bypass state can be
     * updated before the keyboard UI is fully ready, causing selection issues.
     *
     * Added in PR #1508 to fix issue #1507.
     * See: https://github.com/enaboapps/switchify/issues/1507
     */
    private const val BYPASS_UPDATE_DELAY_MS = 250L
    private const val SWITCH_INPUT_SUPPRESSION_DURATION_MS = 1000L

    // State tracking - single source of truth
    private var isKeyboardVisible = false
    private var isEscapedFromKeyboard = false
    private var isDirectlySelectKeyboardKeysEnabled = false
    private var keyboardBounds: Rect? = null
    private var keyboardWindowTarget: KeyboardWindowTarget? = null

    // uptimeMillis at which auto-select bypass becomes available again. Used to
    // hold off bypass for BYPASS_UPDATE_DELAY_MS after returnToKeyboard so the
    // keyboard UI can stabilise before direct selection re-engages.
    private var bypassUnlockAt: Long = 0L
    private val switchInputGuard = KeyboardHideSwitchInputGuard(
        SWITCH_INPUT_SUPPRESSION_DURATION_MS,
        SystemClock::uptimeMillis
    )

    // State machine for explicit state transitions
    private val stateMachine = KeyboardStateMachine()

    // Reactive state flow for UI components
    private val _keyboardState = MutableStateFlow(KeyboardState())

    /**
     * Observable keyboard state flow for reactive UI updates.
     *
     * UI components can collect this flow to automatically update when
     * keyboard state changes. This is the preferred way for Compose
     * components to observe keyboard state.
     *
     * Example (Compose):
     * ```
     * val keyboardState by KeyboardManager.keyboardState.collectAsState()
     * if (keyboardState.shouldShowEscapePrompt) {
     *     // Show prompt
     * }
     * ```
     *
     * Example (Non-Compose):
     * ```
     * KeyboardManager.keyboardState
     *     .onEach { state -> updateUI(state) }
     *     .launchIn(scope)
     * ```
     */
    val keyboardState: StateFlow<KeyboardState> = _keyboardState.asStateFlow()

    // Policy for keyboard selection decisions
    private val selectionPolicy = KeyboardSelectionPolicy()

    // Listener for state changes (legacy - prefer StateFlow)
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
     * Update the reactive state flow with current state.
     * This should be called whenever keyboard state changes.
     */
    private fun updateState() {
        _keyboardState.value = getCurrentState()
    }

    /**
     * Called when keyboard state changes from KeyboardBridge.
     */
    fun onKeyboardStateChanged(
        visible: Boolean,
        bounds: Rect?,
        scanSettings: ScanSettings,
        windowTarget: KeyboardWindowTarget? = null
    ) {
        Log.d(
            TAG,
            "Keyboard state changed: visible=$visible, wasVisible=$isKeyboardVisible, isEscaped=$isEscapedFromKeyboard"
        )

        val wasVisible = isKeyboardVisible
        val wasEscaped = isEscapedFromKeyboard
        val previousBounds = keyboardBounds
        val previousWindowTarget = keyboardWindowTarget
        isKeyboardVisible = visible
        keyboardBounds = bounds
        keyboardWindowTarget = windowTarget
        switchInputGuard.onKeyboardVisibilityChanged(visible)

        // Use state machine for state transitions
        val event = if (visible) KeyboardEvent.KeyboardShown else KeyboardEvent.KeyboardHidden
        val newState = stateMachine.transition(event)

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

        // Only notify if state actually changed
        if (
            stateChanged ||
            wasEscaped != isEscapedFromKeyboard ||
            previousBounds != keyboardBounds ||
            previousWindowTarget != keyboardWindowTarget
        ) {
            updateState()
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

        // Use state machine to validate transition
        val newState = stateMachine.transition(KeyboardEvent.EscapeRequested)
        if (newState == null) {
            Log.w(TAG, "Cannot escape in current state: ${stateMachine.getCurrentState()}")
            return
        }

        Log.d(TAG, "Escaping from keyboard scanning")
        isEscapedFromKeyboard = true

        updateState()
    }

    /**
     * Return to keyboard scanning mode.
     * This is called when user selects "Scan Keyboard" from the menu.
     *
     * Note: Auto-select bypass is gated until BYPASS_UPDATE_DELAY_MS after
     * this call so the keyboard UI can stabilise before direct selection
     * re-engages. See constant documentation for details.
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

        // Use state machine to validate transition
        val newState = stateMachine.transition(KeyboardEvent.ReturnRequested)
        if (newState == null) {
            Log.w(TAG, "Cannot return to keyboard in current state: ${stateMachine.getCurrentState()}")
            return
        }

        Log.d(TAG, "Returning to keyboard scanning")
        isEscapedFromKeyboard = false
        bypassUnlockAt = SystemClock.uptimeMillis() + BYPASS_UPDATE_DELAY_MS

        updateState()

        // Delay completes the state machine return transition once the keyboard
        // UI has stabilised. Bypass timing is handled by bypassUnlockAt above.
        mainHandler.postDelayed({
            if (!isKeyboardVisible || isEscapedFromKeyboard) {
                return@postDelayed
            }
            stateMachine.transition(KeyboardEvent.ReturnCompleted)
        }, BYPASS_UPDATE_DELAY_MS)
    }

    /**
     * Gets the current keyboard state.
     */
    private fun getCurrentState(): KeyboardState {
        return KeyboardState(
            isVisible = isKeyboardVisible,
            isEscaped = isEscapedFromKeyboard,
            isDirectSelectEnabled = isDirectlySelectKeyboardKeysEnabled,
            keyboardBounds = keyboardBounds,
            keyboardWindowTarget = keyboardWindowTarget
        )
    }

    /**
     * Whether auto-select should be bypassed for direct keyboard key selection.
     * Pulled at the moment of selection — see [KeyboardSelectionPolicy.shouldBypassAutoSelect]
     * for the policy. After [returnToKeyboard], bypass is gated for
     * [BYPASS_UPDATE_DELAY_MS] so the keyboard UI can stabilise.
     */
    fun shouldBypassAutoSelect(): Boolean {
        if (!selectionPolicy.shouldBypassAutoSelect(getCurrentState())) return false
        return SystemClock.uptimeMillis() >= bypassUnlockAt
    }

    /**
     * Check if keyboard is currently visible.
     */
    fun isKeyboardVisible(): Boolean {
        return isKeyboardVisible
    }

    fun shouldSuppressSwitchInput(): Boolean {
        return switchInputGuard.shouldSuppressSwitchInput()
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

    /**
     * Handles cycle break events from scanners.
     *
     * This implementation of CycleBreakListener allows KeyboardManager to respond
     * to cycle break selections without the scanner needing to know about keyboard
     * management logic. This provides loose coupling between components.
     *
     * If the keyboard escape prompt should be shown (keyboard visible and not escaped),
     * this method will escape from keyboard scanning.
     */
    override fun onCycleBreak() {
        if (shouldShowKeyboardEscapePrompt()) {
            Log.d(TAG, "Cycle break: Escaping from keyboard scanning")
            escapeKeyboard()
        } else {
            Log.d(TAG, "Cycle break: Keyboard not active, no action taken")
        }
    }
}
