package com.enaboapps.switchify.service.methods.nodes.scanners.keyboard

import android.content.Context
import android.util.Log
import com.enaboapps.switchify.service.methods.nodes.Node
import com.enaboapps.switchify.service.scanning.tree.ScanTree

class KeyboardScanner {
    private lateinit var scanTree: ScanTree
    private val currentNodes = mutableListOf<Node>()

    companion object {
        private const val TAG = "KeyboardScanner"
    }

    /**
     * Starts the keyboard scanner.
     *
     * @param context The context in which the keyboard scanner is started.
     */
    fun start(context: Context) {
        scanTree = ScanTree(
            context = context,
            stopScanningOnSelect = true
        )
    }

    /**
     * Updates the keyboard scanner with the given nodes.
     *
     * @param nodes The list of nodes to update the keyboard scanner with.
     */
    fun updateNodes(nodes: List<Node>) {
        if (currentNodes == nodes) {
            return
        }
        currentNodes.clear()
        currentNodes.addAll(nodes)
        Log.d(TAG, "Updating keyboard scanner with ${nodes.size} nodes")
        scanTree.buildTree(nodes)
    }

    /**
     * Gets the scan tree for the keyboard scanner.
     *
     * @return The scan tree for the keyboard scanner.
     */
    fun getScanTree(): ScanTree {
        return scanTree
    }

    /**
     * Cleans up the keyboard scanner.
     */
    fun cleanup() {
        scanTree.cleanup()
    }
}