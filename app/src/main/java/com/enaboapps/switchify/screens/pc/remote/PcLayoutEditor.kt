package com.enaboapps.switchify.screens.pc.remote

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.enaboapps.switchify.R
import com.enaboapps.switchify.components.ActionButton
import com.enaboapps.switchify.components.ActionButtonType
import com.enaboapps.switchify.pc.remote.actions.PcResolvedAction
import com.enaboapps.switchify.pc.remote.layouts.PcActionOption
import com.enaboapps.switchify.pc.remote.layouts.PcActionPickerModel
import com.enaboapps.switchify.pc.remote.layouts.PcButtonLayout
import com.enaboapps.switchify.pc.remote.layouts.PcLayoutAnnouncement
import com.enaboapps.switchify.pc.remote.layouts.PcLayoutAxis
import com.enaboapps.switchify.pc.remote.layouts.PcLayoutEditorState
import com.enaboapps.switchify.pc.remote.layouts.PcLayoutNudge
import com.enaboapps.switchify.pc.remote.layouts.PcLayoutSaveRequest
import com.enaboapps.switchify.pc.remote.layouts.PcLayoutSections
import com.enaboapps.switchify.pc.remote.layouts.PcLayoutSelection
import com.enaboapps.switchify.pc.remote.layouts.PcLayoutSelectionKind
import com.enaboapps.switchify.theme.Dimens
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private sealed class PcLayoutConfirmation {
    data object Discard : PcLayoutConfirmation()
    data object Reset : PcLayoutConfirmation()
    data class RemoveTrack(val axis: PcLayoutAxis, val index: Int) : PcLayoutConfirmation()
}

