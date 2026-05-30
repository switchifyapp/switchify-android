package com.enaboapps.switchify.service.scanning.tree

import android.content.Context
import android.util.Log
import com.enaboapps.switchify.service.scanning.ScanNodeInterface
import com.enaboapps.switchify.service.scanning.ScanSettings
import com.enaboapps.switchify.service.scanning.ScanningScheduler
import com.enaboapps.switchify.service.techniques.AccessTechniqueInterface
import com.enaboapps.switchify.service.techniques.nodes.NodeSpeaker

interface ScanTreeCallback {
    fun onScanTreeCycleBreakStarted() = Unit
    fun onScanTreeCycleBreakSkipped() = Unit
    fun onScanTreeCycleBreakSelected() = Unit
    fun onSingleCycleCompleted(cycleNumber: Int) = Unit
    fun onScanTreeReset() = Unit
    fun onScanTreeStarted() = Unit
    fun onScanTreeStopped() = Unit
}

/**
 * This class represents the main scanning tree for switch access functionality.
 * It orchestrates the interactions between various components to manage the scanning process.
 *
 * @property context The application context.
 * @property stopScanningOnSelect Whether to stop scanning after a selection is made.
 * @property hasCycleBreak Whether to include a break between scanning cycles.
 */
class ScanTree(
    private val context: Context,
    private var stopScanningOnSelect: Boolean = false,
    private val hasCycleBreak: () -> Boolean = { false },
    private var callback: ScanTreeCallback? = null
) : AccessTechniqueInterface {

    companion object {
        private const val TAG = "ScanTree"
    }

    /** The settings for scanning behavior. */
    private val scanSettings = ScanSettings(context)

    /** The speed at which the scanning tree is traversed. */
    private var scanningSpeed = scanSettings.getScanRate()

    /** The list of ScanTreeItems that make up the scanning tree. */
    private val tree: MutableList<ScanTreeItem> = mutableListOf()

    /** The builder responsible for constructing the scanning tree. */
    private val builder = ScanTreeBuilder(context, scanSettings)

    /** The navigator responsible for traversing the scanning tree. */
    private lateinit var navigator: ScanTreeNavigator

    /** The selector responsible for handling selection actions. */
    private lateinit var selector: ScanTreeSelector

    /** The highlighter responsible for visual feedback during scanning. */
    private lateinit var highlighter: ScanTreeHighlighter

    /** The scheduler for automatic scanning. */
    private var scanningScheduler: ScanningScheduler? = null

    /** The flag to track if manual scanning is active. */
    private var isManualScanActive = false

    init {
        initializeComponents()
    }

    /**
     * Initializes the navigator, selector, and highlighter components.
     */
    private fun initializeComponents() {
        navigator = ScanTreeNavigator(tree, scanSettings, hasCycleBreak)
        selector = ScanTreeSelector(tree, navigator, scanSettings, stopScanningOnSelect)
        highlighter = ScanTreeHighlighter(tree, scanSettings)
    }

    /**
     * Sets the speed at which the scanning tree is traversed.
     *
     * @param scanningSpeed The new scanning speed (in milliseconds).
     */
    fun setSpeed(scanningSpeed: Long) {
        this.scanningSpeed = scanningSpeed
    }

    /**
     * Builds the scanning tree from a list of scan nodes.
     *
     * @param nodes The list of ScanNodeInterface objects to build the tree from.
     * @param itemThreshold The threshold for determining if a node is in the same item (in dp).
     */
    fun buildTree(nodes: List<ScanNodeInterface>, itemThreshold: Int = 40) {
        clearTree()
        tree.addAll(builder.buildTree(nodes, itemThreshold))
        initializeComponents() // Reinitialize components with the new tree
    }

    /**
     * Checks if the manual or directional scan setup is valid.
     *
     * @return True if the manual or directional scan setup is valid, false otherwise.
     */
    private fun checkManualScanSetup(): Boolean {
        if ((scanSettings.isManualScanMode() || scanSettings.isDirectionalScanMode()) && !isManualScanActive) {
            isManualScanActive = true
            highlighter.unhighlightAll()
            highlightCurrent()
            return true
        }
        return false
    }

    /**
     * Performs the selection action based on the current scanning state.
     * This method handles the main logic flow of the scanning process.
     */
    override fun performSelectionAction() {
        try {
            if (checkManualScanSetup()) {
                return
            }

            if (scanSettings.isAutoScanMode()) {
                setup()
            }

            if (scanningScheduler?.isScanning() == false && scanSettings.isAutoScanMode()) {
                startAutoScanning()
                Log.d(TAG, "Scanning started")
                return
            }

            unhighlightCurrent()

            if (handleEscape(true)) {
                return
            }

            if (navigator.isInCycleBreak) {
                callback?.onScanTreeCycleBreakSelected()
                stopScanningAndReset()
                Log.d(TAG, "onScanTreeCycleBreakSelected")
                return
            }

            val selectionMade = selector.performSelection()

            if (selectionMade && stopScanningOnSelect) {
                stopScanningAndReset()
            } else {
                pauseAutoScanning()
                resumeAutoScanning()
            }
            if (!selectionMade) {
                highlightCurrent()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error performing selection: ${e.message}")
        }
    }

    /**
     * Checks if the escape should be highlighted.
     * @param highlight Whether to highlight the escape.
     * @return True if the escape should be highlighted, false otherwise.
     */
    private fun highlightEscape(highlight: Boolean = true): Boolean {
        if (highlight) {
            highlighter.highlightEscape(
                navigator.currentTreeItem,
                navigator.currentGroup,
                navigator.currentColumn,
                navigator.isInTreeItem,
                !navigator.isScanningGroups
            )
        }
        return highlight
    }

    /**
     * Handles the escape logic for items and groups.
     * This method is called when an escape condition is met during scanning.
     * @param confirm Whether to confirm the escape action.
     * @return True if the escape was confirmed, false otherwise.
     */
    private fun handleEscape(confirm: Boolean = false): Boolean {
        var actionWasTaken = false
        if (navigator.handleEscape()) {
            highlighter.unhighlightEscape(
                navigator.currentTreeItem,
                navigator.currentGroup,
                navigator.currentColumn,
                navigator.isInTreeItem,
                !navigator.isScanningGroups
            )
            actionWasTaken = if (confirm) {
                navigator.confirmEscape()
            } else {
                navigator.denyEscape()
            }
        }
        if (actionWasTaken) {
            highlightCurrent()
        }
        return actionWasTaken
    }

    /**
     * Handles the auto scan cycle limit
     * @return True if the auto scan cycle limit has been reached, false otherwise.
     */
    private fun handleAutoScanCycleLimit(): Boolean {
        if (navigator.isAutoScanCycleLimitReached()) {
            stopScanningAndReset()
            return true
        } else {
            return false
        }
    }

    /**
     * Speak during the scan
     * Determines if the scan is a row or group and calls the appropriate function
     * Only called if item scan speech is enabled
     */
    private fun speakDuringScan() {
        if (scanSettings.isItemScanSpeechEnabled()) {
            println("Speaking during scan")
            val currentItem = navigator.getCurrentItem()
            val rowColumnEnabled = scanSettings.isRowColumnScanEnabled()
            val groupsEnabled = scanSettings.isGroupScanEnabled()
            val inGroup = navigator.isInGroup
            val inItem = navigator.isInTreeItem

            when {
                // Speak the node if row column is disabled
                !rowColumnEnabled -> {
                    navigator.getCurrentNode()?.let { node ->
                        NodeSpeaker.speakNode(node)
                    }
                }

                // Speak item if it's a single node
                currentItem.isSingleNode() -> {
                    currentItem.speakNode(null, navigator.currentColumn)
                }

                // Speak the node if row column is enabled but the item is not grouped
                groupsEnabled && !currentItem.isGrouped() && inItem -> {
                    currentItem.speakNode(null, navigator.currentColumn)
                }

                // Speak the node in the current group
                inGroup && groupsEnabled && currentItem.isGrouped() && inItem -> {
                    currentItem.speakNode(navigator.currentGroup, navigator.currentColumn)
                }

                // Speak the row
                !inItem && !inGroup -> {
                    currentItem.speakNodes(false)
                }

                // Speak the row if group scan is enabled but the item is not grouped
                groupsEnabled && !currentItem.isGrouped() && inItem -> {
                    currentItem.speakNodes(false)
                }

                // Speak the group
                !inGroup && groupsEnabled -> {
                    currentItem.speakGroup(navigator.currentGroup)
                }

                // Speak the node
                else -> {
                    currentItem.speakNode(null, navigator.currentColumn)
                }
            }
        }
    }

    /**
     * Handles cycle completion and break logic
     * @param wasInCycleBreak Whether we were in cycle break before movement
     * @return True if we should return early (e.g., when break is started)
     */
    private fun handleCycleCompletion(
        wasInCycleBreak: Boolean
    ): Boolean {
        if (navigator.hasCompletedCycle()) {
            Log.d(
                TAG,
                "Cycle is completed with navigator cycle break: ${navigator.isInCycleBreak}"
            )
            callback?.onSingleCycleCompleted(navigator.currentCycle)
            if (handleAutoScanCycleLimit()) {
                return true
            }
            if (navigator.isInCycleBreak) {
                callback?.onScanTreeCycleBreakStarted()
                return true
            }
        } else if (wasInCycleBreak) {
            navigator.skipCycleBreak()
            callback?.onScanTreeCycleBreakSkipped()
            highlightCurrent()
            return true
        }
        return false
    }

    /**
     * Steps through the scanning tree automatically.
     * This method is called by the scanning scheduler during automatic scanning.
     */
    private fun stepAutoScanning() {
        if (!handlePreMovement()) {
            val movementSuccessful = navigator.moveSelectionToNextOrPrevious()
            handlePostMovement(movementSuccessful)
        }
    }

    /**
     * Handles pre-movement logic.
     *
     * @return True if the movement was successful, false otherwise.
     */
    private fun handlePreMovement(): Boolean {
        unhighlightCurrent()

        if (handleAutoScanCycleLimit()) {
            return true
        }

        val escapeSuccessful = handleEscape()
        val cycleBreakSuccessful = handleCycleCompletion(navigator.isInCycleBreak)

        return escapeSuccessful || cycleBreakSuccessful
    }

    /**
     * Handles post-movement logic, such as highlighting and highlighting the escape.
     * @param movementSuccessful Whether the movement was successful.
     */
    private fun handlePostMovement(movementSuccessful: Boolean) {
        val escapeSuccessful = highlightEscape(!movementSuccessful)
        val cycleBreakSuccessful = handleCycleCompletion(navigator.isInCycleBreak)

        if (cycleBreakSuccessful || escapeSuccessful) return

        if (handleAutoScanCycleLimit()) return

        highlightCurrent()
    }

    /**
     * Manually steps forward in the scanning tree.
     * This method is used for manual navigation through the tree.
     */
    override fun stepScanningForward() {
        if (checkManualScanSetup()) {
            return
        }

        if (!handlePreMovement()) {
            val movementSuccessful = navigator.moveSelectionToNext()
            handlePostMovement(movementSuccessful)
        }
    }

    /**
     * Manually steps backward in the scanning tree.
     * This method is used for manual navigation through the tree.
     */
    override fun stepScanningBackward() {
        if (checkManualScanSetup()) {
            return
        }

        if (!handlePreMovement()) {
            val movementSuccessful = navigator.moveSelectionToPrevious()
            handlePostMovement(movementSuccessful)
        }
    }

    /**
     * Manually steps up in the scanning tree.
     * This method is used for directional navigation through the tree.
     */
    override fun stepScanningUp() {
        if (checkManualScanSetup()) {
            return
        }

        if (!handlePreMovement()) {
            val movementSuccessful = navigator.moveSelectionUp()
            handlePostMovement(movementSuccessful)
        }
    }

    /**
     * Manually steps down in the scanning tree.
     * This method is used for directional navigation through the tree.
     */
    override fun stepScanningDown() {
        if (checkManualScanSetup()) {
            return
        }

        if (!handlePreMovement()) {
            val movementSuccessful = navigator.moveSelectionDown()
            handlePostMovement(movementSuccessful)
        }
    }

    /**
     * Manually steps left in the scanning tree.
     * This method is used for directional navigation through the tree.
     */
    override fun stepScanningLeft() {
        if (checkManualScanSetup()) {
            return
        }

        if (!handlePreMovement()) {
            val movementSuccessful = navigator.moveSelectionToPrevious()
            handlePostMovement(movementSuccessful)
        }
    }

    /**
     * Manually steps right in the scanning tree.
     * This method is used for directional navigation through the tree.
     */
    override fun stepScanningRight() {
        if (checkManualScanSetup()) {
            return
        }

        if (!handlePreMovement()) {
            val movementSuccessful = navigator.moveSelectionToNext()
            handlePostMovement(movementSuccessful)
        }
    }

    /**
     * Swaps the scanning direction between vertical and horizontal.
     * This method is called when the user wants to change the scanning direction.
     */
    override fun swapScanDirection() {
        navigator.swapScanDirection()
        if (scanSettings.isAutoScanMode()) {
            resumeAutoScanning()
        }

        unhighlightCurrent()

        // If scanning is active, highlight the current item
        if (scanningScheduler?.isScanning() == true) {
            highlightCurrent()
        }
    }

    /**
     * Sets up the scanning scheduler if required.
     * This method initializes the scanning scheduler if it hasn't been set up yet.
     */
    private fun setup() {
        if (scanningScheduler == null) {
            scanningScheduler?.stopScanning()
            highlighter.unhighlightAll()
            navigator.reset()
            isManualScanActive = false
            scanningScheduler = ScanningScheduler(context) {
                stepAutoScanning()
            }
        }
    }

    /**
     * Highlights the current item, group, or node based on the current state.
     */
    private fun highlightCurrent() {
        Log.d(
            TAG,
            "Highlighting current: treeItem=${navigator.currentTreeItem}, group=${navigator.currentGroup}, column=${navigator.currentColumn}, isInTreeItem=${navigator.isInTreeItem}, isScanningGroups=${navigator.isScanningGroups}"
        )

        if (navigator.isInCycleBreak) {
            return
        }

        highlighter.highlightCurrent(
            navigator.currentTreeItem,
            navigator.currentGroup,
            navigator.currentColumn,
            navigator.isInTreeItem,
            navigator.isScanningGroups
        )

        speakDuringScan()
    }

    /**
     * Unhighlights the current item, group, or node.
     */
    private fun unhighlightCurrent() {
        highlighter.unhighlightCurrent(
            navigator.currentTreeItem,
            navigator.currentGroup,
            navigator.currentColumn,
            navigator.isInTreeItem,
            navigator.isScanningGroups
        )
    }

    /**
     * Starts the scanning process.
     */
    override fun startAutoScanning() {
        setup()
        if (tree.isNotEmpty()) {
            scanningScheduler?.stopScanning()
            highlighter.unhighlightAll()
            navigator.reset()
            isManualScanActive = false
            callback?.onScanTreeStarted()
            highlightCurrent() // Highlight the first item
            if (scanSettings.isAutoScanMode()) {
                Log.d(TAG, "startScanning")
                scanningScheduler?.startScanning(
                    initialDelay = scanningSpeed,
                    period = scanningSpeed
                )
            }
        }
    }

    /**
     * Pauses the scanning process.
     */
    override fun pauseAutoScanning() {
        scanningScheduler?.pauseScanning()
    }

    /**
     * Resumes the scanning process.
     */
    override fun resumeAutoScanning() {
        scanningScheduler?.resumeScanning()
    }

    /**
     * Resets the UI to its initial state.
     */
    override fun resetUI() {
        highlighter.unhighlightAll()
    }

    /**
     * Checks if the scanning tree is empty.
     *
     * @return True if the scanning tree is empty, false otherwise.
     */
    fun isEmpty(): Boolean {
        return tree.isEmpty()
    }

    /**
     * Resets the scanning tree to its initial state.
     */
    override fun resetForNextUse() {
        scanningScheduler?.stopScanning()
        callback?.onScanTreeStopped()
        callback?.onScanTreeCycleBreakSkipped()
        highlighter.unhighlightAll()
        navigator.reset()
        isManualScanActive = false
        callback?.onScanTreeReset()
    }

    /**
     * Clears the scanning tree.
     */
    fun clearTree() {
        resetForNextUse() // Reset the scanning state
        tree.clear() // Clear the tree
    }

    /**
     * Get the tree items for external spatial navigation
     */
    fun getTree(): List<ScanTreeItem> = tree

    /**
     * Set the current scan position directly for spatial navigation
     * @param treeIndex The tree item index to navigate to
     * @param nodeIndex The node index within the tree item
     */
    fun setSpatialPosition(treeIndex: Int, nodeIndex: Int) {
        if (treeIndex < tree.size && nodeIndex < tree[treeIndex].children.size) {
            // Update navigator position directly
            navigator.currentTreeItem = treeIndex
            navigator.currentColumn = nodeIndex
            navigator.isInTreeItem = true
            navigator.isInGroup = false
            navigator.isScanningGroups = false
            // Highlight the new position
            highlightCurrent()
        }
    }

    override fun cleanup() {
        // Clear callback before cleanup to prevent race condition with handler-posted UI updates
        // during quick technique switches (e.g., keyboard dismiss -> rapid scan start)
        callback = null
        super.cleanup()
        scanningScheduler?.shutdown()
        scanningScheduler = null
    }
}
