package com.enaboapps.switchify.service.techniques.nodes.scanners.keyboard

import android.util.Log
import com.enaboapps.switchify.service.scanning.CycleBreakListener
import com.enaboapps.switchify.service.techniques.nodes.Node
import com.enaboapps.switchify.service.techniques.nodes.scanners.BaseNodeScanner

class KeyboardScanner(
    cycleBreakListener: CycleBreakListener? = null
) : BaseNodeScanner(cycleBreakListener) {
    companion object {
        private const val TAG = "KeyboardScanner"
    }

    /**
     * Updates the keyboard scanner with the given nodes.
     * Duplicate detection is handled by the parent class.
     *
     * @param nodes The list of nodes to update the keyboard scanner with.
     */
    override fun updateNodes(nodes: List<Node>) {
        Log.d(TAG, "Updating keyboard scanner with ${nodes.size} nodes")
        super.updateNodes(nodes)
    }
}