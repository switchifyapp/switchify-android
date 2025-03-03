package com.enaboapps.switchify.service.scanning

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.enaboapps.switchify.service.SwitchifyAccessibilityService
import com.enaboapps.switchify.service.core.GlobalActionManager
import com.enaboapps.switchify.service.custom.actions.ActionPerformer
import com.enaboapps.switchify.service.gestures.AutoScrollManager
import com.enaboapps.switchify.service.gestures.GestureManager
import com.enaboapps.switchify.service.menu.MenuManager
import com.enaboapps.switchify.service.methods.nodes.Node
import com.enaboapps.switchify.service.methods.nodes.scanners.NodeScannerUI
import com.enaboapps.switchify.service.selection.SelectionHandler
import com.enaboapps.switchify.service.window.SwitchifyAccessibilityWindow
import com.enaboapps.switchify.switches.SwitchAction

/**
 * ScanningManager is responsible for managing the scanning process in the application.
 * It coordinates different scanning methods (cursor, radar, item scan) and handles user actions.
 *
 * @property accessibilityService The accessibility service instance used for system-level actions.
 * @property context The application context.
 */
class ScanningManager(
    private val accessibilityService: SwitchifyAccessibilityService,
    val context: Context
) {
    companion object {
        private const val TAG = "ScanningManager"
        private const val SCAN_METHOD_CHANGE_TIMEOUT_MS = 1000L
    }

    private var isAcceptingActions = true

    // Active scan method manager
    private val activeScanMethod = ActiveScanMethod(context)

    private var moveRepeatManager: MoveRepeatManager? = MoveRepeatManager(context)

    // Scan settings
    private val scanSettings = ScanSettings(context)

    /**
     * Provides the current active scanning state method on the current scanning method.
     */
    private val currentScanMethod: ScanMethodBase
        get() = activeScanMethod.currentMethod

    init {
        activeScanMethod.setOnScanningStartCallback {
            if (scanSettings.getAutomaticallyStartScanAfterSelection()) {
                currentScanMethod.startScanning()
            }
        }
    }

    /**
     * Sets up the scanning manager, initializing necessary components.
     */
    fun setup() {
        SwitchifyAccessibilityWindow.instance.setup(context)
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

    /**
     * Sets the scanning method to cursor type.
     */
    fun setCursorType() {
        setType(ScanMethod.MethodType.CURSOR)
    }

    /**
     * Sets the scanning method to radar type.
     */
    fun setRadarType() {
        setType(ScanMethod.MethodType.RADAR)
    }

    /**
     * Sets the scanning method to item scan type and starts the timeout to revert to cursor.
     */
    fun setItemScanType() {
        setType(ScanMethod.MethodType.ITEM_SCAN)
        SelectionHandler.setStartScanningAction { activeScanMethod.currentMethod.startScanning() }
        activeScanMethod.getNodeScanner().startTimeoutToRevertToCursor()
    }

    /**
     * Sets the scanning method to menu type.
     */
    fun setMenuType() {
        startAcceptingActionsTimeout()
        ScanMethod.isInMenu = true
        NodeScannerUI.instance.hideAll()
    }

    /**
     * Helper method to set the scanning method type and perform necessary cleanup.
     *
     * @param type The ScanMethod.MethodType to set. Must be a valid type.
     */
    private fun setType(type: String) {
        startAcceptingActionsTimeout()
        ScanMethod.setType(type)
        ScanMethod.isInMenu = false
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

    /**
     * Performs the specified action based on the SwitchAction type.
     */
    fun performAction(action: SwitchAction) {
        if (GestureManager.getInstance().performGestureLockAction()) {
            return
        }

        if (AutoScrollManager.getInstance().stopAutoScroll()) return

        if (!isAcceptingActions) return

        when (action.id) {
            SwitchAction.ACTION_SELECT -> select()
            SwitchAction.ACTION_STOP_SCANNING -> currentScanMethod.stopScanning()
            SwitchAction.ACTION_CHANGE_SCANNING_DIRECTION -> currentScanMethod.swapScanDirection()
            SwitchAction.ACTION_MOVE_TO_NEXT_ITEM -> currentScanMethod.stepForward()
            SwitchAction.ACTION_MOVE_TO_PREVIOUS_ITEM -> currentScanMethod.stepBackward()
            SwitchAction.ACTION_TOGGLE_GESTURE_LOCK -> GestureManager.getInstance()
                .toggleGestureLock()

            SwitchAction.ACTION_SYS_HOME -> GlobalActionManager.goHome()
            SwitchAction.ACTION_SYS_BACK -> GlobalActionManager.goBack()
            SwitchAction.ACTION_SYS_RECENTS -> GlobalActionManager.openRecents()
            SwitchAction.ACTION_SYS_QUICK_SETTINGS -> GlobalActionManager.openQuickSettings()
            SwitchAction.ACTION_SYS_NOTIFICATIONS -> GlobalActionManager.openNotifications()
            SwitchAction.ACTION_SYS_LOCK_SCREEN -> GlobalActionManager.lockScreen()
            SwitchAction.ACTION_SYS_HEADSET_HOOK -> GlobalActionManager.toggleMediaPlayback()
            SwitchAction.ACTION_PERFORM_USER_ACTION -> {
                val actionPerformer = ActionPerformer(context)
                actionPerformer.performActionFromStore(action.extra?.myActionsId ?: "")
            }

            else -> {} // Do nothing for ACTION_NONE
        }
    }

    /**
     * Pauses the scanning process for the current scanning state.
     */
    fun pauseScanning() {
        currentScanMethod.pauseScanning()
    }

    /**
     * Resumes the scanning process for the current scanning state.
     */
    fun resumeScanning() {
        currentScanMethod.resumeScanning()
    }

    /**
     * Starts the move repeat functionality.
     *
     * @param action The action to be performed when the move repeat is triggered.
     * @return True if the move repeat was started, false otherwise.
     */
    fun startMoveRepeat(action: SwitchAction): Boolean {
        if (scanSettings.isMoveRepeatEnabled()) {
            if (action.id == SwitchAction.ACTION_MOVE_TO_NEXT_ITEM) {
                moveRepeatManager?.setNextAction {
                    performAction(SwitchAction(SwitchAction.ACTION_MOVE_TO_NEXT_ITEM))
                }
                return moveRepeatManager?.start() ?: false
            } else if (action.id == SwitchAction.ACTION_MOVE_TO_PREVIOUS_ITEM) {
                moveRepeatManager?.setPreviousAction {
                    performAction(SwitchAction(SwitchAction.ACTION_MOVE_TO_PREVIOUS_ITEM))
                }
                return moveRepeatManager?.start() ?: false
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
        activeScanMethod.cleanupAll()
        moveRepeatManager = null
    }
}