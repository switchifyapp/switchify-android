package com.enaboapps.switchify.service.selection

import android.content.Context
import android.util.Log
import com.enaboapps.switchify.service.gestures.GestureManager
import com.enaboapps.switchify.service.gestures.GesturePoint
import com.enaboapps.switchify.service.gestures.GestureStateManager
import com.enaboapps.switchify.service.gestures.visuals.GestureVisualManager
import com.enaboapps.switchify.service.keyboard.KeyboardManager
import com.enaboapps.switchify.service.menu.MenuManager
import com.enaboapps.switchify.service.scanning.ScanSettings
import com.enaboapps.switchify.service.techniques.AccessTechnique
import com.enaboapps.switchify.utils.LogEvent
import com.enaboapps.switchify.utils.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * This class handles the selection process.
 */
object SelectionHandler {
    private const val TAG = "SelectionHandler"

    private var selectAction: (() -> Unit)? = null
    private var startScanningAction: (() -> Unit)? = null
    private var gestureVisualManager: GestureVisualManager? = null

    // State management now handled by GestureStateManager

    private lateinit var scanSettings: ScanSettings

    /**
     * Checks if SelectionHandler is initialized and logs warning if not
     * @param action Description of the action being attempted
     * @return true if initialized, false otherwise
     */
    private fun ensureInitializedOrWarn(action: String): Boolean {
        return if (!::scanSettings.isInitialized) {
            Log.w(TAG, "SelectionHandler not initialized, skipping $action")
            false
        } else {
            true
        }
    }

    /**
     * Initializes the selection handler.
     * This method is intended to be called once, ideally during application startup.
     * It uses the application context to avoid memory leaks.
     *
     * @param appContext The application context used for initialization.
     */
    fun init(appContext: Context) {
        // Use application context to avoid leaking activity or other contexts
        scanSettings = ScanSettings(appContext)
        gestureVisualManager = GestureVisualManager(appContext.applicationContext)
    }

    /**
     * Sets the selection action to be executed.
     *
     * @param newAction The action to be executed as part of the selection process.
     */
    fun setSelectAction(newAction: () -> Unit) {
        selectAction = newAction
    }

    /**
     * Sets the start scanning action to be executed.
     *
     * @param newAction The action to be executed as part of the selection process.
     */
    fun setStartScanningAction(newAction: () -> Unit) {
        startScanningAction = newAction
    }

    /**
     * Performs the selection action based on the current settings and state.
     */
    fun performSelectionAction() {
        if (!ensureInitializedOrWarn("selection action")) return

        // Check if a linear gesture is in progress
        if (GestureManager.instance.isPerformingLinearGesture()) {
            MenuManager.getInstance().openCustomGestureConfirmationMenu()
            return
        }

        GestureStateManager.setMethodTypeForStartScanning(
            AccessTechnique.getCurrentTechnique()
        )

        // If bypass auto-select is enabled, perform the selection action and return
        if (KeyboardManager.shouldBypassAutoSelect()) {
            if (selectAction == null) return
            selectAction?.invoke()
            performStartScanningAction()
            return
        }

        // If auto-select is in progress, cancel it and open the main menu
        if (GestureStateManager.isAutoSelectInProgress()) {
            MenuManager.getInstance().openMainMenu()
            GestureStateManager.cancelAutoSelect()
            gestureVisualManager?.hideCircle()
            return
        }

        Log.d(TAG, "performSelectionAction()")

        // Check if auto-select is enabled
        val autoSelectEnabled = scanSettings.isAutoSelectEnabled()
        // If auto-select is enabled, start the auto-select process
        if (autoSelectEnabled) {
            if (selectAction != null) {
                val delayTime = scanSettings.getAutoSelectDelay()
                val point = GesturePoint.getPoint()

                // Show visual feedback
                gestureVisualManager?.showCountdownCircle(
                    point.x.toInt(),
                    point.y.toInt(),
                    delayTime
                )

                // Start auto-select with unified state manager
                GestureStateManager.startAutoSelect(delayTime) {
                    selectAction?.invoke()
                    performStartScanningAction()
                }
            }
        } else { // If auto-select is disabled, open the main menu
            MenuManager.getInstance().openMainMenu()
        }
    }

    /**
     * Performs the start scanning action if it is enabled.
     */
    fun performStartScanningAction() {
        if (!ensureInitializedOrWarn("start scanning action")) return

        CoroutineScope(Dispatchers.Main).launch {
            delay(300)
            if (scanSettings.getAutomaticallyStartScanAfterSelection()) {
                if (GestureStateManager.getMethodTypeForStartScanning() == AccessTechnique.getCurrentTechnique()) {
                    startScanningAction?.invoke()
                }
            }
        }
    }

    /**
     * Checks if the auto-select process is currently in progress.
     *
     * @return True if the auto-select process is in progress, false otherwise.
     */
    fun isAutoSelectInProgress(): Boolean = GestureStateManager.isAutoSelectInProgress()

    /**
     * Cleans up the selection handler.
     */
    fun cleanup() {
        Log.d(TAG, "cleanup() called")
        Log.d(TAG, "Stack trace:", Throwable())

        gestureVisualManager?.hideCircle()

        // Don't reset gesture state if a linear gesture is in progress
        if (!GestureStateManager.isGestureInProgress()) {
            Log.d(TAG, "No active gesture - resetting all state")
            GestureStateManager.resetAllState()
        } else {
            Log.d(
                TAG,
                "Active gesture detected - preserving gesture state, only resetting selection state"
            )
            // Only reset auto-select and selection-specific state, preserve gesture execution state
            GestureStateManager.cancelAutoSelect()
            GestureStateManager.setMethodTypeForStartScanning(null)
            GestureStateManager.setActiveVisualFeedback(false)
        }

        selectAction = null
        startScanningAction = null
    }
}
