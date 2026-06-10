package com.enaboapps.switchify.screens.pc

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
        Text(
            text = stringResource(R.string.pc_mouse_movement_size),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        PcMouseMovementSizeSelector(
            selectedSize = selectedSize,
            onSizeSelected = onSizeSelected
        )
    }
}

/**
 * 3x3 movement pad with the left click in the center, so the
 * position-then-click loop stays inside one tight scan group.
 * Rows share the available height equally.
 */
@Composable
fun PcMouseControlPad(
    connected: Boolean,
    movementStep: Int,
    onCommandSelected: (PcControlCommand) -> Unit,
    modifier: Modifier = Modifier
) {
    val specs = pcMouseGridSpecs(movementStep)
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        specs.chunked(3).forEach { rowSpecs ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                rowSpecs.forEach { spec ->
                    PcScannedCommandTile(
                        labelResId = spec.labelResId,
                        enabled = connected,
                        onClick = { onCommandSelected(spec.command) },
                        modifier = Modifier.weight(1f),
                        minHeightDp = 64,
                        fillHeight = true
                    )
                }
            }
        }
    }
}

@Composable
fun PcCommandButtonRow(
    specs: List<PcMouseControlSpec>,
    connected: Boolean,
    onCommandSelected: (PcControlCommand) -> Unit,
    modifier: Modifier = Modifier,
    minHeightDp: Int = 64
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        specs.forEach { spec ->
            PcScannedCommandTile(
                labelResId = spec.labelResId,
                enabled = connected,
                onClick = { onCommandSelected(spec.command) },
                modifier = Modifier.weight(1f),
                minHeightDp = minHeightDp
            )
        }
    }
}

@Composable
fun PcScannedCommandTile(
    @StringRes labelResId: Int,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    minHeightDp: Int = 64,
    fillHeight: Boolean = false
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
    val tileModifier = if (fillHeight) {
        modifier
            .fillMaxSize()
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
                maxLines = 2,
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
                Text(text = stringResource(size.labelResId))
            }
        }
    }
}

fun pcMouseControlSpecs(moveStep: Int): List<PcMouseControlSpec> {
    return pcMovementControlSpecs(moveStep) + pcClickControlSpecs() + pcScrollControlSpecs()
}

/**
 * The 3x3 mouse pad in row order: the eight movement directions with the
 * left click occupying the center cell.
 */
fun pcMouseGridSpecs(moveStep: Int): List<PcMouseControlSpec> {
    val moves = pcMovementControlSpecs(moveStep)
    return moves.take(4) +
            PcMouseControlSpec(R.string.pc_mouse_click, PcControlCommand.LeftClick) +
            moves.drop(4)
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

/**
 * Click actions that live below the pad; the plain left click sits in the
 * pad center instead.
 */
fun pcSecondaryClickControlSpecs(): List<PcMouseControlSpec> {
    return pcClickControlSpecs().filter { it.command != PcControlCommand.LeftClick }
}

fun pcScrollControlSpecs(): List<PcMouseControlSpec> {
    val scrollStep = 5
    return listOf(
        PcMouseControlSpec(R.string.pc_mouse_scroll_up, PcControlCommand.Scroll(0, scrollStep)),
        PcMouseControlSpec(R.string.pc_mouse_scroll_down, PcControlCommand.Scroll(0, -scrollStep))
    )
}
