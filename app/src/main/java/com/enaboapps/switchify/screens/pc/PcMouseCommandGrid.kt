package com.enaboapps.switchify.screens.pc

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.enaboapps.switchify.R
import com.enaboapps.switchify.pc.PcControlCommand

data class PcMouseControlSpec(
    @param:StringRes val labelResId: Int,
    val command: PcControlCommand
)

data class PcCompactCommandCell(
    @param:StringRes val labelResId: Int,
    val enabled: Boolean,
    val onClick: () -> Unit
)

/**
 * Small connection indicator shown in the navbar next to the surface switcher.
 * Non-interactive; the connection state is exposed through semantics so speech
 * feedback still announces it without adding a scan target.
 */
@Composable
fun PcConnectionStatusDot(
    connectedDisplayName: String?,
    modifier: Modifier = Modifier
) {
    val description = connectedDisplayName?.let {
        stringResource(R.string.pc_mouse_control_connected, it)
    } ?: stringResource(R.string.pc_control_connect_first)
    val dotColor = if (connectedDisplayName != null) {
        Color(0xFF66BB6A)
    } else {
        MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.4f)
    }
    Box(
        modifier = modifier
            .size(12.dp)
            .background(color = dotColor, shape = CircleShape)
            .semantics { contentDescription = description }
    )
}

/**
 * Renders a status/error line only when there is something to say, so the
 * surfaces do not pay a permanent layout cost for occasional messages.
 */
@Composable
fun PcTransientMessage(
    message: String?,
    modifier: Modifier = Modifier
) {
    if (message == null) return
    Text(
        text = message,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.fillMaxWidth()
    )
}

@Composable
fun PcMovementSizeSection(
    selectedSize: PcMouseMovementSize,
    onSizeSelected: (PcMouseMovementSize) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        PcCommandSectionTitle(R.string.pc_mouse_movement_size)
        PcMouseMovementSizeSelector(
            selectedSize = selectedSize,
            onSizeSelected = onSizeSelected
        )
    }
}

@Composable
fun PcControlCommandGrid(
    connected: Boolean,
    movementStep: Int,
    onCommandSelected: (PcControlCommand) -> Unit,
    modifier: Modifier = Modifier
) {
    PcControlCommandSections(
        connected = connected,
        movementStep = movementStep,
        onCommandSelected = onCommandSelected,
        modifier = modifier
    )
}

@Composable
fun PcControlCommandSections(
    connected: Boolean,
    movementStep: Int,
    onCommandSelected: (PcControlCommand) -> Unit,
    modifier: Modifier = Modifier
) {
    PcCompactCommandGrid(
        columns = 3,
        minTileHeightDp = 52,
        cells = pcMouseCompactControlSpecs(movementStep).map { spec ->
            spec?.let {
                PcCompactCommandCell(
                    labelResId = it.labelResId,
                    enabled = connected,
                    onClick = { onCommandSelected(it.command) }
                )
            }
        },
        modifier = modifier
    )
}

