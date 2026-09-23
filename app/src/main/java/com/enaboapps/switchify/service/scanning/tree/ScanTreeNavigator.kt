package com.enaboapps.switchify.service.scanning.tree

import com.enaboapps.switchify.service.scanning.ScanDirection
import com.enaboapps.switchify.service.scanning.ScanNodeInterface
import com.enaboapps.switchify.service.scanning.ScanSettings

/**
 * This class is responsible for navigating through the ScanTree structure.
 * It manages the current position and provides methods for moving between items, groups, and nodes.
 * The class supports row-column and sequential scanning modes.
 *
 * @property tree The list of ScanTreeItems that make up the scanning tree.
 * @property scanSettings The settings for scanning behavior.
 * @property hasCycleBreak Indicates whether scanning includes a break between cycles
 */
internal interface ScanTreeNavigatorSettings {
    fun isRowColumnScanEnabled(): Boolean
    fun isGroupScanEnabled(): Boolean
    fun getScanCycles(): Int
    fun isAutoScanMode(): Boolean
}

private class ScanSettingsNavigatorAdapter(
    private val scanSettings: ScanSettings
) : ScanTreeNavigatorSettings {
    override fun isRowColumnScanEnabled(): Boolean = scanSettings.isRowColumnScanEnabled()
    override fun isGroupScanEnabled(): Boolean = scanSettings.isGroupScanEnabled()
    override fun getScanCycles(): Int = scanSettings.getScanCycles()
    override fun isAutoScanMode(): Boolean = scanSettings.isAutoScanMode()
}

