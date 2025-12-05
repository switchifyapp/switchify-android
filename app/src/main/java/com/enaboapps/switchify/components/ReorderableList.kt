package com.enaboapps.switchify.components

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
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
 */
enum class ReorderMode {
    DRAG,
    ARROWS
}

/**
 * A generic reorderable list component that allows users to choose between
 * drag-and-drop or up/down arrow buttons for reordering items.
 *
 * @param T The type of items in the list
 * @param items The list of items to display
 * @param onMove Callback invoked when an item is moved from one position to another
 * @param key Function to extract a unique key from each item
 * @param defaultMode The default reorder mode (DRAG or ARROWS)
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
    modifier: Modifier = Modifier,
    itemContent: @Composable (item: T, isDragging: Boolean, reorderControls: @Composable () -> Unit) -> Unit
) {
    var currentMode by remember { mutableStateOf(defaultMode) }

    Column(modifier = modifier) {
        // Mode toggle
        ReorderModeToggle(
            currentMode = currentMode,
            onModeChange = { currentMode = it },
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
        }
    }
}

/**
 * Mode toggle segmented button row.
 */
@Composable
private fun ReorderModeToggle(
    currentMode: ReorderMode,
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

    SingleChoiceSegmentedButtonRow(modifier = modifier) {
        SegmentedButton(
            selected = currentMode == ReorderMode.DRAG,
            onClick = { onModeChange(ReorderMode.DRAG) },
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            colors = colors
        ) {
            Text(stringResource(R.string.reorder_mode_drag))
        }
        SegmentedButton(
            selected = currentMode == ReorderMode.ARROWS,
            onClick = { onModeChange(ReorderMode.ARROWS) },
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            colors = colors
        ) {
            Text(stringResource(R.string.reorder_mode_arrows))
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
