package com.enaboapps.switchify.service.scanning.tree

import com.enaboapps.switchify.service.scanning.ScanDirection
import com.enaboapps.switchify.service.scanning.ScanNodeInterface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanTreeNavigatorTest {
    @Test
    fun rowEndEntersEscapeStateWithoutWrappingToFirstNode() {
        val navigator = navigatorForRow("first", "last").apply {
            isInTreeItem = true
            scanDirection = ScanDirection.RIGHT
            currentColumn = 1
        }

        val moved = navigator.moveSelectionToNext()

        assertFalse(moved)
        assertTrue(navigator.handleEscape())
        assertEquals(1, navigator.currentColumn)
    }

    @Test
    fun confirmingRowEndEscapeLeavesCurrentRow() {
        val navigator = navigatorForRow("first", "last").apply {
            isInTreeItem = true
            scanDirection = ScanDirection.RIGHT
            currentColumn = 1
        }

        navigator.moveSelectionToNext()
        val confirmed = navigator.confirmEscape()

        assertTrue(confirmed)
        assertFalse(navigator.isInTreeItem)
        assertFalse(navigator.handleEscape())
    }

    @Test
    fun denyingRowEndEscapeStartsNextCycleAtFirstNode() {
        val navigator = navigatorForRow("first", "last").apply {
            isInTreeItem = true
            scanDirection = ScanDirection.RIGHT
            currentColumn = 1
        }

        navigator.moveSelectionToNext()
        val denied = navigator.denyEscape()

        assertTrue(denied)
        assertEquals(0, navigator.currentColumn)
        assertEquals(1, navigator.currentCycle)
        assertFalse(navigator.handleEscape())
    }

    @Test
    fun directionChangeResetsCycleProgressOnlyWhenValueChanges() {
        val navigator = navigatorForRow("first", "last").apply {
            scanDirection = ScanDirection.RIGHT
            currentCycle = 2
        }

        navigator.scanDirection = ScanDirection.RIGHT

        assertEquals(2, navigator.currentCycle)

        navigator.scanDirection = ScanDirection.LEFT

        assertEquals(0, navigator.currentCycle)
    }

    @Test
    fun enteringAndLeavingRowResetCycleProgress() {
        val navigator = navigatorForRow("first", "last").apply {
            currentCycle = 2
        }

        navigator.isInTreeItem = true

        assertEquals(0, navigator.currentCycle)

        navigator.scanDirection = ScanDirection.RIGHT
        navigator.currentCycle = 2
        navigator.currentColumn = 1
        navigator.moveSelectionToNext()
        navigator.confirmEscape()

        assertEquals(0, navigator.currentCycle)
        assertFalse(navigator.isInTreeItem)
    }

    @Test
    fun enteringAndLeavingGroupResetCycleProgress() {
        val navigator = navigatorForGroupedRow().apply {
            isInTreeItem = true
            currentCycle = 2
        }

        navigator.selectGroup()

        assertEquals(0, navigator.currentCycle)
        assertTrue(navigator.isInGroup)

        navigator.currentCycle = 2
        navigator.currentColumn = 1
        navigator.moveSelectionToNext()
        navigator.confirmEscape()

        assertEquals(0, navigator.currentCycle)
        assertFalse(navigator.isInGroup)
        assertTrue(navigator.isScanningGroups)
    }

    @Test
    fun spatialPositionResetsCycleProgress() {
        val navigator = navigatorForRow("first", "last").apply {
            currentCycle = 2
        }

        val positioned = navigator.setSpatialPosition(treeIndex = 0, nodeIndex = 1)

        assertTrue(positioned)
        assertEquals(0, navigator.currentCycle)
        assertEquals(1, navigator.currentColumn)
        assertTrue(navigator.isInTreeItem)
    }

    @Test
    fun ordinaryMovementWithinContextPreservesCycleProgress() {
        val navigator = navigatorForRow("first", "middle", "last").apply {
            isInTreeItem = true
            scanDirection = ScanDirection.RIGHT
            currentCycle = 2
        }

        navigator.moveSelectionToNext()

        assertEquals(2, navigator.currentCycle)
        assertEquals(1, navigator.currentColumn)
    }

    @Test
    fun contextChangeClearsPendingCycleCompletion() {
        val navigator = navigatorForRow("first", "last").apply {
            isInTreeItem = true
            scanDirection = ScanDirection.RIGHT
            currentColumn = 1
        }
        navigator.moveSelectionToNext()
        navigator.denyEscape()

        navigator.scanDirection = ScanDirection.LEFT

        assertFalse(navigator.hasCompletedCycle())
        assertEquals(0, navigator.currentCycle)
    }

    @Test
    fun contextChangeCancelsActiveCycleBreakExactlyOnce() {
        var cancellationCount = 0
        val navigator = navigatorForRows(
            row("only"),
            settings = TestNavigatorSettings(),
            hasCycleBreak = { true },
            onCycleBreakCancelled = { cancellationCount++ }
        )

        navigator.moveSelectionToNext()
        assertTrue(navigator.isInCycleBreak)

        navigator.isInTreeItem = true
        navigator.scanDirection = ScanDirection.RIGHT

        assertFalse(navigator.isInCycleBreak)
        assertEquals(0, navigator.currentCycle)
        assertEquals(1, cancellationCount)
    }

    @Test
    fun resetCancelsActiveCycleBreakExactlyOnce() {
        var cancellationCount = 0
        val navigator = navigatorForRows(
            row("only"),
            settings = TestNavigatorSettings(),
            hasCycleBreak = { true },
            onCycleBreakCancelled = { cancellationCount++ }
        )
        navigator.moveSelectionToNext()

        navigator.reset()

        assertEquals(0, navigator.currentCycle)
        assertFalse(navigator.isInCycleBreak)
        assertFalse(navigator.hasCompletedCycle())
        assertEquals(1, cancellationCount)
    }

    @Test
    fun resetAfterUiCleanupDoesNotDuplicateCancellationCallback() {
        var cancellationCount = 0
        val navigator = navigatorForRows(
            row("only"),
            settings = TestNavigatorSettings(),
            hasCycleBreak = { true },
            onCycleBreakCancelled = { cancellationCount++ }
        )
        navigator.moveSelectionToNext()

        navigator.resetAfterCycleBreakUiCleanup()

        assertEquals(0, navigator.currentCycle)
        assertFalse(navigator.isInCycleBreak)
        assertFalse(navigator.hasCompletedCycle())
        assertEquals(0, cancellationCount)
    }

    @Test
    fun newlyEnteredRowReceivesFullAutomaticCycleAllowance() {
        val navigator = navigatorForRow(
            "first",
            "last",
            settings = TestNavigatorSettings(autoScan = true)
        ).apply {
            currentCycle = 3
        }

        navigator.isInTreeItem = true

        assertFalse(navigator.isAutoScanCycleLimitReached())
        repeat(3) {
            navigator.scanDirection = ScanDirection.RIGHT
            navigator.currentColumn = 1
            navigator.moveSelectionToNext()
            navigator.denyEscape()
        }
        assertTrue(navigator.isAutoScanCycleLimitReached())
    }

    @Test
    fun newlyEnteredGroupReceivesFullAutomaticCycleAllowance() {
        val navigator = navigatorForRows(
            row("first", "second", "third", "fourth", groupScan = true),
            settings = TestNavigatorSettings(groupScan = true, autoScan = true)
        ).apply {
            isInTreeItem = true
            currentCycle = 3
        }

        navigator.selectGroup()

        assertFalse(navigator.isAutoScanCycleLimitReached())
        repeat(3) {
            navigator.currentColumn = 1
            navigator.moveSelectionToNext()
            navigator.denyEscape()
        }
        assertTrue(navigator.isAutoScanCycleLimitReached())
    }

    private fun navigatorForRow(
        vararg ids: String,
        settings: TestNavigatorSettings = TestNavigatorSettings()
    ): ScanTreeNavigator = navigatorForRows(row(*ids), settings = settings)

    private fun navigatorForGroupedRow(): ScanTreeNavigator = navigatorForRows(
        row("first", "second", "third", "fourth", groupScan = true),
        settings = TestNavigatorSettings(groupScan = true),
    )

    private fun navigatorForRows(
        vararg rows: ScanTreeItem,
        settings: TestNavigatorSettings,
        hasCycleBreak: () -> Boolean = { false },
        onCycleBreakCancelled: () -> Unit = {}
    ): ScanTreeNavigator = ScanTreeNavigator(
        tree = rows.toList(),
        scanSettings = settings,
        hasCycleBreak = hasCycleBreak,
        onCycleBreakCancelled = onCycleBreakCancelled
    )

    private fun row(
        vararg ids: String,
        groupScan: Boolean = false
    ): ScanTreeItem {
        return ScanTreeItem(
            children = ids.mapIndexed { index, id ->
                TestScanNode(id = id, nodeLeft = index * 100)
            },
            y = 0,
            isGroupScanEnabled = groupScan
        )
    }

    private class TestNavigatorSettings(
        private val groupScan: Boolean = false,
        private val autoScan: Boolean = false
    ) : ScanTreeNavigatorSettings {
        override fun isRowColumnScanEnabled(): Boolean = true
        override fun isDirectionalScanMode(): Boolean = false
        override fun isGroupScanEnabled(): Boolean = groupScan
        override fun getScanCycles(): Int = 3
        override fun isAutoScanMode(): Boolean = autoScan
    }

    private data class TestScanNode(
        val id: String,
        val nodeLeft: Int
    ) : ScanNodeInterface {
        override fun getLeft(): Int = nodeLeft
        override fun getTop(): Int = 0
        override fun getMidX(): Int = nodeLeft + 10
        override fun getMidY(): Int = 10
        override fun getWidth(): Int = 20
        override fun getHeight(): Int = 20
        override fun getContentDescription(): String = id
        override fun highlight() = Unit
        override fun unhighlight() = Unit
        override fun select() = Unit
    }
}
