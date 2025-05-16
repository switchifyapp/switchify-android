package com.enaboapps.switchify.service.techniques.nodes.scanners.system

import android.content.Context
import android.util.Log
import com.enaboapps.switchify.service.techniques.nodes.Node
import com.enaboapps.switchify.service.techniques.nodes.NodeSpeaker
import com.enaboapps.switchify.service.techniques.nodes.scanners.BaseNodeScanner

/**
 * SystemNodeScanner is a class that handles the scanning of nodes.
 * It manages the scanning process using a ScanTree instance and handles updates from NodeExaminer.
 * Duplicate updates are prevented by the parent BaseNodeScanner class.
 */
class SystemNodeScanner : BaseNodeScanner() {
    companion object {
        private const val TAG = "SystemNodeScanner"
    }

    override fun start(context: Context) {
        super.start(context)
        // Register for node updates
        SystemNodeHolder.setOnNodesUpdatedCallback { nodes ->
            handleNodeUpdate(nodes)
        }
        // Build initial tree from current nodes
        buildFromNodes(SystemNodeHolder.getNodes())
        NodeSpeaker.init(context)
    }

    private fun handleNodeUpdate(nodes: List<Node>) {
        Log.d(TAG, "Received node update with ${nodes.size} nodes")
        updateNodes(nodes)
    }

    override fun cleanup() {
        super.cleanup()
        // Remove the callback from SystemNodeHolder
        SystemNodeHolder.removeCallback()
    }
}