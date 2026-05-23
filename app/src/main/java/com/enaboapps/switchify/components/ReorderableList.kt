package com.enaboapps.switchify.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.enaboapps.switchify.R
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.ReorderableCollectionItemScope
import sh.calvin.reorderable.rememberReorderableLazyListState

/**
 * Reorder mode for the reorderable list.
 *
 * SELECT is a switch-access-friendly two-step flow: pick an item, then pick
 * a destination. It moves any item to any position in two taps regardless of
 * distance, where ARROWS needs one tap per position.
 */
enum class ReorderMode {
    DRAG,
    ARROWS,
    SELECT
}

/**
 * Selection state and callbacks for [ReorderMode.SELECT]. Passing null to
 * [ReorderableList] hides the Select segmented button entirely.
 */
class SelectModeState<T>(
    val selectedKey: Any?,
    val getLabel: (T) -> String,
    val onPickUp: (T) -> Unit,
    val onCancel: () -> Unit,
    val onInsertBefore: (T) -> Unit,
    val onInsertAtEnd: () -> Unit
)

/**
 * A generic reorderable list component that allows users to choose between
 * drag-and-drop, up/down arrow buttons, or a switch-friendly select-then-
 * insert flow for reordering items.
 *
 * @param T The type of items in the list
 * @param items The list of items to display
 * @param onMove Callback invoked when an item is moved from one position to another
 * @param key Function to extract a unique key from each item
 * @param defaultMode The default reorder mode (DRAG, ARROWS, or SELECT)
 * @param selectModeState Selection state for SELECT mode. Null hides the Select toggle.
 * @param modifier Modifier for the component
 * @param itemContent Composable function to render each item. Receives the item,
 *                    whether it's being dragged, and the reorder controls composable
 */
@Composable
fun <T> ReorderableList(
    items: List<T>,
    onMove: (fromIndex: Int, toIndex: Int) -> Unit,
    key: (T) -> Any,
    defaultMode: ReorderMode = ReorderMode.DRAG,
    selectModeState: SelectModeState<T>? = null,
    modifier: Modifier = Modifier,
    itemContent: @Composable (item: T, isDragging: Boolean, reorderControls: @Composable () -> Unit) -> Unit
) {
    var currentMode by remember { mutableStateOf(defaultMode) }

    Column(modifier = modifier) {
        // Mode toggle
        ReorderModeToggle(
            currentMode = currentMode,
            showSelect = selectModeState != null,
            onModeChange = { newMode ->
                if (currentMode == ReorderMode.SELECT && newMode != ReorderMode.SELECT) {
                    selectModeState?.onCancel?.invoke()
                }
                currentMode = newMode
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )

        when (currentMode) {
            ReorderMode.DRAG -> DragReorderableList(
                items = items,
                onMove = onMove,
                key = key,
                itemContent = itemContent
            )
            ReorderMode.ARROWS -> ArrowReorderableList(
                items = items,
                onMove = onMove,
                key = key,
                itemContent = itemContent
            )
            ReorderMode.SELECT -> {
                if (selectModeState != null) {
                    SelectReorderableList(
                        items = items,
                        key = key,
                        selectModeState = selectModeState,
                        itemContent = itemContent
                    )
                } else {
                    // Defensive: if Select was the default but no state was supplied,
                    // fall back to drag rather than rendering nothing.
                    DragReorderableList(items, onMove, key, itemContent)
                }
            }
        }
    }
}

/**
 * Mode toggle segmented button row.
 */
@Composable
private fun ReorderModeToggle(
    currentMode: ReorderMode,
    showSelect: Boolean,
    onModeChange: (ReorderMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = SegmentedButtonDefaults.colors(
        activeContainerColor = MaterialTheme.colorScheme.primary,
        activeContentColor = MaterialTheme.colorScheme.onPrimary,
        activeBorderColor = MaterialTheme.colorScheme.primary,
        inactiveContainerColor = MaterialTheme.colorScheme.surface,
        inactiveContentColor = MaterialTheme.colorScheme.onSurface,
        inactiveBorderColor = MaterialTheme.colorScheme.outline
    )

    val totalCount = if (showSelect) 3 else 2

    SingleChoiceSegmentedButtonRow(modifier = modifier) {
        SegmentedButton(
            selected = currentMode == ReorderMode.DRAG,
            onClick = { onModeChange(ReorderMode.DRAG) },
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = totalCount),
            colors = colors
        ) {
            Text(stringResource(R.string.reorder_mode_drag))
        }
        SegmentedButton(
            selected = currentMode == ReorderMode.ARROWS,
            onClick = { onModeChange(ReorderMode.ARROWS) },
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = totalCount),
            colors = colors
        ) {
            Text(stringResource(R.string.reorder_mode_arrows))
        }
        if (showSelect) {
            SegmentedButton(
                selected = currentMode == ReorderMode.SELECT,
                onClick = { onModeChange(ReorderMode.SELECT) },
                shape = SegmentedButtonDefaults.itemShape(index = 2, count = totalCount),
                colors = colors
            ) {
                Text(stringResource(R.string.reorder_mode_select))
            }
        }
    }
}

/**
 * Drag-and-drop reorderable list implementation.
 */
