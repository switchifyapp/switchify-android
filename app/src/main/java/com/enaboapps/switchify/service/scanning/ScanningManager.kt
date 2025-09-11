package com.enaboapps.switchify.service.scanning

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.enaboapps.switchify.service.actions.GlobalActionManager
import com.enaboapps.switchify.service.core.ServiceCore
import com.enaboapps.switchify.service.core.SwitchifyAccessibilityService
import com.enaboapps.switchify.service.core.Tasks
import com.enaboapps.switchify.service.gestures.GestureManager
import com.enaboapps.switchify.service.menu.MenuManager
import com.enaboapps.switchify.service.selection.SelectionHandler
import com.enaboapps.switchify.service.techniques.AccessTechnique
import com.enaboapps.switchify.service.techniques.AccessTechniqueInterface
import com.enaboapps.switchify.service.techniques.ActiveAccessTechnique
import com.enaboapps.switchify.service.techniques.nodes.Node
import com.enaboapps.switchify.service.techniques.nodes.scanners.NodeScannerUI
import com.enaboapps.switchify.service.window.SwitchifyAccessibilityWindow
import com.enaboapps.switchify.switches.SwitchAction

/**
 * ScanningManager is responsible for managing the scanning process in the application.
 * It coordinates different scanning methods (point scan, radar, item scan) and handles user actions.
 *
 * @property accessibilityService The accessibility service instance used for system-level actions.
 */