@Composable
fun PcLayoutEditorDialog(
    title: String,
    initialState: PcLayoutEditorState,
    controls: List<PcResolvedAction>,
    onSave: suspend (PcButtonLayout?) -> Unit,
    onClose: () -> Unit
) {
    var state by remember(initialState) { mutableStateOf(initialState) }
    var saving by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var confirmation by remember { mutableStateOf<PcLayoutConfirmation?>(null) }
    val scope = rememberCoroutineScope()
    val scroll = rememberScrollState()
    val byId = controls.associateBy { it.id }
    val options = controls.map { control ->
        PcActionOption(
            id = control.id,
            name = control.name.resolve(),
            category = stringResource(PcActionPickerModel.categoryLabel(control.definition.category)),
            keywords = control.definition.keywords,
            explanation = control.unavailable?.let { stringResource(it.messageRes) }
        )
    }

    fun update(next: PcLayoutEditorState) {
        if (saving) return
        if (next.draft !== state.draft || next.reset != state.reset) failed = false
        state = next
    }

    fun dismiss() {
        if (saving) return
        if (state.dirty) confirmation = PcLayoutConfirmation.Discard else onClose()
    }

    fun save() {
        if (saving) return
        saving = true
        val request = state.saveRequest()
        scope.launch {
            try {
                if (request is PcLayoutSaveRequest.Save) onSave(request.layout)
                onClose()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                failed = true
            } finally {
                saving = false
            }
        }
    }

    fun resize(axis: PcLayoutAxis, index: Int, insert: Boolean) {
        if (!insert && state.isTrackOccupied(axis, index)) {
            confirmation = PcLayoutConfirmation.RemoveTrack(axis, index)
        } else {
            update(state.resize(axis, index, insert))
        }
    }

    Dialog(
        onDismissRequest = { if (state.pickerCell != null) update(state.closePicker()) else dismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            val pickerCell = state.pickerCell
            if (pickerCell != null) {
                PcActionPicker(
                    row = state.rowOf(pickerCell) + 1,
                    column = state.columnOf(pickerCell) + 1,
                    options = state.pickerOptions(options),
                    onSelect = { id -> update(state.assign(id, byId.keys)) },
                    onClose = { update(state.closePicker()) }
                )
            } else {
                Column(modifier = Modifier.fillMaxSize().imePadding()) {
                    Column(
                        modifier = Modifier.padding(Dimens.spaceM),
                        verticalArrangement = Arrangement.spacedBy(Dimens.spaceXs)
                    ) {
                        Text(
                            text = stringResource(R.string.pc_layout_editor_title, title),
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.semantics { heading() }
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.spaceXs)) {
                            ActionButton(
                                textResId = if (saving) R.string.pc_layout_saving else R.string.pc_layout_save,
                                onClick = ::save,
                                enabled = !saving,
                                applyPadding = false,
                                modifier = Modifier.weight(1f)
                            )
                            ActionButton(
                                textResId = R.string.pc_layout_cancel,
                                onClick = ::dismiss,
                                type = ActionButtonType.SECONDARY,
                                enabled = !saving,
                                applyPadding = false,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (failed) {
                            Text(
                                text = stringResource(R.string.pc_layout_save_failed),
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive }
                            )
                        }
                        Announcement(state, byId)
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(scroll)
                            .padding(Dimens.spaceM),
                        verticalArrangement = Arrangement.spacedBy(Dimens.spaceM)
                    ) {
                        Text(
                            text = stringResource(R.string.pc_layout_help),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        state.selected?.let { selected ->
                            SelectionActions(
                                state = state,
                                selected = selected,
                                enabled = !saving,
                                onUpdate = ::update,
                                onResize = ::resize
                            )
                        }
                        EditorGrid(state = state, controls = byId, enabled = !saving, onSelect = { update(state.select(it)) })
                        PcControlButton(
                            label = stringResource(R.string.pc_layout_add_row),
                            onClick = { resize(PcLayoutAxis.Row, state.rows, true) },
                            enabled = !saving && state.canInsert(PcLayoutAxis.Row),
                            modifier = Modifier.fillMaxWidth()
                        )
                        PcControlButton(
                            label = stringResource(R.string.pc_layout_add_column),
                            onClick = { resize(PcLayoutAxis.Column, state.draft.columns, true) },
                            enabled = !saving && state.canInsert(PcLayoutAxis.Column),
                            modifier = Modifier.fillMaxWidth()
                        )
                        PcControlButton(
                            label = stringResource(R.string.pc_layout_reset),
                            onClick = { confirmation = PcLayoutConfirmation.Reset },
                            enabled = !saving,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
        LaunchedEffect(state.selected) {
            if (state.selected != null && !state.moving) scroll.animateScrollTo(0)
        }
        confirmation?.let { current ->
            Confirmation(
                confirmation = current,
                onConfirm = {
                    confirmation = null
                    when (current) {
                        PcLayoutConfirmation.Discard -> onClose()
                        PcLayoutConfirmation.Reset -> update(state.resetToDefault())
                        is PcLayoutConfirmation.RemoveTrack -> update(state.resize(current.axis, current.index, false))
                    }
                },
                onDismiss = { confirmation = null }
            )
        }
    }
}

@Composable
private fun Announcement(state: PcLayoutEditorState, controls: Map<String, PcResolvedAction>) {
    val message = when (val announcement = state.announcement) {
        null -> null
        is PcLayoutAnnouncement.CellMoved ->
            stringResource(R.string.pc_layout_announce_cell_moved, announcement.row, announcement.column)
        is PcLayoutAnnouncement.TrackMoved -> stringResource(
            if (announcement.kind == PcLayoutSelectionKind.Row) R.string.pc_layout_announce_row_moved else R.string.pc_layout_announce_column_moved,
            announcement.from,
            announcement.to
        )
        is PcLayoutAnnouncement.Assigned -> stringResource(
            R.string.pc_layout_announce_assigned,
            controls[announcement.actionId]?.name?.resolve().orEmpty(),
            announcement.row,
            announcement.column
        )
        is PcLayoutAnnouncement.Removed ->
            stringResource(R.string.pc_layout_announce_removed, announcement.row, announcement.column)
    }
    if (message != null) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
        )
    }
}

@Composable
private fun SelectionActions(
    state: PcLayoutEditorState,
    selected: PcLayoutSelection,
    enabled: Boolean,
    onUpdate: (PcLayoutEditorState) -> Unit,
    onResize: (PcLayoutAxis, Int, Boolean) -> Unit
) {
    val occupied = selected.kind == PcLayoutSelectionKind.Cell && state.draft.cells[selected.index] != null
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.spaceXs)) {
        Text(
            text = when (selected.kind) {
                PcLayoutSelectionKind.Cell -> stringResource(
                    R.string.pc_layout_cell_heading,
                    state.rowOf(selected.index) + 1,
                    state.columnOf(selected.index) + 1
                )
                PcLayoutSelectionKind.Row -> stringResource(R.string.pc_layout_row_heading, selected.index + 1)
                PcLayoutSelectionKind.Column -> stringResource(R.string.pc_layout_column_heading, selected.index + 1)
            },
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() }
        )
        if (state.moving) {
            Text(
                text = stringResource(
                    when (selected.kind) {
                        PcLayoutSelectionKind.Cell -> R.string.pc_layout_move_cell_instruction
                        PcLayoutSelectionKind.Row -> R.string.pc_layout_move_row_instruction
                        PcLayoutSelectionKind.Column -> R.string.pc_layout_move_column_instruction
                    }
                ),
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
            )
        } else {
            if (state.canStartMove()) {
                EditorAction(
                    when (selected.kind) {
                        PcLayoutSelectionKind.Cell -> R.string.pc_layout_move_button
                        PcLayoutSelectionKind.Row -> R.string.pc_layout_move_row
                        PcLayoutSelectionKind.Column -> R.string.pc_layout_move_column
                    },
                    enabled
                ) { onUpdate(state.startMove()) }
            }
            val nudges = when (selected.kind) {
                PcLayoutSelectionKind.Cell -> if (occupied) PcLayoutNudge.entries else emptyList()
                PcLayoutSelectionKind.Row -> listOf(PcLayoutNudge.Up, PcLayoutNudge.Down)
                PcLayoutSelectionKind.Column -> listOf(PcLayoutNudge.Left, PcLayoutNudge.Right)
            }
            nudges.forEach { direction ->
                EditorAction(nudgeLabel(direction), enabled && state.nudgeTarget(direction) != null) {
                    onUpdate(state.nudge(direction))
                }
            }
            if (occupied) {
                EditorAction(R.string.pc_layout_remove_button, enabled) { onUpdate(state.removeSelectedCell()) }
            }
            if (selected.kind != PcLayoutSelectionKind.Cell) {
                val axis = if (selected.kind == PcLayoutSelectionKind.Row) PcLayoutAxis.Row else PcLayoutAxis.Column
                val row = axis == PcLayoutAxis.Row
                EditorAction(
                    if (row) R.string.pc_layout_insert_row_before else R.string.pc_layout_insert_column_before,
                    enabled && state.canInsert(axis)
                ) { onResize(axis, selected.index, true) }
                EditorAction(
                    if (row) R.string.pc_layout_insert_row_after else R.string.pc_layout_insert_column_after,
                    enabled && state.canInsert(axis)
                ) { onResize(axis, selected.index + 1, true) }
                EditorAction(
                    if (row) R.string.pc_layout_remove_row else R.string.pc_layout_remove_column,
                    enabled && state.canRemove(axis)
                ) { onResize(axis, selected.index, false) }
            }
        }
        EditorAction(R.string.pc_layout_close_actions, enabled) { onUpdate(state.closeActions()) }
    }
}

@Composable
private fun EditorAction(label: Int, enabled: Boolean, onClick: () -> Unit) {
    PcControlButton(
        label = stringResource(label),
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth()
    )
}

private fun nudgeLabel(direction: PcLayoutNudge) = when (direction) {
    PcLayoutNudge.Up -> R.string.pc_layout_move_up
    PcLayoutNudge.Down -> R.string.pc_layout_move_down
    PcLayoutNudge.Left -> R.string.pc_layout_move_left
    PcLayoutNudge.Right -> R.string.pc_layout_move_right
}

@Composable
private fun EditorGrid(
    state: PcLayoutEditorState,
    controls: Map<String, PcResolvedAction>,
    enabled: Boolean,
    onSelect: (PcLayoutSelection) -> Unit
) {
    val draft = state.draft
    val selected = state.selected
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val metrics = PcLayoutSections.gridMetrics(maxWidth.value, draft.columns, GRID_GAP, TRACK_WIDTH)
        val horizontal = rememberScrollState()
        Column(
            modifier = Modifier
                .then(if (metrics.overflows) Modifier.horizontalScroll(horizontal) else Modifier)
                .width(metrics.gridWidth.dp),
            verticalArrangement = Arrangement.spacedBy(GRID_GAP.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(GRID_GAP.dp)) {
                Spacer(modifier = Modifier.width(TRACK_WIDTH.dp))
                repeat(draft.columns) { column ->
                    PcControlButton(
                        label = "${column + 1}",
                        accessibilityLabel = stringResource(R.string.pc_layout_column_heading, column + 1),
                        icon = Icons.Rounded.DragIndicator,
                        stacked = true,
                        selected = selectedOrNull(selected, PcLayoutSelectionKind.Column, column),
                        enabled = enabled,
                        onClick = { onSelect(PcLayoutSelection(PcLayoutSelectionKind.Column, column)) },
                        modifier = Modifier.width(metrics.cellWidth.dp)
                    )
                }
            }
            repeat(state.rows) { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(GRID_GAP.dp)) {
                    PcControlButton(
                        label = "${row + 1}",
                        accessibilityLabel = stringResource(R.string.pc_layout_row_heading, row + 1),
                        icon = Icons.Rounded.DragIndicator,
                        stacked = true,
                        selected = selectedOrNull(selected, PcLayoutSelectionKind.Row, row),
                        enabled = enabled,
                        onClick = { onSelect(PcLayoutSelection(PcLayoutSelectionKind.Row, row)) },
                        modifier = Modifier.width(TRACK_WIDTH.dp)
                    )
                    draft.row(row).forEachIndexed { column, id ->
                        val index = row * draft.columns + column
                        val presentation = id?.let(controls::get)?.presentation
                        val label = presentation?.label?.resolve() ?: stringResource(R.string.pc_layout_empty)
                        val spoken = presentation?.accessibilityLabel?.resolve() ?: label
                        PcControlButton(
                            label = label,
                            accessibilityLabel = stringResource(R.string.pc_layout_cell_description, row + 1, column + 1, spoken),
                            stacked = true,
                            selected = selectedOrNull(selected, PcLayoutSelectionKind.Cell, index),
                            enabled = enabled,
                            onClick = { onSelect(PcLayoutSelection(PcLayoutSelectionKind.Cell, index)) },
                            modifier = Modifier.width(metrics.cellWidth.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun selectedOrNull(selected: PcLayoutSelection?, kind: PcLayoutSelectionKind, index: Int): Boolean? =
    if (selected?.kind == kind && selected.index == index) true else null

@Composable
private fun Confirmation(confirmation: PcLayoutConfirmation, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val (titleRes, bodyRes, confirmRes, dismissRes) = when (confirmation) {
        PcLayoutConfirmation.Discard -> listOf(
            R.string.pc_layout_discard_title,
            R.string.pc_layout_discard_body,
            R.string.pc_layout_discard,
            R.string.pc_layout_keep_editing
        )
        PcLayoutConfirmation.Reset -> listOf(
            R.string.pc_layout_reset_title,
            R.string.pc_layout_reset_body,
            R.string.pc_layout_reset_confirm,
            R.string.pc_layout_cancel
        )
        is PcLayoutConfirmation.RemoveTrack -> listOf(
            if (confirmation.axis == PcLayoutAxis.Row) R.string.pc_layout_remove_row_title else R.string.pc_layout_remove_column_title,
            R.string.pc_layout_remove_track_body,
            R.string.pc_layout_remove,
            R.string.pc_layout_cancel
        )
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(titleRes)) },
        text = { Text(stringResource(bodyRes)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(confirmRes)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(dismissRes)) } }
    )
}

private const val GRID_GAP = 8
private const val TRACK_WIDTH = 48
