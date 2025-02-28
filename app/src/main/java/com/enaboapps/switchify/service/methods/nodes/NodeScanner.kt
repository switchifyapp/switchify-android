package com.enaboapps.switchify.service.methods.nodes

import android.content.Context
import android.util.Log
import com.enaboapps.switchify.service.menu.MenuManager
import com.enaboapps.switchify.service.menu.OpenMenuPrompt
import com.enaboapps.switchify.service.scanning.ScanMethod
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
 * NodeScanner is a class that handles the scanning of nodes.
 * It manages the scanning process using a ScanTree instance and handles updates from NodeExaminer.
 */
class NodeScanner : ScanTreeCallback {
    companion object {
        private const val TAG = "NodeScanner"
        private const val RAPID_UPDATE_THRESHOLD_MS =
            200L // Time in ms to consider an update as rapid
        private const val RESET_WINDOW_MS = 10000L // Time window to reset the update count
        private const val MAX_RAPID_UPDATES =
            150 // Number of rapid updates before switching to cursor
        private const val EMPTY_NODES_TIMEOUT_MS =
            5000L // Time to wait before reverting to cursor when nodes are empty
    }

    private lateinit var context: Context
    lateinit var scanTree: ScanTree
    private var currentNodes: List<Node> = emptyList()
    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var lastNodeUpdateTime: Long = 0
    private var continuousUpdateJob: Job? = null
    private var rapidUpdateCount: Int = 0
    private var updateCountResetJob: Job? = null
    private var revertToCursorJob: Job? = null

    /**
     * Starts the NodeScanner.
     * Initializes the scanTree with the context and starts observing node updates.
     *
     * @param context The context in which the NodeScanner is started.
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
        NodeSpeaker.init(context)
    }

    /**
     * Starts a timeout that resets the scanTree and changes the state of the ScanMethod
     * if the state is ITEM_SCAN and there are no nodes after 5 seconds.
     */
    fun startTimeoutToRevertToCursor() {
        revertToCursorJob?.cancel()
        revertToCursorJob = coroutineScope.launch {
            delay(EMPTY_NODES_TIMEOUT_MS)
            if (ScanMethod.getType() == ScanMethod.MethodType.ITEM_SCAN && currentNodes.isEmpty()) {
                withContext(Dispatchers.Main) {
                    scanTree.reset()
                    ScanMethod.setType(ScanMethod.MethodType.CURSOR)
                    Log.d(TAG, "ScanMethod changed to cursor")
                }
            } else {
                Log.d(TAG, "ScanMethod not changed, nodes.size: ${currentNodes.size}")
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
                    if (ScanMethod.getType() == ScanMethod.MethodType.ITEM_SCAN) {
                        withContext(Dispatchers.Main) {
                            scanTree.reset()
                            ScanMethod.setType(ScanMethod.MethodType.CURSOR)
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

    /**
     * Updates the nodes and rebuilds the scanTree.
     * If no nodes are present, it starts the timeout.
     * If nodes are continuously updating for more than 10 seconds, switches to cursor mode.
     *
     * @param nodes List of new Node instances.
     */
    fun updateNodes(nodes: List<Node>) {
        currentNodes = nodes
        scanTree.buildTree(nodes)

        handleContinuousUpdates()

        if (nodes.isEmpty()) {
            startTimeoutToRevertToCursor()
        }
    }

    /**
     * Resets NodeScanner and stops any ongoing jobs.
     */
    fun cleanup() {
        // Cancel any existing jobs
        continuousUpdateJob?.cancel()
        updateCountResetJob?.cancel()
        revertToCursorJob?.cancel()

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