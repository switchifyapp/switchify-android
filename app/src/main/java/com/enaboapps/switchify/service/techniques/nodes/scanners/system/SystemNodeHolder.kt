package com.enaboapps.switchify.service.techniques.nodes.scanners.system

import com.enaboapps.switchify.service.techniques.nodes.Node

/**
 * SystemNodeHolder is responsible for maintaining the current list of nodes.
 * It acts as a central store for nodes that can be accessed by different components.
 */
object SystemNodeHolder {
    private var currentNodes: List<Node> = emptyList()
    private var onNodesUpdatedCallback: ((List<Node>) -> Unit)? = null

    /**
     * Updates the current list of nodes and notifies any registered callback.
     *
     * @param nodes The new list of nodes to store
     */
    fun updateNodes(nodes: List<Node>) {
        currentNodes = nodes
        onNodesUpdatedCallback?.invoke(nodes)
    }

    /**
     * Gets the current list of nodes.
     *
     * @return The current list of nodes
     */
    fun getNodes(): List<Node> = currentNodes

    /**
     * Sets a callback to be notified when nodes are updated.
     *
     * @param callback The callback to invoke when nodes are updated
     */
    fun setOnNodesUpdatedCallback(callback: (List<Node>) -> Unit) {
        onNodesUpdatedCallback = callback
    }

    /**
     * Removes the nodes updated callback.
     */
    fun removeCallback() {
        onNodesUpdatedCallback = null
    }

    /**
     * Clears the current list of nodes.
     */
    fun clear() {
        currentNodes = emptyList()
        onNodesUpdatedCallback = null
    }
} 