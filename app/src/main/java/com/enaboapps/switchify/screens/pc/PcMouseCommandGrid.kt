package com.enaboapps.switchify.screens.pc

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.dp
import com.enaboapps.switchify.R
import com.enaboapps.switchify.components.EqualHeightGridRow
import com.enaboapps.switchify.components.Section
import com.enaboapps.switchify.pc.PcControlCommand

data class PcMouseControlSpec(
    @param:StringRes val labelResId: Int,
    val command: PcControlCommand,
    val icon: ImageVector? = null,
    val iconRotationDegrees: Float = 0f,
    val tone: PcCommandTone = PcCommandTone.Neutral,
    val repeatable: Boolean = false
)

enum class PcCommandTone {
    Neutral,
    Primary,
    Destructive
}

data class PcCompactCommandCell(
    @param:StringRes val labelResId: Int,
    val enabled: Boolean,
    val onClick: () -> Unit,
    val icon: ImageVector? = null,
    val iconRotationDegrees: Float = 0f,
    val tone: PcCommandTone = PcCommandTone.Neutral,
    val selected: Boolean = false
)

data class PcCompactTextCommandCell(
    val label: String,
    val enabled: Boolean,
    val onClick: () -> Unit,
    val selected: Boolean = false
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
    val connected = connectedDisplayName != null
    val dotColor = if (connected) {
        Color(0xFF66BB6A)
    } else {
        MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.4f)
    }
    val pulseScale = if (connected) {
        val pulseTransition = rememberInfiniteTransition(label = "connectionPulse")
        pulseTransition.animateFloat(
            initialValue = 1f,
            targetValue = 1.25f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 900),
                repeatMode = RepeatMode.Reverse
            ),
            label = "connectionPulseScale"
        ).value
    } else {
        1f
    }
    Box(
        modifier = modifier
            .size(12.dp)
            .scale(pulseScale)
            .background(color = dotColor, shape = CircleShape)
            .semantics { contentDescription = description }
    )
}

/**
 * Renders a status/error line only when there is something to say, so the
 * surfaces do not pay a permanent layout cost for occasional messages.
 * The last message is remembered so the exit animation has content to show.
 */
@Composable
fun PcTransientMessage(
    message: String?,
    modifier: Modifier = Modifier
) {
    var lastMessage by remember { mutableStateOf(message) }
    if (message != null) {
        lastMessage = message
    }
    val visibleState = remember { MutableTransitionState(message != null) }
    visibleState.targetState = message != null
    if (message == null && visibleState.isIdle && !visibleState.currentState) return
    AnimatedVisibility(
        visibleState = visibleState,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
        modifier = modifier.fillMaxWidth()
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.secondaryContainer
        ) {
            Text(
                text = lastMessage.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
            )
        }
    }
}

@Composable
fun PcMovementSizeSection(
    selectedSize: PcMouseMovementSize,
    onSizeSelected: (PcMouseMovementSize) -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Section(titleResId = R.string.pc_mouse_movement_size) {
            Box(modifier = Modifier.padding(12.dp)) {
                PcMouseMovementSizeSelector(
                    selectedSize = selectedSize,
                    onSizeSelected = onSizeSelected,
                    enabled = enabled
                )
            }
        }
    }
}