@Composable
private fun <T> DragReorderableList(
    items: List<T>,
    onMove: (fromIndex: Int, toIndex: Int) -> Unit,
    key: (T) -> Any,
    itemContent: @Composable (item: T, isDragging: Boolean, reorderControls: @Composable () -> Unit) -> Unit
) {
    val lazyListState: LazyListState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(
        lazyListState = lazyListState,
        onMove = { from, to ->
            onMove(from.index, to.index)
        }
    )

    LazyColumn(state = lazyListState) {
        itemsIndexed(items, key = { _, item -> key(item) }) { _, item ->
            ReorderableItem(state = reorderableState, key = key(item)) {
                val isDragging = it
                itemContent(item, isDragging) {
                    DragHandle(this)
                }
            }
        }
    }
}

/**
 * Arrow button reorderable list implementation.
 */
@Composable
private fun <T> ArrowReorderableList(
    items: List<T>,
    onMove: (fromIndex: Int, toIndex: Int) -> Unit,
    key: (T) -> Any,
    itemContent: @Composable (item: T, isDragging: Boolean, reorderControls: @Composable () -> Unit) -> Unit
) {
    val lazyListState: LazyListState = rememberLazyListState()

    LazyColumn(state = lazyListState) {
        itemsIndexed(items, key = { _, item -> key(item) }) { index, item ->
            itemContent(item, false) {
                ArrowControls(
                    canMoveUp = index > 0,
                    canMoveDown = index < items.size - 1,
                    onMoveUp = { onMove(index, index - 1) },
                    onMoveDown = { onMove(index, index + 1) }
                )
            }
        }
    }
}

/**
 * Select-then-insert reorderable list implementation. The reorder-controls
 * slot renders one of three buttons per row depending on selection state:
 *
 *  - no selection → Move (picks the item up)
 *  - this row is selected → Cancel (clears the selection)
 *  - another row is selected → Insert above (places the selection before this row)
 *
 * A pinned "Insert at end" row is appended while a selection is active.
 */
@Composable
private fun <T> SelectReorderableList(
    items: List<T>,
    key: (T) -> Any,
    selectModeState: SelectModeState<T>,
    itemContent: @Composable (item: T, isDragging: Boolean, reorderControls: @Composable () -> Unit) -> Unit
) {
    val lazyListState: LazyListState = rememberLazyListState()
    val selectedKey = selectModeState.selectedKey
    val selectedLabel = remember(items, selectedKey) {
        items.firstOrNull { key(it) == selectedKey }?.let { selectModeState.getLabel(it) }
    }

    LazyColumn(state = lazyListState) {
        itemsIndexed(items, key = { _, item -> key(item) }) { _, item ->
            val isSelected = key(item) == selectedKey
            val hasSelection = selectedKey != null
            itemContent(item, false) {
                when {
                    isSelected -> SelectControlCancel(
                        itemLabel = selectModeState.getLabel(item),
                        onClick = selectModeState.onCancel
                    )
                    hasSelection -> SelectControlInsertAbove(
                        selectedLabel = selectedLabel.orEmpty(),
                        targetLabel = selectModeState.getLabel(item),
                        onClick = { selectModeState.onInsertBefore(item) }
                    )
                    else -> SelectControlMove(
                        itemLabel = selectModeState.getLabel(item),
                        onClick = { selectModeState.onPickUp(item) }
                    )
                }
            }
        }
        if (selectedKey != null) {
            item(key = "__select_insert_at_end__") {
                InsertAtEndRow(
                    selectedLabel = selectedLabel.orEmpty(),
                    onClick = selectModeState.onInsertAtEnd
                )
            }
        }
    }
}

/**
 * Drag handle icon for drag mode.
 */
@Composable
private fun DragHandle(scope: ReorderableCollectionItemScope) {
    Icon(
        imageVector = Icons.Default.DragHandle,
        contentDescription = stringResource(R.string.content_desc_drag_handle),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = with(scope) {
            Modifier
                .draggableHandle()
                .padding(8.dp)
        }
    )
}

/**
 * Up/down arrow controls for arrow mode.
 */
@Composable
private fun ArrowControls(
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        IconButton(
            onClick = onMoveUp,
            enabled = canMoveUp,
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowUp,
                contentDescription = stringResource(R.string.content_desc_move_up),
                tint = if (canMoveUp) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                }
            )
        }
        Spacer(modifier = Modifier.width(4.dp))
        IconButton(
            onClick = onMoveDown,
            enabled = canMoveDown,
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = stringResource(R.string.content_desc_move_down),
                tint = if (canMoveDown) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                }
            )
        }
    }
}

@Composable
private fun SelectControlMove(itemLabel: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(40.dp)) {
        Icon(
            imageVector = Icons.Default.OpenWith,
            contentDescription = stringResource(
                R.string.menu_customization_select_move_action,
                itemLabel
            ),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SelectControlCancel(itemLabel: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(40.dp)) {
        Icon(
            imageVector = Icons.Default.Close,
            contentDescription = stringResource(
                R.string.menu_customization_select_cancel_action,
                itemLabel
            ),
            tint = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun SelectControlInsertAbove(
    selectedLabel: String,
    targetLabel: String,
    onClick: () -> Unit
) {
    IconButton(onClick = onClick, modifier = Modifier.size(40.dp)) {
        Icon(
            imageVector = Icons.Default.ArrowUpward,
            contentDescription = stringResource(
                R.string.menu_customization_select_insert_above_action,
                selectedLabel,
                targetLabel
            ),
            tint = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun InsertAtEndRow(selectedLabel: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        color = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.ArrowDownward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = stringResource(
                    R.string.menu_customization_select_insert_at_end_action,
                    selectedLabel
                ),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
