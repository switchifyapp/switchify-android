package com.enaboapps.switchify.service.techniques.nodes.scanners.keyboard

import android.util.Log
import com.enaboapps.switchify.service.techniques.nodes.Node
import com.enaboapps.switchify.service.techniques.nodes.scanners.BaseNodeScanner

class KeyboardScanner : BaseNodeScanner() {
    private val currentNodes = mutableListOf<Node>()

    companion object {
        private const val TAG = "KeyboardScanner"
    }

    /**
     * Updates the keyboard scanner with the given nodes.
     *
     * @param nodes The list of nodes to update the keyboard scanner with.
     */
    override fun updateNodes(nodes: List<Node>) {
        if (currentNodes == nodes) {
            return
        }
        currentNodes.clear()
        currentNodes.addAll(nodes)
        Log.d(TAG, "Updating keyboard scanner with ${nodes.size} nodes")
        super.updateNodes(nodes)
    }
}