@Composable
fun PcControlCommandGrid(
    enabled: Boolean,
    movementStep: Int,
    onCommandSelected: (PcControlCommand, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    PcControlCommandSections(
        enabled = enabled,
        movementStep = movementStep,
        onCommandSelected = onCommandSelected,
        modifier = modifier
    )
}

@Composable
fun PcControlCommandSections(
    enabled: Boolean,
    movementStep: Int,
    onCommandSelected: (PcControlCommand, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val specs = pcMouseCompactControlSpecs(movementStep)
    val dpadCells = specs.take(9).map { it.toCommandCell(enabled, onCommandSelected) }
    val clickCells = specs.subList(9, 12).map { it.toCommandCell(enabled, onCommandSelected) }
    val scrollCells = specs.subList(12, 14).map { it.toCommandCell(enabled, onCommandSelected) }

    Column(modifier = modifier.fillMaxWidth()) {
        Section(titleResId = R.string.pc_mouse_section_movement) {
            Box(modifier = Modifier.padding(12.dp)) {
                PcCompactCommandGrid(
                    columns = 3,
                    minTileHeightDp = 56,
                    cells = dpadCells
                )
            }
        }
        Section(titleResId = R.string.pc_mouse_section_clicks) {
            Box(modifier = Modifier.padding(12.dp)) {
                PcCompactCommandGrid(
                    columns = 3,
                    minTileHeightDp = 52,
                    cells = clickCells
                )
            }
        }
        Section(titleResId = R.string.pc_mouse_section_scroll) {
            Box(modifier = Modifier.padding(12.dp)) {
                PcCompactCommandGrid(
                    columns = 2,
                    minTileHeightDp = 52,
                    cells = scrollCells
                )
            }
        }
    }
}

private fun PcMouseControlSpec?.toCommandCell(
    enabled: Boolean,
    onCommandSelected: (PcControlCommand, Boolean) -> Unit
): PcCompactCommandCell? {
    return this?.let {
        PcCompactCommandCell(
            labelResId = it.labelResId,
            enabled = enabled,
            onClick = { onCommandSelected(it.command, it.repeatable) },
            icon = it.icon,
            iconRotationDegrees = it.iconRotationDegrees,
            tone = it.tone
        )
    }
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
                EqualHeightGridRow(
                    items = rowCells,
                    columns = columns,
                    itemWidth = tileWidth,
                    minItemHeight = minTileHeightDp.dp,
                    horizontalGap = horizontalGap
                ) { cell, itemModifier ->
                    PcScannedCommandTile(
                        labelResId = cell.labelResId,
                        enabled = cell.enabled,
                        onClick = cell.onClick,
                        icon = cell.icon,
                        iconRotationDegrees = cell.iconRotationDegrees,
                        tone = cell.tone,
                        selected = cell.selected,
                        minHeightDp = minTileHeightDp,
                        modifier = itemModifier
                    )
                }
            }
        }
    }
}

@Composable
fun PcCompactTextCommandGrid(
    columns: Int,
    minTileHeightDp: Int,
    horizontalGapDp: Int = 8,
    verticalGapDp: Int = 8,
    cells: List<PcCompactTextCommandCell>,
    modifier: Modifier = Modifier
) {
    require(columns > 0)
    val horizontalGap = horizontalGapDp.dp
    val verticalGap = verticalGapDp.dp

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val tileWidth = (maxWidth - horizontalGap * (columns - 1)) / columns
        Column(verticalArrangement = Arrangement.spacedBy(verticalGap)) {
            cells.chunked(columns).forEach { rowCells ->
                EqualHeightGridRow(
                    items = rowCells,
                    columns = columns,
                    itemWidth = tileWidth,
                    minItemHeight = minTileHeightDp.dp,
                    horizontalGap = horizontalGap
                ) { cell, itemModifier ->
                    PcScannedCommandTile(
                        label = cell.label,
                        enabled = cell.enabled,
                        onClick = cell.onClick,
                        selected = cell.selected,
                        minHeightDp = minTileHeightDp,
                        modifier = itemModifier
                    )
                }
            }
        }
    }
}

@Composable
fun PcScannedCommandTile(
    @StringRes labelResId: Int,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconRotationDegrees: Float = 0f,
    tone: PcCommandTone = PcCommandTone.Neutral,
    selected: Boolean = false,
    minHeightDp: Int = 52,
    square: Boolean = false
) {
    PcScannedCommandTile(
        label = stringResource(labelResId),
        enabled = enabled,
        onClick = onClick,
        modifier = modifier,
        icon = icon,
        iconRotationDegrees = iconRotationDegrees,
        tone = tone,
        selected = selected,
        minHeightDp = minHeightDp,
        square = square
    )
}

