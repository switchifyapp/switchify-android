package com.enaboapps.switchify.service.techniques.nodes

import android.graphics.Rect

/**
 * Snapshot of keyboard nodes emitted by NodeExaminer.
 *
 * Pairs the node list with the IME bounds it was captured from so consumers
 * can detect a stale batch after the keyboard has resized or been replaced.
 *
 * @property nodes Keyboard nodes extracted from the IME accessibility tree.
 * @property keyboardBounds Bounds of the IME window at extraction time, or
 *   null if the keyboard was not visible. Treated as a snapshot — callers
 *   must not mutate.
 */
data class KeyboardNodesState(
    val nodes: List<Node> = emptyList(),
    val keyboardBounds: Rect? = null
) {
    /**
     * True when this batch's captured bounds differ from [currentBounds],
     * meaning the nodes belong to a previous keyboard layout.
     *
     * Returns false when either side is null — callers needing to reject
     * uninitialized batches (no bounds captured yet) should check that
     * separately or go through `KeyboardNodesPolicy.shouldDropAsStale`.
     */
    fun isStaleAgainst(currentBounds: Rect?): Boolean {
        return keyboardBounds != null &&
                currentBounds != null &&
                keyboardBounds != currentBounds
    }
}