class ScanningManager(
    private val accessibilityService: SwitchifyAccessibilityService
) {
    companion object {
        private const val TAG = "ScanningManager"
        private const val SCAN_METHOD_CHANGE_TIMEOUT_MS = 1000L
    }

    private var isAcceptingActions = true

    // Active scan method manager
    private val activeScanMethod = ActiveAccessTechnique(accessibilityService)

    private var moveRepeatManager: MoveRepeatManager? = MoveRepeatManager(accessibilityService)

    // Scan settings
    private val scanSettings = ScanSettings(accessibilityService)

    /**
     * Provides the current active scanning state method on the current scanning method.
     */
    private val currentScanMethod: AccessTechniqueInterface
        get() = activeScanMethod.currentAccessTechnique

    /**
     * Provides access to the active scan method manager for head control integration
     */
    fun getActiveScanMethod(): ActiveAccessTechnique = activeScanMethod

    /**
     * Provides direct access to head control manager if available
     */
    fun getHeadControlManagerOrNull() = try {
        activeScanMethod.getHeadControlManagerInstance()
    } catch (_: Exception) { null }

    init {
        activeScanMethod.setOnScanningStartCallback {
            if (scanSettings.getAutomaticallyStartScanAfterSelection()) {
                currentScanMethod.startAutoScanning()
            }
        }
    }

    /**
     * Sets up the scanning manager, initializing necessary components.
     */
    fun setup() {
        SwitchifyAccessibilityWindow.instance.setup(accessibilityService)
        SwitchifyAccessibilityWindow.instance.show()
        MenuManager.getInstance().setup(this, accessibilityService)
    }

    /**
     * Updates the nodes in the SystemNodeScanner with the current layout information.
     *
     * @param nodes List of Node instances representing the current screen layout.
     */
    fun updateActionableNodes(nodes: List<Node>) {
        activeScanMethod.updateActionableNodes(nodes)
    }

    /**
     * Updates the nodes in the KeyboardScanner with the current layout information.
     *
     * @param nodes List of Node instances representing the current screen layout.
     */
    fun updateKeyboardNodes(nodes: List<Node>) {
        activeScanMethod.updateKeyboardNodes(nodes)
    }

    fun setPointScanType() {
        setType(AccessTechnique.Technique.POINT_SCAN)
    }

    /**
     * Sets the scanning method to radar type.
     */
    fun setRadarType() {
        setType(AccessTechnique.Technique.RADAR)
    }

    /**
     * Sets the scanning method to item scan type and starts the timeout to revert to point scan.
     */
    fun setItemScanType() {
        setType(AccessTechnique.Technique.ITEM_SCAN)
        SelectionHandler.setStartScanningAction { activeScanMethod.currentAccessTechnique.startAutoScanning() }
        activeScanMethod.getNodeScanner().startTimeoutToRevertToCursor()
    }


    fun setHeadControlType() {
        setType(AccessTechnique.Technique.HEAD_CONTROL)
    }

    /**
     * Sets the scanning method to menu type.
     */
    fun setMenuType() {
        setType(AccessTechnique.Technique.MENU)
    }

    /**
     * Helper method to set the scanning method type and perform necessary cleanup.
     *
     * @param type The AccessTechnique.Technique to set. Must be a valid type.
     */
    private fun setType(type: String) {
        startAcceptingActionsTimeout()
        AccessTechnique.setCurrentTechnique(type)
        NodeScannerUI.instance.hideAll()
        activeScanMethod.resetNodeScanner()
    }

    /**
     * Starts the timeout to pause accepting actions.
     */
    private fun startAcceptingActionsTimeout() {
        isAcceptingActions = false
        Handler(Looper.getMainLooper()).postDelayed({
            isAcceptingActions = true
        }, SCAN_METHOD_CHANGE_TIMEOUT_MS)
    }

    /**
     * Performs the selection action for the current scanning state.
     */
    fun select() {
        if (!isAcceptingActions) return
        currentScanMethod.performSelectionAction()
    }

    fun checkOngoingTasks(): Boolean {
        return Tasks.getInstance().checkOngoingTasks()
    }

    /**
     * Performs the specified action based on the SwitchAction type.
     */
    fun performAction(action: SwitchAction) {
        if (!isAcceptingActions) return

        if (GestureManager.instance.performGestureLockAction()) return

        when (action.id) {
            SwitchAction.ACTION_SELECT -> select()
            SwitchAction.ACTION_STOP_SCANNING -> currentScanMethod.stopScanningAndReset()
            SwitchAction.ACTION_CHANGE_SCANNING_DIRECTION -> currentScanMethod.swapScanDirection()
            SwitchAction.ACTION_MOVE_TO_NEXT_ITEM -> currentScanMethod.stepScanningForward()
            SwitchAction.ACTION_MOVE_TO_PREVIOUS_ITEM -> currentScanMethod.stepScanningBackward()
            SwitchAction.ACTION_TOGGLE_GESTURE_LOCK -> GestureManager.instance
                .toggleGestureLock()

            SwitchAction.ACTION_SYS_HOME -> GlobalActionManager.goHome()
            SwitchAction.ACTION_SYS_BACK -> GlobalActionManager.goBack()
            SwitchAction.ACTION_SYS_RECENTS -> GlobalActionManager.openRecents()
            SwitchAction.ACTION_SYS_QUICK_SETTINGS -> GlobalActionManager.openQuickSettings()
            SwitchAction.ACTION_SYS_NOTIFICATIONS -> GlobalActionManager.openNotifications()
            SwitchAction.ACTION_SYS_LOCK_SCREEN -> GlobalActionManager.lockScreen()
            SwitchAction.ACTION_SYS_HEADSET_HOOK -> GlobalActionManager.toggleMediaPlayback()
            SwitchAction.ACTION_PAUSE -> {
                Log.d(TAG, "ACTION_PAUSE triggered")
                val pauseManager = ServiceCore.getPauseManager()
                Log.d(TAG, "Starting pause via PauseManager")
                pauseManager.startPause()
            }

            else -> {} // Do nothing for ACTION_NONE
        }
    }

    /**
     * Pauses the scanning process for the current scanning state.
     */
    fun pauseScanning() {
        if (isAcceptingActions) currentScanMethod.pauseAutoScanning()
    }

    /**
     * Resumes the scanning process for the current scanning state.
     */
    fun resumeScanning() {
        if (isAcceptingActions) currentScanMethod.resumeAutoScanning()
    }

    /**
     * Starts the move repeat functionality.
     *
     * @param action The action to be performed when the move repeat is triggered.
     * @return True if the move repeat was started, false otherwise.
     */
    fun startMoveRepeat(action: SwitchAction): Boolean {
        val allowRepeat = scanSettings.isMoveRepeatEnabled()
        if (allowRepeat) {
            when (action.id) {
                SwitchAction.ACTION_MOVE_TO_NEXT_ITEM -> {
                    moveRepeatManager?.setNextAction {
                        performAction(SwitchAction(SwitchAction.ACTION_MOVE_TO_NEXT_ITEM))
                    }
                    return moveRepeatManager?.start() ?: false
                }

                SwitchAction.ACTION_MOVE_TO_PREVIOUS_ITEM -> {
                    moveRepeatManager?.setPreviousAction {
                        performAction(SwitchAction(SwitchAction.ACTION_MOVE_TO_PREVIOUS_ITEM))
                    }
                    return moveRepeatManager?.start() ?: false
                }

            }
        }
        return false
    }

    /**
     * Stops the move repeat functionality.
     *
     * @return True if the move repeat was stopped, false otherwise.
     */
    fun stopMoveRepeat(): Boolean {
        if (scanSettings.isMoveRepeatEnabled()) {
            moveRepeatManager?.stop()
            return true
        }
        return false
    }

    /**
     * Resets the scanning manager, stopping all scanning processes and cleaning up resources.
     */
    fun reset() {
        pauseScanning()
        activeScanMethod.cleanupAll()
        MenuManager.getInstance().closeMenuHierarchy()
        moveRepeatManager = null
    }

    /**
     * Shuts down the scanning manager, stopping all processes and cleaning up resources.
     */
    fun shutdown() {
        pauseScanning()
        activeScanMethod.destroy()
        moveRepeatManager = null
    }
}
