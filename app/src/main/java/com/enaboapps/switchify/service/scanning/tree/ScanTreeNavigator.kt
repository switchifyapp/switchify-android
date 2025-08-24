package com.enaboapps.switchify.service.scanning.tree

import com.enaboapps.switchify.service.scanning.ScanDirection
import com.enaboapps.switchify.service.scanning.ScanNodeInterface
import com.enaboapps.switchify.service.scanning.ScanSettings

/**
 * This class is responsible for navigating through the ScanTree structure.
 * It manages the current position and provides methods for moving between items, groups, and nodes.
 * The class supports row-column scanning, sequential (non-row-column) scanning, and directional scanning modes.
 *
 * @property tree The list of ScanTreeItems that make up the scanning tree.
 * @property scanSettings The settings for scanning behavior.
 * @property hasCycleBreak Indicates whether scanning includes a break between cycles
 */
class ScanTreeNavigator(
    private val tree: List<ScanTreeItem>,
    private val scanSettings: ScanSettings,
    private val hasCycleBreak: Boolean = false
) {
    /** Represents the different types of escape states in the scanning tree */
    sealed class EscapeState {
        object None : EscapeState()
        object Item : EscapeState()
        object Group : EscapeState()
    }

    /** The index of the current tree item being scanned. */
    var currentTreeItem = 0

    /** The index of the current group within the current tree item. */
    var currentGroup = 0

    /** Indicates whether the scanning is currently within a group. */
    var isInGroup = false

    /** The index of the current column within the current group or the current node in non-row-column mode. */
    var currentColumn = 0

    /** Indicates whether the scanning is currently within a tree item. */
    var isInTreeItem = false

    /** Tracks the current cycle of the scanning tree */
    var currentCycle = 0

    /** Indicates whether we're scanning groups or items within a group. */
    var isScanningGroups = true

    /** Indicates whether we're in the cycle break */
    var isInCycleBreak = false

    /** Flag to track if we just completed a cycle */
    private var justCompletedCycle = false

    /** The current direction of scanning. */
    var scanDirection = ScanDirection.DOWN

    /** The current escape state */
    private var escapeState: EscapeState = EscapeState.None

    /** Spatial navigator for directional mode */
    private val spatialNavigator: SpatialNavigator by lazy { SpatialNavigator(tree) }

    /** Indicates whether row-column scanning is enabled based on scan settings. */
    private val isRowColumnScanEnabled: Boolean
        get() = scanSettings.isRowColumnScanEnabled()

    /**
     * A flattened list of all nodes in the tree, used when row-column scanning is disabled.
     * Computed lazily to avoid unnecessary processing when row-column scanning is enabled.
     */
    private val flattenedNodes: List<ScanNodeInterface> by lazy {
        tree.flatMap { it.children }
    }

    /**
     * Validates the scanning direction
     * Sequential scanning should only allow left and right
     */
    private fun validateScanDirection() {
        if (!isRowColumnScanEnabled && scanDirection != ScanDirection.LEFT && scanDirection != ScanDirection.RIGHT) {
            scanDirection = ScanDirection.RIGHT
        }
    }

    /**
     * Main movement function that handles both directions based on current scan direction
     */
    fun moveSelectionToNextOrPrevious(): Boolean {
        if (isInCycleBreak) return false

        return if (scanSettings.isDirectionalScanMode()) {
            handleDirectionalMovement()
        } else if (!isRowColumnScanEnabled) {
            handleSequentialMovement()
        } else {
            when (scanDirection) {
                ScanDirection.DOWN, ScanDirection.RIGHT -> moveSelectionToNext()
                ScanDirection.UP, ScanDirection.LEFT -> moveSelectionToPrevious()
            }
        }
    }

    /**
     * Handles movement in directional scanning mode
     */
    private fun handleDirectionalMovement(): Boolean {
        val result = spatialNavigator.findClosestNodeInDirection(
            currentTreeIndex = currentTreeItem,
            currentNodeIndex = currentColumn,
            direction = scanDirection
        )
        
        return if (result != null) {
            val (newTreeIndex, newNodeIndex) = result
            currentTreeItem = newTreeIndex
            currentColumn = newNodeIndex
            // Reset group navigation state for directional mode
            currentGroup = 0
            isInTreeItem = true
            isInGroup = false
            isScanningGroups = false
            true
        } else {
            // No node found in this direction - stay in current position
            // Return true to indicate the action was handled (even though no movement occurred)
            true
        }
    }

    /**
     * Handles movement in sequential (non-row-column) scanning mode
     */
    private fun handleSequentialMovement(): Boolean {
        validateScanDirection()
        when (scanDirection) {
            ScanDirection.LEFT -> moveSequentialPrevious()
            ScanDirection.RIGHT -> moveSequentialNext()
            else -> return false
        }
        return true
    }

    /**
     * Moves selection forward based on current scanning mode and state
     */
    fun moveSelectionToNext(): Boolean {
        if (isInCycleBreak) return false

        return if (scanSettings.isDirectionalScanMode()) {
            scanDirection = ScanDirection.RIGHT
            handleDirectionalMovement()
        } else if (!isRowColumnScanEnabled) {
            moveSequentialNext()
        } else {
            when {
                !isInTreeItem -> moveSelectionToNextTreeItem()
                isCurrentItemSingleGroup() -> moveSelectionToNextWithinGroup()
                scanSettings.isGroupScanEnabled() && isScanningGroups -> moveSelectionToNextGroup()
                else -> moveSelectionToNextWithinGroup()
            }
        }
    }

    /**
     * Moves selection backward based on current scanning mode and state
     */
    fun moveSelectionToPrevious(): Boolean {
        if (isInCycleBreak) return false

        return if (scanSettings.isDirectionalScanMode()) {
            scanDirection = ScanDirection.LEFT
            handleDirectionalMovement()
        } else if (!isRowColumnScanEnabled) {
            moveSequentialPrevious()
        } else {
            when {
                !isInTreeItem -> moveSelectionToPreviousTreeItem()
                isCurrentItemSingleGroup() -> moveSelectionToPreviousWithinGroup()
                scanSettings.isGroupScanEnabled() && isScanningGroups -> moveSelectionToPreviousGroup()
                else -> moveSelectionToPreviousWithinGroup()
            }
        }
    }

    private fun moveSequentialNext(): Boolean {
        if (currentColumn < flattenedNodes.size - 1) {
            currentColumn++
        } else {
            currentColumn = 0
            handleCycleCompletion()
        }
        return true
    }

    private fun moveSequentialPrevious(): Boolean {
        if (currentColumn > 0) {
            currentColumn--
        } else {
            currentColumn = flattenedNodes.size - 1
            handleCycleCompletion()
        }
        return true
    }

    /**
     * Move selection up in directional mode
     */
    fun moveSelectionUp(): Boolean {
        if (isInCycleBreak) return false
        
        return if (scanSettings.isDirectionalScanMode()) {
            scanDirection = ScanDirection.UP
            handleDirectionalMovement()
        } else {
            false
        }
    }

    /**
     * Move selection down in directional mode
     */
    fun moveSelectionDown(): Boolean {
        if (isInCycleBreak) return false
        
        return if (scanSettings.isDirectionalScanMode()) {
            scanDirection = ScanDirection.DOWN
            handleDirectionalMovement()
        } else {
            false
        }
    }

    private fun moveSelectionToNextWithinGroup(): Boolean {
        val currentItem = getCurrentItem()
        return when {
            currentColumn < currentItem.getNodeCount(currentGroup) - 1 -> {
                currentColumn++
                true
            }

            scanSettings.isDirectionalScanMode() -> {
                // In directional mode, don't set escape state, just return false
                false
            }

            isCurrentItemSingleGroup() -> {
                escapeState = EscapeState.Item
                false
            }

            scanSettings.isGroupScanEnabled() -> {
                escapeState = EscapeState.Group
                false
            }

            else -> {
                escapeState = EscapeState.Item
                false
            }
        }
    }

    private fun moveSelectionToPreviousWithinGroup(): Boolean {
        return when {
            currentColumn > 0 -> {
                currentColumn--
                true
            }

            scanSettings.isDirectionalScanMode() -> {
                // In directional mode, don't set escape state, just return false
                false
            }

            isCurrentItemSingleGroup() -> {
                escapeState = EscapeState.Item
                false
            }

            scanSettings.isGroupScanEnabled() -> {
                escapeState = EscapeState.Group
                false
            }

            else -> {
                escapeState = EscapeState.Item
                false
            }
        }
    }

    private fun moveSelectionToNextGroup(): Boolean {
        return when {
            currentGroup < getCurrentItem().getGroupCount() - 1 -> {
                currentGroup++
                true
            }

            scanSettings.isDirectionalScanMode() -> {
                // In directional mode, don't set escape state, just return false
                false
            }

            else -> {
                escapeState = EscapeState.Item
                false
            }
        }
    }

    private fun moveSelectionToPreviousGroup(): Boolean {
        return when {
            currentGroup > 0 -> {
                currentGroup--
                true
            }

            scanSettings.isDirectionalScanMode() -> {
                // In directional mode, don't set escape state, just return false
                false
            }

            else -> {
                escapeState = EscapeState.Item
                false
            }
        }
    }

    private fun moveSelectionToNextTreeItem(): Boolean {
        if (currentTreeItem < tree.size - 1) {
            currentTreeItem++
        } else {
            currentTreeItem = 0
            handleCycleCompletion()
        }
        resetGroupAndColumn()
        return true
    }

    private fun moveSelectionToPreviousTreeItem(): Boolean {
        if (currentTreeItem > 0) {
            currentTreeItem--
        } else {
            currentTreeItem = tree.size - 1
            handleCycleCompletion()
        }
        resetGroupAndColumn()
        return true
    }

    /**
     * Handles cycle completion and break logic
     */
    private fun handleCycleCompletion() {
        if (isInCycleBreak) {
            isInCycleBreak = false
        } else if (hasCycleBreak) {
            isInCycleBreak = true
        }
        justCompletedCycle = true
        currentCycle++
    }

    /**
     * Finds out if the current item has only one group.
     * @return True if the current item has only one group, false otherwise.
     */
    private fun isCurrentItemSingleGroup(): Boolean = getCurrentItem().getGroupCount() == 1

    /**
     * Resets the group and column indices to their initial values.
     * In non-row-column mode, it only resets the group index.
     */
    private fun resetGroupAndColumn() {
        currentGroup = 0
        isInGroup = false
        currentColumn = if (isRowColumnScanEnabled) 0 else currentColumn
        isScanningGroups = scanSettings.isGroupScanEnabled()
    }

    /**
     * Handles the escape logic for items and groups.
     * In directional mode, escape is disabled to allow free movement.
     * @return True if an escape was handled, false otherwise.
     */
    fun handleEscape(): Boolean = escapeState != EscapeState.None && 
                                  !isInCycleBreak && 
                                  !scanSettings.isDirectionalScanMode()

    /**
     * Checks if the auto scan cycle limit has been reached.
     * @return True if the auto scan cycle limit has been reached, false otherwise.
     */
    fun isAutoScanCycleLimitReached(): Boolean {
        val userDefinedCycles = scanSettings.getScanCycles()
        return currentCycle == userDefinedCycles && scanSettings.isAutoScanMode()
    }

    /**
     * Confirms the escape action and updates the navigation state accordingly.
     * @return True if the escape was confirmed, false otherwise.
     */
    fun confirmEscape(): Boolean {
        return when (escapeState) {
            EscapeState.Item -> {
                resetToItemEscape()
                true
            }

            EscapeState.Group -> {
                resetToGroupEscape()
                true
            }

            EscapeState.None -> false
        }
    }

    /**
     * Denies the escape action and resets the escape flags.
     * @return True if an escape was denied, false otherwise.
     */
    fun denyEscape(): Boolean {
        return when (escapeState) {
            EscapeState.Item -> {
                handleItemEscapeDenial()
                true
            }

            EscapeState.Group -> {
                handleGroupEscapeDenial()
                true
            }

            EscapeState.None -> false
        }
    }

    private fun resetToItemEscape() {
        escapeState = EscapeState.None
        isInTreeItem = false
        isInGroup = false
        scanDirection = ScanDirection.DOWN
        if (!isRowColumnScanEnabled) {
            currentColumn = 0
        }
        currentCycle = 0
    }

    private fun resetToGroupEscape() {
        escapeState = EscapeState.None
        isScanningGroups = true
        isInGroup = false
        scanDirection = ScanDirection.RIGHT
        currentColumn = 0
        currentGroup = 0
    }

    private fun handleItemEscapeDenial() {
        escapeState = EscapeState.None
        currentColumn = if (scanDirection == ScanDirection.RIGHT) 0 else {
            if (isRowColumnScanEnabled) getCurrentItem().getNodeCount(currentGroup) - 1
            else flattenedNodes.size - 1
        }
        if (isRowColumnScanEnabled) {
            currentGroup =
                if (scanDirection == ScanDirection.RIGHT) 0 else getCurrentItem().getGroupCount() - 1
        }
        handleCycleCompletion()
    }

    private fun handleGroupEscapeDenial() {
        escapeState = EscapeState.None
        currentColumn = if (scanDirection == ScanDirection.RIGHT) 0 else {
            if (isRowColumnScanEnabled) getCurrentItem().getNodeCount(currentGroup) - 1
            else flattenedNodes.size - 1
        }
        handleCycleCompletion()
    }

    /**
     * Gets the current ScanTreeItem.
     * @return The current ScanTreeItem.
     */
    fun getCurrentItem(): ScanTreeItem = tree[currentTreeItem]

    /**
     * Swaps the scanning direction between vertical and horizontal.
     */
    fun swapScanDirection() {
        scanDirection = when (scanDirection) {
            ScanDirection.DOWN -> ScanDirection.UP
            ScanDirection.UP -> ScanDirection.DOWN
            ScanDirection.RIGHT -> ScanDirection.LEFT
            ScanDirection.LEFT -> ScanDirection.RIGHT
        }
    }

    /**
     * Selects the current group and switches to scanning items within the group.
     * Only applicable in row-column scanning mode.
     */
    fun selectGroup() {
        if (isRowColumnScanEnabled && scanSettings.isGroupScanEnabled()) {
            isScanningGroups = false
            currentColumn = 0
            scanDirection = ScanDirection.RIGHT
            isInGroup = true
        }
    }

    /**
     * Resets the navigator to its initial state.
     */
    fun reset() {
        currentTreeItem = 0
        currentGroup = 0
        currentColumn = 0
        currentCycle = 0
        
        if (scanSettings.isDirectionalScanMode()) {
            // In directional mode, start positioned at the first node
            val firstNode = spatialNavigator.getFirstNode()
            if (firstNode != null) {
                currentTreeItem = firstNode.first
                currentColumn = firstNode.second
            }
            isInTreeItem = true
            isInGroup = false
            isScanningGroups = false
        } else {
            isInTreeItem = false
            isInGroup = false
            isScanningGroups = scanSettings.isGroupScanEnabled()
        }
        
        escapeState = EscapeState.None
        isInCycleBreak = false
        justCompletedCycle = false
        scanDirection = ScanDirection.DOWN
    }

    /**
     * Gets the current ScanNodeInterface based on the scanning mode.
     * @return The current ScanNodeInterface, or null if not available.
     */
    fun getCurrentNode(): ScanNodeInterface? {
        return if (isRowColumnScanEnabled) {
            getCurrentItem().children.getOrNull(currentColumn)
        } else {
            flattenedNodes.getOrNull(currentColumn)
        }
    }

    /**
     * Checks if we've just completed a cycle
     * @return True if a cycle was just completed, false otherwise
     */
    fun hasCompletedCycle(): Boolean {
        if (justCompletedCycle) {
            justCompletedCycle = false
            return true
        }
        return false
    }

    /**
     * Handles skipping the cycle break
     */
    fun skipCycleBreak() {
        if (isInCycleBreak) {
            isInCycleBreak = false
        }
    }
}