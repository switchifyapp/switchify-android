package com.enaboapps.switchify.screens.pc

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.enaboapps.switchify.R
import com.enaboapps.switchify.pc.PcMouseCommand

data class PcMouseControlSpec(
    @StringRes val labelResId: Int,
    val command: PcMouseCommand
)

@Composable
fun PcMouseCommandGrid(
    connected: Boolean,
    movementStep: Int,
    busyCommand: PcMouseCommand?,
    onCommandSelected: (PcMouseCommand) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(pcMouseControlSpecs(movementStep)) { control ->
            Button(
                onClick = { onCommandSelected(control.command) },
                enabled = connected && busyCommand == null,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 84.dp)
            ) {
                Text(
                    text = stringResource(control.labelResId),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

fun pcMouseControlSpecs(moveStep: Int): List<PcMouseControlSpec> {
    val step = moveStep.coerceAtLeast(1)
    val scrollStep = 5
    return listOf(
        PcMouseControlSpec(R.string.pc_mouse_up_left, PcMouseCommand.Move(-step, -step)),
        PcMouseControlSpec(R.string.pc_mouse_up, PcMouseCommand.Move(0, -step)),
        PcMouseControlSpec(R.string.pc_mouse_up_right, PcMouseCommand.Move(step, -step)),
        PcMouseControlSpec(R.string.pc_mouse_left, PcMouseCommand.Move(-step, 0)),
        PcMouseControlSpec(R.string.pc_mouse_click, PcMouseCommand.LeftClick),
        PcMouseControlSpec(R.string.pc_mouse_right, PcMouseCommand.Move(step, 0)),
        PcMouseControlSpec(R.string.pc_mouse_down_left, PcMouseCommand.Move(-step, step)),
        PcMouseControlSpec(R.string.pc_mouse_down, PcMouseCommand.Move(0, step)),
        PcMouseControlSpec(R.string.pc_mouse_down_right, PcMouseCommand.Move(step, step)),
        PcMouseControlSpec(R.string.pc_mouse_right_click, PcMouseCommand.RightClick),
        PcMouseControlSpec(R.string.pc_mouse_double_click, PcMouseCommand.DoubleClick),
        PcMouseControlSpec(R.string.pc_mouse_scroll_up, PcMouseCommand.Scroll(0, scrollStep)),
        PcMouseControlSpec(R.string.pc_mouse_scroll_down, PcMouseCommand.Scroll(0, -scrollStep))
    )
}
