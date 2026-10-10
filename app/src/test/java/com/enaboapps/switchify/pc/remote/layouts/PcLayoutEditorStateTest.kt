package com.enaboapps.switchify.pc.remote.layouts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PcLayoutEditorStateTest {
    private val start = PcButtonLayout.initial(listOf("click.double", "key.Enter"))
    private val available = setOf("click.double", "key.Enter", "window.closeFocused", "draft.send")

    private fun editor(initiallyCustomized: Boolean = true) = PcLayoutEditorState(
        surface = PcLayoutSurface.Mouse,
        initial = start,
        defaultLayout = start,
        initiallyCustomized = initiallyCustomized
    )

    private fun cell(index: Int) = PcLayoutSelection(PcLayoutSelectionKind.Cell, index)
    private fun row(index: Int) = PcLayoutSelection(PcLayoutSelectionKind.Row, index)
    private fun column(index: Int) = PcLayoutSelection(PcLayoutSelectionKind.Column, index)

    private fun PcLayoutEditorState.saved(): PcButtonLayout? =
        (saveRequest() as PcLayoutSaveRequest.Save).layout

    @Test
    fun movesAndSwapsUsingSelectableCells() {
        val state = editor().select(cell(0)).startMove().select(cell(1))
        assertEquals(PcButtonLayout(3, listOf("key.Enter", "click.double", null)), state.saved())
        assertNull(state.selected)
        assertFalse(state.moving)
        assertEquals(PcLayoutAnnouncement.CellMoved(1, 2), state.announcement)
    }

    @Test
    fun removesAControlAndRestoresItFromAnEmptyCell() {
        val removed = editor().select(cell(0)).removeSelectedCell()
        assertEquals(listOf(null, "key.Enter", null), removed.draft.cells)
        val picking = removed.select(cell(2))
        assertEquals(2, picking.pickerCell)
        val assigned = picking.assign("click.double", available)
        assertNull(assigned.pickerCell)
        assertEquals(PcButtonLayout(3, listOf(null, "key.Enter", "click.double")), assigned.saved())
        assertEquals(PcLayoutAnnouncement.Assigned("click.double", 1, 3), assigned.announcement)
    }

    @Test
    fun reordersAWholeRowOrColumn() {
        val grown = editor().resize(PcLayoutAxis.Row, 1, true)
        assertEquals(
            listOf(null, null, null, "click.double", "key.Enter", null),
            grown.select(row(0)).startMove().select(row(1)).draft.cells
        )
        assertEquals(
            listOf("key.Enter", null, "click.double", null, null, null),
            grown.select(column(0)).startMove().select(column(2)).draft.cells
        )
        assertEquals(
            PcLayoutAnnouncement.TrackMoved(PcLayoutSelectionKind.Column, 1, 3),
            grown.select(column(0)).startMove().select(column(2)).announcement
        )
    }

    @Test
    fun moveIgnoresDestinationsOfAnotherKind() {
        val moving = editor().select(row(0)).startMove()
        assertSame(moving, moving.select(cell(1)))
    }

    @Test
    fun insertsAfterASelectedTrackAndRemovesAnOccupiedColumn() {
        val inserted = editor().select(column(0)).let { it.resize(PcLayoutAxis.Column, it.selected!!.index + 1, true) }
        assertEquals(PcButtonLayout(4, listOf("click.double", null, "key.Enter", null)), inserted.draft)
        assertTrue(inserted.isTrackOccupied(PcLayoutAxis.Column, 0))
        assertFalse(inserted.isTrackOccupied(PcLayoutAxis.Column, 1))
        val removed = inserted.select(column(0)).resize(PcLayoutAxis.Column, 0, false)
        assertEquals(PcButtonLayout(3, listOf(null, "key.Enter", null)), removed.saved())
    }

    @Test
    fun enforcesGridLimits() {
        var state = editor()
        repeat(30) { state = state.resize(PcLayoutAxis.Row, state.rows, true) }
        assertEquals(PcButtonLayout.MAX_ROWS, state.rows)
        assertFalse(state.canInsert(PcLayoutAxis.Row))
        repeat(5) { state = state.resize(PcLayoutAxis.Column, state.draft.columns, true) }
        assertEquals(PcButtonLayout.MAX_COLUMNS, state.draft.columns)
        assertFalse(state.canInsert(PcLayoutAxis.Column))
        val single = editor().copy(draft = PcButtonLayout(1, listOf("click.double")))
        assertFalse(single.canRemove(PcLayoutAxis.Row))
        assertFalse(single.canRemove(PcLayoutAxis.Column))
        assertSame(single.draft, single.resize(PcLayoutAxis.Row, 0, false).draft)
    }

    @Test
    fun leavesAdaptiveDefaultsUntouchedOnAnUnchangedSave() {
        assertEquals(PcLayoutSaveRequest.None, editor(initiallyCustomized = false).saveRequest())
        assertEquals(PcLayoutSaveRequest.Save(start), editor().saveRequest())
        val noOp = editor(initiallyCustomized = false).select(cell(0)).closeActions()
        assertEquals(PcLayoutSaveRequest.None, noOp.saveRequest())
        assertFalse(editor().resize(PcLayoutAxis.Row, 99, true).dirty)
    }

    @Test
    fun resetSavesNullUntilAnotherChange() {
        val changed = editor().resize(PcLayoutAxis.Row, 1, true)
        val reset = changed.resetToDefault()
        assertEquals(start, reset.draft)
        assertTrue(reset.dirty)
        assertEquals(PcLayoutSaveRequest.Save(null), reset.saveRequest())
        val edited = reset.select(cell(0)).removeSelectedCell()
        assertFalse(edited.reset)
        assertEquals(PcButtonLayout(3, listOf(null, "key.Enter", null)), edited.saved())
    }

    @Test
    fun pickerFiltersPlacedAndForbiddenActionsAndAssignsOnlyToTheDraft() {
        val options = listOf("click.double", "key.Enter", "window.closeFocused", "draft.send").map {
            PcActionOption(it, it, "Category", emptyList())
        }
        val picking = editor().select(cell(2))
        assertEquals(listOf("window.closeFocused"), picking.pickerOptions(options).map { it.id })
        val assigned = picking.assign("window.closeFocused", available)
        assertEquals(listOf("click.double", "key.Enter", "window.closeFocused"), assigned.draft.cells)
        assertEquals(start, assigned.initial)
    }

    @Test
    fun rejectsDuplicateForbiddenOrUnavailableAssignments() {
        val picking = editor().select(cell(2))
        listOf("click.double", "draft.send", "scroll.up", "unknown").forEach { id ->
            val result = picking.assign(id, available)
            assertNull(id, result.pickerCell)
            assertSame(id, picking.draft, result.draft)
            assertFalse(id, result.dirty)
        }
        val idle = editor()
        assertSame(idle, idle.assign("window.closeFocused", available))
        val filled = idle.copy(pickerCell = 0)
        assertEquals(start, filled.assign("window.closeFocused", available).draft)
    }

    @Test
    fun backClosesThePickerWithoutChangingItsCell() {
        val closed = editor().select(cell(2)).closePicker()
        assertNull(closed.pickerCell)
        assertEquals(start, closed.draft)
        assertFalse(closed.dirty)
    }

    @Test
    fun usesAnEmptyCellAsAMoveDestinationBeforeThePicker() {
        val moved = editor().select(cell(0)).startMove().select(cell(2))
        assertNull(moved.pickerCell)
        assertEquals(listOf(null, "key.Enter", "click.double"), moved.draft.cells)
    }

    @Test
    fun emptyCellsCannotStartAMove() {
        val selected = editor().copy(selected = cell(2))
        assertFalse(selected.canStartMove())
        assertSame(selected, selected.startMove())
        assertSame(selected, selected.removeSelectedCell())
    }

    @Test
    fun nudgesCellsAndKeepsThemSelected() {
        val grown = editor().resize(PcLayoutAxis.Row, 1, true).select(cell(0))
        assertNull(grown.nudgeTarget(PcLayoutNudge.Up))
        assertNull(grown.nudgeTarget(PcLayoutNudge.Left))
        val right = grown.nudge(PcLayoutNudge.Right)
        assertEquals(listOf("key.Enter", "click.double", null, null, null, null), right.draft.cells)
        assertEquals(cell(1), right.selected)
        val down = right.nudge(PcLayoutNudge.Down)
        assertEquals(listOf("key.Enter", null, null, null, "click.double", null), down.draft.cells)
        assertEquals(cell(4), down.selected)
        assertNull(down.nudgeTarget(PcLayoutNudge.Down))
        assertEquals(PcLayoutAnnouncement.CellMoved(2, 2), down.announcement)
        assertEquals(cell(4), down.nudge(PcLayoutNudge.Right).nudge(PcLayoutNudge.Left).selected)
    }

    @Test
    fun nudgesRowsAndColumnsInTheirOwnAxisOnly() {
        val grown = editor().resize(PcLayoutAxis.Row, 1, true)
        val rowSelected = grown.select(row(0))
        assertNull(rowSelected.nudgeTarget(PcLayoutNudge.Up))
        assertNull(rowSelected.nudgeTarget(PcLayoutNudge.Left))
        val down = rowSelected.nudge(PcLayoutNudge.Down)
        assertEquals(listOf(null, null, null, "click.double", "key.Enter", null), down.draft.cells)
        assertEquals(row(1), down.selected)
        val columnSelected = grown.select(column(2))
        assertNull(columnSelected.nudgeTarget(PcLayoutNudge.Right))
        assertNull(columnSelected.nudgeTarget(PcLayoutNudge.Down))
        val left = columnSelected.nudge(PcLayoutNudge.Left)
        assertEquals(listOf("click.double", null, "key.Enter", null, null, null), left.draft.cells)
        assertEquals(column(1), left.selected)
    }

    @Test
    fun nudgingIsUnavailableWhileMoving() {
        val moving = editor().select(cell(0)).startMove()
        assertNull(moving.nudgeTarget(PcLayoutNudge.Right))
        assertSame(moving, moving.nudge(PcLayoutNudge.Right))
    }

    @Test
    fun ignoresOutOfRangeSelections() {
        val state = editor()
        listOf(cell(-1), cell(3), row(1), column(3)).forEach { assertSame(it.toString(), state, state.select(it)) }
    }

    @Test
    fun announcementsCountRepeatedMessages() {
        val once = editor().select(cell(0)).nudge(PcLayoutNudge.Right)
        val twice = once.nudge(PcLayoutNudge.Left)
        assertEquals(once.announcementCount + 1, twice.announcementCount)
    }
}
