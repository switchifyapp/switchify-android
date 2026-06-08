package com.enaboapps.switchify.screens.pc

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.enaboapps.switchify.R
import com.enaboapps.switchify.pc.PcMouseCommand

data class PcMouseControlSpec(
    @param:StringRes val labelResId: Int,
    val command: PcMouseCommand
)

@Composable
fun PcMouseCommandGrid(
    connected: Boolean,
    movementStep: Int,
    onCommandSelected: (PcMouseCommand) -> Unit,
    modifier: Modifier = Modifier
) {
    PcMouseCommandSections(
        connected = connected,
        movementStep = movementStep,
        onCommandSelected = onCommandSelected,
        modifier = modifier
    )
}

@Composable
fun PcControlStatusStrip(
    connectedDisplayName: String?,
    message: String?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = connectedDisplayName?.let {
                stringResource(R.string.pc_mouse_control_connected, it)
            } ?: stringResource(R.string.pc_control_connect_first),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        message?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
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
fun PcMouseCommandSections(
    connected: Boolean,
    movementStep: Int,
    onCommandSelected: (PcMouseCommand) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        PcMovementCommandSection(
            connected = connected,
            movementStep = movementStep,
            onCommandSelected = onCommandSelected
        )
        PcButtonCommandSection(
            titleResId = R.string.pc_mouse_section_clicks,
            specs = pcClickControlSpecs(),
            connected = connected,
            onCommandSelected = onCommandSelected
        )
        PcButtonCommandSection(
            titleResId = R.string.pc_mouse_section_scroll,
            specs = pcScrollControlSpecs(),
            connected = connected,
            onCommandSelected = onCommandSelected
        )
    }
}

@Composable
fun PcTypingCommandSection(
    connected: Boolean,
    onOpenTyping: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        PcCommandSectionTitle(R.string.pc_mouse_section_typing)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            PcCommandTile(
                labelResId = R.string.pc_typing_type_text,
                connected = connected,
                onClick = onOpenTyping,
                minHeightDp = 72,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun PcMovementCommandSection(
    connected: Boolean,
    movementStep: Int,
    onCommandSelected: (PcMouseCommand) -> Unit
) {
    val controls = pcMovementControlSpecs(movementStep)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PcCommandSectionTitle(R.string.pc_mouse_section_movement)
        PcCommandButtonRow(
            specs = controls.take(3),
            connected = connected,
            onCommandSelected = onCommandSelected,
            minHeightDp = 76
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            PcCommandButton(
                spec = controls[3],
                connected = connected,
                onCommandSelected = onCommandSelected,
                minHeightDp = 76,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.weight(1f))
            PcCommandButton(
                spec = controls[4],
                connected = connected,
                onCommandSelected = onCommandSelected,
                minHeightDp = 76,
                modifier = Modifier.weight(1f)
            )
        }
        PcCommandButtonRow(
            specs = controls.drop(5),
            connected = connected,
            onCommandSelected = onCommandSelected,
            minHeightDp = 76
        )
    }
}

@Composable
private fun PcButtonCommandSection(
    @StringRes titleResId: Int,
    specs: List<PcMouseControlSpec>,
    connected: Boolean,
    onCommandSelected: (PcMouseCommand) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PcCommandSectionTitle(titleResId)
        PcCommandButtonRow(
            specs = specs,
            connected = connected,
            onCommandSelected = onCommandSelected,
            minHeightDp = 72
        )
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
private fun PcCommandButtonRow(
    specs: List<PcMouseControlSpec>,
    connected: Boolean,
    onCommandSelected: (PcMouseCommand) -> Unit,
    minHeightDp: Int
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        specs.forEach { spec ->
            PcCommandButton(
                spec = spec,
                connected = connected,
                onCommandSelected = onCommandSelected,
                minHeightDp = minHeightDp,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun PcCommandButton(
    spec: PcMouseControlSpec,
    connected: Boolean,
    onCommandSelected: (PcMouseCommand) -> Unit,
    minHeightDp: Int,
    modifier: Modifier = Modifier
) {
    PcCommandTile(
        labelResId = spec.labelResId,
        connected = connected,
        onClick = { onCommandSelected(spec.command) },
        minHeightDp = minHeightDp,
        modifier = modifier
    )
}

@Composable
private fun PcCommandTile(
    @StringRes labelResId: Int,
    connected: Boolean,
    onClick: () -> Unit,
    minHeightDp: Int,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val backgroundColor = when {
        !connected -> MaterialTheme.colorScheme.surfaceVariant
        pressed -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surface
    }
    val borderColor = if (connected) {
        MaterialTheme.colorScheme.outline
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }
    val contentColor = if (connected) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .heightIn(min = minHeightDp.dp)
            .clickable(
                enabled = connected,
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

fun pcMovementControlSpecs(moveStep: Int): List<PcMouseControlSpec> {
    val step = moveStep.coerceAtLeast(1)
    return listOf(
        PcMouseControlSpec(R.string.pc_mouse_up_left, PcMouseCommand.Move(-step, -step)),
        PcMouseControlSpec(R.string.pc_mouse_up, PcMouseCommand.Move(0, -step)),
        PcMouseControlSpec(R.string.pc_mouse_up_right, PcMouseCommand.Move(step, -step)),
        PcMouseControlSpec(R.string.pc_mouse_left, PcMouseCommand.Move(-step, 0)),
        PcMouseControlSpec(R.string.pc_mouse_right, PcMouseCommand.Move(step, 0)),
        PcMouseControlSpec(R.string.pc_mouse_down_left, PcMouseCommand.Move(-step, step)),
        PcMouseControlSpec(R.string.pc_mouse_down, PcMouseCommand.Move(0, step)),
        PcMouseControlSpec(R.string.pc_mouse_down_right, PcMouseCommand.Move(step, step))
    )
}

fun pcClickControlSpecs(): List<PcMouseControlSpec> {
    return listOf(
        PcMouseControlSpec(R.string.pc_mouse_click, PcMouseCommand.LeftClick),
        PcMouseControlSpec(R.string.pc_mouse_double_click, PcMouseCommand.DoubleClick),
        PcMouseControlSpec(R.string.pc_mouse_right_click, PcMouseCommand.RightClick)
    )
}

fun pcScrollControlSpecs(): List<PcMouseControlSpec> {
    val scrollStep = 5
    return listOf(
        PcMouseControlSpec(R.string.pc_mouse_scroll_up, PcMouseCommand.Scroll(0, scrollStep)),
        PcMouseControlSpec(R.string.pc_mouse_scroll_down, PcMouseCommand.Scroll(0, -scrollStep))
    )
}