class ScanTreeNavigator internal constructor(
    private val tree: List<ScanTreeItem>,
    private val scanSettings: ScanTreeNavigatorSettings,
    private val hasCycleBreak: () -> Boolean = { false },
    private val onCycleBreakCancelled: () -> Unit = {}
) {
    constructor(
        tree: List<ScanTreeItem>,
        scanSettings: ScanSettings,
        hasCycleBreak: () -> Boolean = { false }
    ) : this(
        tree,
        ScanSettingsNavigatorAdapter(scanSettings),
        hasCycleBreak
    )

    internal constructor(
        tree: List<ScanTreeItem>,
        scanSettings: ScanSettings,
        hasCycleBreak: () -> Boolean,
        onCycleBreakCancelled: () -> Unit
    ) : this(
        tree,
        ScanSettingsNavigatorAdapter(scanSettings),
        hasCycleBreak,
        onCycleBreakCancelled
    )

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
        set(value) {
            if (field != value) {
                resetCycleProgress()
                field = value
            }
        }

    /** The index of the current column within the current group or the current node in non-row-column mode. */
    var currentColumn = 0

    /** Indicates whether the scanning is currently within a tree item. */
    var isInTreeItem = false
        set(value) {
            if (field != value) {
                resetCycleProgress()
                field = value
            }
        }

    /** Tracks the current cycle of the scanning tree */
    var currentCycle = 0

    /** Indicates whether we're scanning groups or items within a group. */
    var isScanningGroups = scanSettings.isGroupScanEnabled()
        set(value) {
            if (field != value) {
                resetCycleProgress()
                field = value
            }
        }

    /** Indicates whether we're in the cycle break */
    var isInCycleBreak = false

    /** Flag to track if we just completed a cycle */
    private var justCompletedCycle = false

    /** The current direction of scanning. */
    var scanDirection = ScanDirection.DOWN
        set(value) {
            if (field != value) {
                resetCycleProgress()
                field = value
            }
        }

    /** The current escape state */
    private var escapeState: EscapeState = EscapeState.None

    /** Indicates whether row-column scanning is enabled based on scan settings. */
    private val isRowColumnScanEnabled: Boolean
        get() = scanSettings.isRowColumnScanEnabled()

    /**
     * A flattened list of all nodes in the tree, used when row-column scanning is disabled.
     * Computed lazily to avoid unnecessary processing when row-column scanning is enabled.
     */
    private var flattenedNodes: List<ScanNodeInterface> = tree.flatMap { it.children }

    internal fun reconcile(
        replacement: List<ScanTreeItem>,
        matches: Map<ScanNodeInterface, ScanNodeInterface>
    ): Boolean {
        val reverse = scanDirection == ScanDirection.UP || scanDirection == ScanDirection.LEFT
        val rowLevel = isRowColumnScanEnabled && (!isInTreeItem || escapeState == EscapeState.Item)
        val groupLevel = isRowColumnScanEnabled && !rowLevel &&
            (escapeState == EscapeState.Group || (isScanningGroups && tree.getOrNull(currentTreeItem)?.isGrouped() == true))
        data class Target(val row: Int, val group: Int, val column: Int, val members: List<ScanNodeInterface>)
        fun targets(items: List<ScanTreeItem>): List<Target> {
            var flatIndex = 0
            return items.flatMapIndexed { row, item ->
                when {
                    rowLevel -> listOf(Target(row, 0, 0, item.children))
                    groupLevel -> (0 until item.getGroupCount()).map { group ->
                        Target(row, group, 0, (0 until item.getNodeCount(group)).mapNotNull { item.getNode(group, it) })
                    }
                    else -> (0 until item.getGroupCount()).flatMap { group ->
                        (0 until item.getNodeCount(group)).mapNotNull { column ->
                            item.getNode(group, column)?.let { node ->
                                Target(row, group, if (isRowColumnScanEnabled) column else flatIndex++, listOf(node))
                            }
                        }
                    }
                }
            }
        }
        val oldTargets = targets(tree)
        val nextTargets = targets(replacement)
        if (nextTargets.isEmpty()) return false
        val oldIndex = oldTargets.indexOfFirst {
            if (!isRowColumnScanEnabled) it.column == currentColumn
            else it.row == currentTreeItem && (rowLevel || it.group == currentGroup && (groupLevel || it.column == currentColumn))
        }
        val targetByNode = java.util.IdentityHashMap<ScanNodeInterface, Int>()
        nextTargets.forEachIndexed { index, target -> target.members.forEach { targetByNode[it] = index } }
        fun corresponding(target: Target): Int? {
            val counts = mutableMapOf<Int, Int>()
            val order = if (reverse) target.members.asReversed() else target.members
            val ranks = mutableMapOf<Int, Int>()
            order.forEachIndexed { rank, old ->
                matches[old]?.let { targetByNode[it] }?.let { index ->
                    counts[index] = (counts[index] ?: 0) + 1
                    ranks.putIfAbsent(index, rank)
                }
            }
            return counts.keys.minWithOrNull(compareByDescending<Int> { counts[it] }.thenBy { ranks[it] })
        }
        val currentMatch = oldTargets.getOrNull(oldIndex)?.let(::corresponding)
        var selected = currentMatch
        if (selected == null && oldIndex >= 0) {
            for (offset in 1 until oldTargets.size) {
                val index = Math.floorMod(oldIndex + if (reverse) -offset else offset, oldTargets.size)
                selected = corresponding(oldTargets[index])
                if (selected != null) break
            }
        }
        val target = nextTargets[selected ?: if (reverse) nextTargets.lastIndex else 0]
        currentTreeItem = target.row
        currentGroup = target.group
        currentColumn = target.column
        flattenedNodes = replacement.flatMap { it.children }
        if (currentMatch == null) {
            escapeState = EscapeState.None
            if (rowLevel) {
                isInTreeItem = false
                isInGroup = false
            }
            resetCycleProgress()
        }
        return currentMatch != null
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

        return if (!isRowColumnScanEnabled) {
            handleSequentialMovement()
        } else {
            when (scanDirection) {
                ScanDirection.DOWN, ScanDirection.RIGHT -> moveSelectionToNext()
                ScanDirection.UP, ScanDirection.LEFT -> moveSelectionToPrevious()
            }
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

        return if (!isRowColumnScanEnabled) {
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

        return if (!isRowColumnScanEnabled) {
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
            handleCycleCompletion(forward = true)
        }
        return true
    }

    private fun moveSequentialPrevious(): Boolean {
        if (currentColumn > 0) {
            currentColumn--
        } else {
            currentColumn = flattenedNodes.size - 1
            handleCycleCompletion(forward = false)
        }
        return true
    }

    private fun moveSelectionToNextWithinGroup(): Boolean {
        val currentItem = getCurrentItem()
        return when {
            currentColumn < currentItem.getNodeCount(currentGroup) - 1 -> {
                currentColumn++
                true
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
            handleCycleCompletion(forward = true)
        }
        resetGroupAndColumn()
        return true
    }

    private fun moveSelectionToPreviousTreeItem(): Boolean {
        if (currentTreeItem > 0) {
            currentTreeItem--
        } else {
            currentTreeItem = tree.size - 1
            handleCycleCompletion(forward = false)
        }
        resetGroupAndColumn()
        return true
    }

    /**
     * Handles cycle completion and break logic
     */
    private fun handleCycleCompletion(forward: Boolean) {
        if (isInCycleBreak) {
            isInCycleBreak = false
        } else if (forward && hasCycleBreak()) {
            isInCycleBreak = true
        }
        justCompletedCycle = true
        currentCycle++
    }

    internal fun resetCycleProgress(notifyCycleBreakCancellation: Boolean = true) {
        val wasInCycleBreak = isInCycleBreak
        currentCycle = 0
        justCompletedCycle = false
        isInCycleBreak = false
        if (wasInCycleBreak && notifyCycleBreakCancellation) {
            onCycleBreakCancelled()
        }
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
     * @return True if an escape was handled, false otherwise.
     */
    fun handleEscape(): Boolean = escapeState != EscapeState.None &&
            !isInCycleBreak

    /**
     * Checks if the auto scan cycle limit has been reached.
     * @return True if the auto scan cycle limit has been reached, false otherwise.
     */
    fun isAutoScanCycleLimitReached(): Boolean {
        val userDefinedCycles = scanSettings.getScanCycles()
        return currentCycle >= userDefinedCycles && scanSettings.isAutoScanMode()
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
        handleCycleCompletion(forward = scanDirection == ScanDirection.RIGHT)
    }

    private fun handleGroupEscapeDenial() {
        escapeState = EscapeState.None
        currentColumn = if (scanDirection == ScanDirection.RIGHT) 0 else {
            if (isRowColumnScanEnabled) getCurrentItem().getNodeCount(currentGroup) - 1
            else flattenedNodes.size - 1
        }
        handleCycleCompletion(forward = scanDirection == ScanDirection.RIGHT)
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
        resetState(notifyCycleBreakCancellation = true)
    }

    internal fun resetAfterCycleBreakUiCleanup() {
        resetState(notifyCycleBreakCancellation = false)
    }

    private fun resetState(notifyCycleBreakCancellation: Boolean) {
        resetCycleProgress(notifyCycleBreakCancellation)
        currentTreeItem = 0
        currentGroup = 0
        currentColumn = 0

        isInTreeItem = false
        isInGroup = false
        isScanningGroups = scanSettings.isGroupScanEnabled()

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
            if (isInGroup) {
                getCurrentItem().getNode(currentGroup, currentColumn)
            } else {
                getCurrentItem().children.getOrNull(currentColumn)
            }
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