@Composable
fun PcScannedCommandTile(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconRotationDegrees: Float = 0f,
    tone: PcCommandTone = PcCommandTone.Neutral,
    selected: Boolean = false,
    minHeightDp: Int = 52,
    square: Boolean = false
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val backgroundColor by animateColorAsState(
        targetValue = when {
            !enabled -> MaterialTheme.colorScheme.surfaceVariant
            pressed || selected -> MaterialTheme.colorScheme.primaryContainer
            tone == PcCommandTone.Primary -> MaterialTheme.colorScheme.primaryContainer
            tone == PcCommandTone.Destructive -> MaterialTheme.colorScheme.errorContainer
            else -> MaterialTheme.colorScheme.surfaceContainerHigh
        },
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "tileBackground"
    )
    val pressScale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.96f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "tileScale"
    )
    val border = if (enabled && selected) {
        BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
    } else {
        null
    }
    val contentColor = if (enabled) {
        when {
            selected -> MaterialTheme.colorScheme.onPrimaryContainer
            tone == PcCommandTone.Primary -> MaterialTheme.colorScheme.onPrimaryContainer
            tone == PcCommandTone.Destructive -> MaterialTheme.colorScheme.onErrorContainer
            else -> MaterialTheme.colorScheme.onSurface
        }
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
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .clickable(
                enabled = enabled,
                role = Role.Button,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        shape = RoundedCornerShape(12.dp),
        color = backgroundColor,
        border = border
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                icon?.let {
                    Icon(
                        imageVector = it,
                        contentDescription = null,
                        modifier = Modifier
                            .size(22.dp)
                            .rotate(iconRotationDegrees),
                        tint = contentColor
                    )
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (tone == PcCommandTone.Primary) FontWeight.SemiBold else FontWeight.Medium,
                    color = contentColor,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun PcMouseMovementSizeSelector(
    selectedSize: PcMouseMovementSize,
    onSizeSelected: (PcMouseMovementSize) -> Unit,
    enabled: Boolean = true,
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
                enabled = enabled,
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
        clicks[3],
        scroll[0],
        scroll[1],
        null
    )
}

fun pcMovementControlSpecs(moveStep: Int): List<PcMouseControlSpec> {
    val step = moveStep.coerceAtLeast(1)
    return listOf(
        PcMouseControlSpec(
            R.string.pc_mouse_up_left,
            PcControlCommand.Move(-step, -step),
            Icons.Default.KeyboardArrowUp,
            -45f,
            repeatable = true
        ),
        PcMouseControlSpec(
            R.string.pc_mouse_up,
            PcControlCommand.Move(0, -step),
            Icons.Default.KeyboardArrowUp,
            repeatable = true
        ),
        PcMouseControlSpec(
            R.string.pc_mouse_up_right,
            PcControlCommand.Move(step, -step),
            Icons.Default.KeyboardArrowUp,
            45f,
            repeatable = true
        ),
        PcMouseControlSpec(
            R.string.pc_mouse_left,
            PcControlCommand.Move(-step, 0),
            Icons.Default.KeyboardArrowUp,
            -90f,
            repeatable = true
        ),
        PcMouseControlSpec(
            R.string.pc_mouse_right,
            PcControlCommand.Move(step, 0),
            Icons.Default.KeyboardArrowUp,
            90f,
            repeatable = true
        ),
        PcMouseControlSpec(
            R.string.pc_mouse_down_left,
            PcControlCommand.Move(-step, step),
            Icons.Default.KeyboardArrowUp,
            -135f,
            repeatable = true
        ),
        PcMouseControlSpec(
            R.string.pc_mouse_down,
            PcControlCommand.Move(0, step),
            Icons.Default.KeyboardArrowUp,
            180f,
            repeatable = true
        ),
        PcMouseControlSpec(
            R.string.pc_mouse_down_right,
            PcControlCommand.Move(step, step),
            Icons.Default.KeyboardArrowUp,
            135f,
            repeatable = true
        )
    )
}

fun pcClickControlSpecs(): List<PcMouseControlSpec> {
    return listOf(
        PcMouseControlSpec(
            R.string.pc_mouse_click,
            PcControlCommand.LeftClick,
            Icons.Default.TouchApp,
            tone = PcCommandTone.Primary
        ),
        PcMouseControlSpec(R.string.pc_mouse_double_click, PcControlCommand.DoubleClick),
        PcMouseControlSpec(R.string.pc_mouse_right_click, PcControlCommand.RightClick),
        PcMouseControlSpec(R.string.menu_item_drag, PcControlCommand.DragStart(), Icons.Default.OpenWith)
    )
}

fun pcScrollControlSpecs(): List<PcMouseControlSpec> {
    val scrollStep = 5
    return listOf(
        PcMouseControlSpec(
            R.string.pc_mouse_scroll_up,
            PcControlCommand.Scroll(0, scrollStep),
            Icons.Default.KeyboardArrowUp,
            repeatable = true
        ),
        PcMouseControlSpec(
            R.string.pc_mouse_scroll_down,
            PcControlCommand.Scroll(0, -scrollStep),
            Icons.Default.KeyboardArrowDown,
            repeatable = true
        )
    )
}
