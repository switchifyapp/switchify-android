package com.enaboapps.switchify.service.methods.nodes

import android.content.Context
import android.util.Log
import com.enaboapps.switchify.service.menu.MenuPopup
import com.enaboapps.switchify.service.scanning.ScanMethod
import com.enaboapps.switchify.service.scanning.tree.ScanTree
import com.enaboapps.switchify.service.scanning.tree.ScanTreeCallback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * NodeScanner is a class that handles the scanning of nodes.
 * It manages the scanning process using a ScanTree instance and handles updates from NodeExaminer.
 */
class NodeScanner : ScanTreeCallback {
    private val TAG = "NodeScanner"
    private lateinit var context: Context
    lateinit var scanTree: ScanTree
    private var currentNodes: List<Node> = emptyList()
    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

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
        coroutineScope.launch {
            delay(5000)
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
     * Updates the nodes and rebuilds the scanTree.
     * If no nodes are present, it starts the timeout.
     *
     * @param nodes List of new Node instances.
     */
    fun updateNodes(nodes: List<Node>) {
        currentNodes = nodes
        scanTree.buildTree(nodes)
        if (nodes.isEmpty()) {
            startTimeoutToRevertToCursor()
        }
    }

    override fun onScanTreeCycleBreakStarted() {
        Log.d(TAG, "Cycle break started")
        MenuPopup.instance.show(context)
    }

    override fun onScanTreeCycleBreakSkipped() {
        Log.d(TAG, "Cycle break skipped")
        MenuPopup.instance.hide()
    }

    override fun onScanTreeCycleBreakSelected() {
        Log.d(TAG, "Cycle break selected")
        MenuPopup.instance.hide()
    }

    override fun onSingleCycleCompleted(cycleNumber: Int) {
        Log.d(TAG, "Cycle completed: $cycleNumber")
    }
}