@Composable
fun PcCompactCommandGrid(
    columns: Int,
    minTileHeightDp: Int,
    horizontalGapDp: Int = 8,
    verticalGapDp: Int = 8,
    cells: List<PcCompactCommandCell?>,
    modifier: Modifier = Modifier
) {
    require(columns > 0)
    val horizontalGap = horizontalGapDp.dp
    val verticalGap = verticalGapDp.dp

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val tileWidth = (maxWidth - horizontalGap * (columns - 1)) / columns
        Column(verticalArrangement = Arrangement.spacedBy(verticalGap)) {
            cells.chunked(columns).forEach { rowCells ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(horizontalGap)
                ) {
                    rowCells.forEach { cell ->
                        if (cell == null) {
                            Spacer(
                                modifier = Modifier
                                    .width(tileWidth)
                                    .heightIn(min = minTileHeightDp.dp)
                            )
                        } else {
                            PcScannedCommandTile(
                                labelResId = cell.labelResId,
                                enabled = cell.enabled,
                                onClick = cell.onClick,
                                minHeightDp = minTileHeightDp,
                                modifier = Modifier.width(tileWidth)
                            )
                        }
                    }
                    repeat(columns - rowCells.size) {
                        Spacer(
                            modifier = Modifier
                                .width(tileWidth)
                                .heightIn(min = minTileHeightDp.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PcCommandSectionTitle(@StringRes titleResId: Int) {
    Text(
        text = stringResource(titleResId),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface
    )
}

@Composable
fun PcScannedCommandTile(
    @StringRes labelResId: Int,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    minHeightDp: Int = 52,
    square: Boolean = false
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val backgroundColor = when {
        !enabled -> MaterialTheme.colorScheme.surfaceVariant
        pressed -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surface
    }
    val borderColor = if (enabled) {
        MaterialTheme.colorScheme.outline
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }
    val contentColor = if (enabled) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val tileModifier = if (square) {
        modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .heightIn(min = minHeightDp.dp)
    } else {
        modifier
            .fillMaxWidth()
            .heightIn(min = minHeightDp.dp)
    }

    Surface(
        modifier = tileModifier
            .clickable(
                enabled = enabled,
                role = Role.Button,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        shape = RoundedCornerShape(8.dp),
        color = backgroundColor,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(labelResId),
                style = MaterialTheme.typography.labelLarge,
                color = contentColor,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun PcMouseMovementSizeSelector(
    selectedSize: PcMouseMovementSize,
    onSizeSelected: (PcMouseMovementSize) -> Unit,
    modifier: Modifier = Modifier
) {
    val sizes = PcMouseMovementSize.entries
    val colors = SegmentedButtonDefaults.colors(
        activeContainerColor = MaterialTheme.colorScheme.primary,
        activeContentColor = MaterialTheme.colorScheme.onPrimary,
        activeBorderColor = MaterialTheme.colorScheme.primary,
        inactiveContainerColor = MaterialTheme.colorScheme.surface,
        inactiveContentColor = MaterialTheme.colorScheme.onSurface,
        inactiveBorderColor = MaterialTheme.colorScheme.outline
    )

    SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
        sizes.forEachIndexed { index, size ->
            SegmentedButton(
                selected = selectedSize == size,
                onClick = { onSizeSelected(size) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = sizes.size),
                colors = colors
            ) {
                Text(
                    text = stringResource(size.labelResId),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

fun pcMouseControlSpecs(moveStep: Int): List<PcMouseControlSpec> {
    return pcMovementControlSpecs(moveStep) + pcClickControlSpecs() + pcScrollControlSpecs()
}

fun pcMouseCompactControlSpecs(moveStep: Int): List<PcMouseControlSpec?> {
    val movement = pcMovementControlSpecs(moveStep)
    val clicks = pcClickControlSpecs()
    val scroll = pcScrollControlSpecs()
    return listOf(
        movement[0],
        movement[1],
        movement[2],
        movement[3],
        clicks[0],
        movement[4],
        movement[5],
        movement[6],
        movement[7],
        clicks[1],
        clicks[2],
        scroll[0],
        scroll[1],
        null,
        null
    )
}

fun pcMovementControlSpecs(moveStep: Int): List<PcMouseControlSpec> {
    val step = moveStep.coerceAtLeast(1)
    return listOf(
        PcMouseControlSpec(R.string.pc_mouse_up_left, PcControlCommand.Move(-step, -step)),
        PcMouseControlSpec(R.string.pc_mouse_up, PcControlCommand.Move(0, -step)),
        PcMouseControlSpec(R.string.pc_mouse_up_right, PcControlCommand.Move(step, -step)),
        PcMouseControlSpec(R.string.pc_mouse_left, PcControlCommand.Move(-step, 0)),
        PcMouseControlSpec(R.string.pc_mouse_right, PcControlCommand.Move(step, 0)),
        PcMouseControlSpec(R.string.pc_mouse_down_left, PcControlCommand.Move(-step, step)),
        PcMouseControlSpec(R.string.pc_mouse_down, PcControlCommand.Move(0, step)),
        PcMouseControlSpec(R.string.pc_mouse_down_right, PcControlCommand.Move(step, step))
    )
}

fun pcClickControlSpecs(): List<PcMouseControlSpec> {
    return listOf(
        PcMouseControlSpec(R.string.pc_mouse_click, PcControlCommand.LeftClick),
        PcMouseControlSpec(R.string.pc_mouse_double_click, PcControlCommand.DoubleClick),
        PcMouseControlSpec(R.string.pc_mouse_right_click, PcControlCommand.RightClick)
    )
}

fun pcScrollControlSpecs(): List<PcMouseControlSpec> {
    val scrollStep = 5
    return listOf(
        PcMouseControlSpec(R.string.pc_mouse_scroll_up, PcControlCommand.Scroll(0, scrollStep)),
        PcMouseControlSpec(R.string.pc_mouse_scroll_down, PcControlCommand.Scroll(0, -scrollStep))
    )
}
