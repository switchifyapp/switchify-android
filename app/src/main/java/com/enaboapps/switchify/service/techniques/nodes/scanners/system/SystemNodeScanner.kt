package com.enaboapps.switchify.service.techniques.nodes.scanners.system

import android.content.Context
import android.util.Log
import com.enaboapps.switchify.service.menu.MenuManager
import com.enaboapps.switchify.service.menu.OpenMenuPrompt
import com.enaboapps.switchify.service.techniques.nodes.Node
import com.enaboapps.switchify.service.techniques.nodes.NodeSpeaker
import com.enaboapps.switchify.service.techniques.AccessTechnique
import com.enaboapps.switchify.service.scanning.tree.ScanTree
import com.enaboapps.switchify.service.scanning.tree.ScanTreeCallback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * SystemNodeScanner is a class that handles the scanning of nodes.
 * It manages the scanning process using a ScanTree instance and handles updates from NodeExaminer.
 */
class SystemNodeScanner : ScanTreeCallback {
    companion object {
        private const val TAG = "SystemNodeScanner"
        private const val RAPID_UPDATE_THRESHOLD_MS =
            200L // Time in ms to consider an update as rapid
        private const val RESET_WINDOW_MS = 10000L // Time window to reset the update count
        private const val MAX_RAPID_UPDATES =
            150 // Number of rapid updates before switching to cursor
        private const val EMPTY_NODES_TIMEOUT_MS =
            5000L // Time to wait before reverting to cursor when nodes are empty
    }

    private lateinit var context: Context
    private lateinit var scanTree: ScanTree
    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var lastNodeUpdateTime: Long = 0
    private var continuousUpdateJob: Job? = null
    private var rapidUpdateCount: Int = 0
    private var updateCountResetJob: Job? = null
    private var revertToCursorJob: Job? = null

    /**
     * Starts the SystemNodeScanner.
     * Initializes the scanTree with the context and starts observing node updates.
     *
     * @param context The context in which the SystemNodeScanner is started.
     */
    fun start(context: Context) {
        this.context = context
        startTimeoutToRevertToCursor()
        scanTree = ScanTree(
            context = context,
            stopScanningOnSelect = true,
            hasCycleBreak = true,
            callback = this
        )

        // Register for node updates
        SystemNodeHolder.setOnNodesUpdatedCallback { nodes ->
            handleNodeUpdate(nodes)
        }

        // Build initial tree from current nodes
        buildFromNodes(SystemNodeHolder.getNodes())

        NodeSpeaker.init(context)
    }

    /**
     * Starts a timeout that resets the scanTree and changes the state of the AccessTechnique
     * if the state is ITEM_SCAN and there are no nodes after 5 seconds.
     */
    fun startTimeoutToRevertToCursor() {
        revertToCursorJob?.cancel()
        revertToCursorJob = coroutineScope.launch {
            delay(EMPTY_NODES_TIMEOUT_MS)
            if (AccessTechnique.getCurrentTechnique() == AccessTechnique.Technique.ITEM_SCAN && SystemNodeHolder.getNodes()
                    .isEmpty()
            ) {
                withContext(Dispatchers.Main) {
                    scanTree.reset()
                    AccessTechnique.setCurrentTechnique(AccessTechnique.Technique.CURSOR)
                    Log.d(TAG, "AccessTechnique changed to cursor")
                }
            } else {
                Log.d(
                    TAG,
                    "AccessTechnique not changed, nodes.size: ${SystemNodeHolder.getNodes().size}"
                )
            }
        }
    }

    /**
     * Checks if nodes are continuously updating and switches to cursor mode if there are
     * too many rapid updates within a 10-second window.
     */
    private fun handleContinuousUpdates() {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastNodeUpdateTime < RAPID_UPDATE_THRESHOLD_MS) {
            rapidUpdateCount++

            // Cancel existing jobs
            continuousUpdateJob?.cancel()
            updateCountResetJob?.cancel()

            if (rapidUpdateCount >= MAX_RAPID_UPDATES) {
                continuousUpdateJob = coroutineScope.launch {
                    if (AccessTechnique.getCurrentTechnique() == AccessTechnique.Technique.ITEM_SCAN) {
                        withContext(Dispatchers.Main) {
                            scanTree.reset()
                            AccessTechnique.setCurrentTechnique(AccessTechnique.Technique.CURSOR)
                            Log.d(
                                TAG,
                                "Switched to cursor mode due to $rapidUpdateCount rapid updates"
                            )
                            rapidUpdateCount = 0
                        }
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

    private fun handleNodeUpdate(nodes: List<Node>) {
        buildFromNodes(nodes)
        handleContinuousUpdates()

        if (nodes.isEmpty()) {
            startTimeoutToRevertToCursor()
        }
    }

    private fun buildFromNodes(nodes: List<Node>) {
        scanTree.buildTree(nodes)
    }

    /**
     * Gets the scan tree for the node scanner.
     *
     * @return The scan tree for the node scanner.
     */
    fun getScanTree(): ScanTree {
        return scanTree
    }

    /**
     * Resets SystemNodeScanner and stops any ongoing jobs.
     */
    fun cleanup() {
        // Cancel any existing jobs
        continuousUpdateJob?.cancel()
        updateCountResetJob?.cancel()
        revertToCursorJob?.cancel()

        // Remove the callback from SystemNodeHolder
        SystemNodeHolder.removeCallback()

        // Cleanup the scanTree
        scanTree.cleanup()
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