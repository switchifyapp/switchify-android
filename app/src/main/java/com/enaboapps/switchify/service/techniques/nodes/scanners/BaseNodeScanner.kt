package com.enaboapps.switchify.service.techniques.nodes.scanners

import android.content.Context
import android.util.Log
import com.enaboapps.switchify.service.menu.MenuManager
import com.enaboapps.switchify.service.menu.OpenMenuPrompt
import com.enaboapps.switchify.service.scanning.tree.ScanTree
import com.enaboapps.switchify.service.scanning.tree.ScanTreeCallback
import com.enaboapps.switchify.service.techniques.AccessTechnique
import com.enaboapps.switchify.service.techniques.nodes.Node
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Base class for node scanners that provides common functionality for both system and keyboard scanners.
 */
abstract class BaseNodeScanner : ScanTreeCallback {
    protected lateinit var context: Context
    lateinit var scanTree: ScanTree
    protected val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    protected var revertToCursorJob: Job? = null
    protected var lastNodeUpdateTime: Long = 0
    protected var continuousUpdateJob: Job? = null
    protected var rapidUpdateCount: Int = 0
    protected var updateCountResetJob: Job? = null

    companion object {
        private const val TAG = "BaseNodeScanner"
        private const val EMPTY_NODES_TIMEOUT_MS =
            5000L // Time to wait before reverting to cursor when nodes are empty
        protected const val RAPID_UPDATE_THRESHOLD_MS =
            200L // Time in ms to consider an update as rapid
        protected const val RESET_WINDOW_MS = 10000L // Time window to reset the update count
        protected const val MAX_RAPID_UPDATES =
            150 // Number of rapid updates before switching to cursor
    }

    /**
     * Starts the node scanner.
     * Initializes the scanTree with the context and starts observing node updates.
     *
     * @param context The context in which the node scanner is started.
     */
    open fun start(context: Context) {
        this.context = context
        startTimeoutToRevertToCursor()
        scanTree = ScanTree(
            context = context,
            stopScanningOnSelect = true,
            hasCycleBreak = true,
            callback = this
        )
    }

    /**
     * Updates the nodes in the scanner.
     *
     * @param nodes The list of nodes to update the scanner with.
     */
    open fun updateNodes(nodes: List<Node>) {
        buildFromNodes(nodes)
        handleContinuousUpdates()
        if (nodes.isEmpty()) {
            startTimeoutToRevertToCursor()
        }
    }

    /**
     * Cleans up the node scanner.
     */
    open fun cleanup() {
        revertToCursorJob?.cancel()
        continuousUpdateJob?.cancel()
        updateCountResetJob?.cancel()
        scanTree.cleanup()
    }

    /**
     * Starts the timeout to revert to cursor when nodes are empty.
     */
    open fun startTimeoutToRevertToCursor() {
        revertToCursorJob?.cancel()
        revertToCursorJob = coroutineScope.launch {
            delay(EMPTY_NODES_TIMEOUT_MS)
            withContext(Dispatchers.Main) {
                scanTree.stopScanningAndReset()
                if (AccessTechnique.getCurrentTechnique() == AccessTechnique.Technique.ITEM_SCAN) {
                    AccessTechnique.setCurrentTechnique(AccessTechnique.Technique.CURSOR)
                    Log.d(TAG, "Reverted to cursor mode due to empty nodes")
                }
            }
        }
    }

    /**
     * Builds the scanning tree from a list of nodes.
     *
     * @param nodes The list of nodes to build the tree from.
     */
    protected open fun buildFromNodes(nodes: List<Node>) {
        scanTree.buildTree(nodes)
    }

    /**
     * Handles continuous updates and switches to cursor mode if there are too many rapid updates.
     */
    protected open fun handleContinuousUpdates() {
        val currentTime = System.currentTimeMillis()
        val timeSinceLastUpdate = currentTime - lastNodeUpdateTime

        if (timeSinceLastUpdate < RAPID_UPDATE_THRESHOLD_MS) {
            rapidUpdateCount++
            updateCountResetJob?.cancel()

            if (rapidUpdateCount >= MAX_RAPID_UPDATES) {
                continuousUpdateJob = coroutineScope.launch {
                    withContext(Dispatchers.Main) {
                        scanTree.stopScanningAndReset()
                        if (AccessTechnique.getCurrentTechnique() == AccessTechnique.Technique.ITEM_SCAN) {
                            AccessTechnique.setCurrentTechnique(AccessTechnique.Technique.CURSOR)
                            Log.d(TAG, "Switched to cursor mode due to rapid updates")
                        }
                        rapidUpdateCount = 0
                    }
                }
            } else {
                // Start a new window to reset the count
                updateCountResetJob = coroutineScope.launch {
                    delay(RESET_WINDOW_MS)
                    rapidUpdateCount = 0
                }
            }
        } else {
            // If this update wasn't rapid, start a new counting window
            updateCountResetJob?.cancel()
            updateCountResetJob = coroutineScope.launch {
                delay(RESET_WINDOW_MS)
                rapidUpdateCount = 0
            }
        }
        lastNodeUpdateTime = currentTime
    }

    override fun onScanTreeCycleBreakStarted() {
        Log.d(TAG, "Cycle break started")
        OpenMenuPrompt.instance.show(context)
    }

    override fun onScanTreeCycleBreakSkipped() {
        Log.d(TAG, "Cycle break skipped")
        OpenMenuPrompt.instance.hide()
    }

    override fun onScanTreeCycleBreakSelected() {
        Log.d(TAG, "Cycle break selected")
        OpenMenuPrompt.instance.hide()
        MenuManager.getInstance().openScanCycleBreakMenu()
    }

    override fun onSingleCycleCompleted(cycleNumber: Int) {
        Log.d(TAG, "Cycle completed: $cycleNumber")
    }
} 