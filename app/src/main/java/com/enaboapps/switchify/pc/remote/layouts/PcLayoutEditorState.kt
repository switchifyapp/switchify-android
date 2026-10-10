package com.enaboapps.switchify.pc.remote.layouts

import com.enaboapps.switchify.pc.remote.actions.PcActionCatalog

enum class PcLayoutSelectionKind {
    Cell,
    Row,
    Column
}

data class PcLayoutSelection(val kind: PcLayoutSelectionKind, val index: Int)

enum class PcLayoutNudge {
    Up,
    Down,
    Left,
    Right
}

sealed class PcLayoutAnnouncement {
    data class CellMoved(val row: Int, val column: Int) : PcLayoutAnnouncement()
    data class TrackMoved(val kind: PcLayoutSelectionKind, val from: Int, val to: Int) : PcLayoutAnnouncement()
    data class Assigned(val actionId: String, val row: Int, val column: Int) : PcLayoutAnnouncement()
    data class Removed(val row: Int, val column: Int) : PcLayoutAnnouncement()
}

sealed class PcLayoutSaveRequest {
    data object None : PcLayoutSaveRequest()
    data class Save(val layout: PcButtonLayout?) : PcLayoutSaveRequest()
}

data class PcLayoutEditorState(
    val surface: PcLayoutSurface,
    val initial: PcButtonLayout,
    val defaultLayout: PcButtonLayout,
    val initiallyCustomized: Boolean,
    val draft: PcButtonLayout = initial,
    val reset: Boolean = false,
    val dirty: Boolean = false,
    val selected: PcLayoutSelection? = null,
    val moving: Boolean = false,
    val pickerCell: Int? = null,
    val announcement: PcLayoutAnnouncement? = null,
    val announcementCount: Int = 0
) {
    val rows: Int get() = draft.rows

    fun rowOf(cell: Int): Int = cell / draft.columns

    fun columnOf(cell: Int): Int = cell % draft.columns

    fun select(selection: PcLayoutSelection): PcLayoutEditorState {
        if (!contains(selection)) return this
        val current = selected
        if (moving && current != null) {
            return if (current.kind == selection.kind) finishMove(current, selection.index) else this
        }
        if (selection.kind == PcLayoutSelectionKind.Cell && draft.cells[selection.index] == null) {
            return copy(selected = null, moving = false, pickerCell = selection.index)
        }
        return copy(selected = selection, moving = false)
    }

    fun canStartMove(): Boolean {
        val current = selected ?: return false
        return current.kind != PcLayoutSelectionKind.Cell || draft.cells[current.index] != null
    }

    fun startMove(): PcLayoutEditorState = if (canStartMove()) copy(moving = true) else this

    fun closeActions(): PcLayoutEditorState = copy(selected = null, moving = false)

    fun nudgeTarget(direction: PcLayoutNudge): Int? {
        val current = selected ?: return null
        if (moving) return null
        val columns = draft.columns
        return when (current.kind) {
            PcLayoutSelectionKind.Cell -> {
                if (draft.cells.getOrNull(current.index) == null) return null
                val row = rowOf(current.index)
                val column = columnOf(current.index)
                when (direction) {
                    PcLayoutNudge.Up -> if (row > 0) current.index - columns else null
                    PcLayoutNudge.Down -> if (row < rows - 1) current.index + columns else null
                    PcLayoutNudge.Left -> if (column > 0) current.index - 1 else null
                    PcLayoutNudge.Right -> if (column < columns - 1) current.index + 1 else null
                }
            }
            PcLayoutSelectionKind.Row -> when (direction) {
                PcLayoutNudge.Up -> (current.index - 1).takeIf { it >= 0 }
                PcLayoutNudge.Down -> (current.index + 1).takeIf { it < rows }
                else -> null
            }
            PcLayoutSelectionKind.Column -> when (direction) {
                PcLayoutNudge.Left -> (current.index - 1).takeIf { it >= 0 }
                PcLayoutNudge.Right -> (current.index + 1).takeIf { it < columns }
                else -> null
            }
        }
    }

    fun nudge(direction: PcLayoutNudge): PcLayoutEditorState {
        val current = selected ?: return this
        val target = nudgeTarget(direction) ?: return this
        return finishMove(current, target).copy(selected = PcLayoutSelection(current.kind, target))
    }

    fun removeSelectedCell(): PcLayoutEditorState {
        val current = selected ?: return this
        if (current.kind != PcLayoutSelectionKind.Cell || draft.cells.getOrNull(current.index) == null) return this
        return change(PcButtonLayout.setCell(draft, current.index, null))
            .copy(selected = null, moving = false)
            .announce(PcLayoutAnnouncement.Removed(rowOf(current.index) + 1, columnOf(current.index) + 1))
    }

    fun canInsert(axis: PcLayoutAxis): Boolean =
        if (axis == PcLayoutAxis.Row) rows < PcButtonLayout.MAX_ROWS else draft.columns < PcButtonLayout.MAX_COLUMNS

    fun canRemove(axis: PcLayoutAxis): Boolean = (if (axis == PcLayoutAxis.Row) rows else draft.columns) > 1

    fun isTrackOccupied(axis: PcLayoutAxis, index: Int): Boolean =
        draft.cells.withIndex().any { (cell, id) ->
            id != null && (if (axis == PcLayoutAxis.Row) rowOf(cell) else columnOf(cell)) == index
        }

    fun resize(axis: PcLayoutAxis, index: Int, insert: Boolean): PcLayoutEditorState =
        change(PcButtonLayout.resize(draft, axis, index, insert)).copy(selected = null, moving = false)

    fun resetToDefault(): PcLayoutEditorState =
        copy(draft = defaultLayout, reset = true, dirty = true, selected = null, moving = false)

    fun pickerOptions(options: List<PcActionOption>): List<PcActionOption> =
        options.filter { PcActionCatalog.canPlace(it.id, surface) && it.id !in draft.cells }

    fun assign(id: String, available: Collection<String>): PcLayoutEditorState {
        val cell = pickerCell ?: return this
        if (
            draft.cells.getOrNull(cell) != null ||
            !PcActionCatalog.canPlace(id, surface) ||
            id in draft.cells ||
            id !in available
        ) {
            return closePicker()
        }
        return change(PcButtonLayout.setCell(draft, cell, id))
            .copy(pickerCell = null)
            .announce(PcLayoutAnnouncement.Assigned(id, rowOf(cell) + 1, columnOf(cell) + 1))
    }

    fun closePicker(): PcLayoutEditorState = copy(pickerCell = null)

    fun saveRequest(): PcLayoutSaveRequest = when {
        dirty -> PcLayoutSaveRequest.Save(if (reset) null else draft)
        initiallyCustomized -> PcLayoutSaveRequest.Save(initial)
        else -> PcLayoutSaveRequest.None
    }

    private fun finishMove(source: PcLayoutSelection, to: Int): PcLayoutEditorState {
        val next = when (source.kind) {
            PcLayoutSelectionKind.Cell -> PcButtonLayout.moveCell(draft, source.index, to)
            PcLayoutSelectionKind.Row -> PcButtonLayout.moveTrack(draft, PcLayoutAxis.Row, source.index, to)
            PcLayoutSelectionKind.Column -> PcButtonLayout.moveTrack(draft, PcLayoutAxis.Column, source.index, to)
        }
        val announcement = if (source.kind == PcLayoutSelectionKind.Cell) {
            PcLayoutAnnouncement.CellMoved(rowOf(to) + 1, columnOf(to) + 1)
        } else {
            PcLayoutAnnouncement.TrackMoved(source.kind, source.index + 1, to + 1)
        }
        return change(next).copy(selected = null, moving = false).announce(announcement)
    }

    private fun change(next: PcButtonLayout): PcLayoutEditorState =
        if (next === draft) this else copy(draft = next, reset = false, dirty = true)

    private fun announce(value: PcLayoutAnnouncement): PcLayoutEditorState =
        copy(announcement = value, announcementCount = announcementCount + 1)

    private fun contains(selection: PcLayoutSelection): Boolean = selection.index >= 0 && when (selection.kind) {
        PcLayoutSelectionKind.Cell -> selection.index < draft.cells.size
        PcLayoutSelectionKind.Row -> selection.index < rows
        PcLayoutSelectionKind.Column -> selection.index < draft.columns
    }
}
