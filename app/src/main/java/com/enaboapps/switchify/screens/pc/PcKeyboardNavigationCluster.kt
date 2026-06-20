package com.enaboapps.switchify.screens.pc

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.enaboapps.switchify.pc.PcKeyboardKey

@Composable
fun PcKeyboardNavigationCluster(
    enabled: Boolean,
    onKeySelected: (PcKeyboardKey) -> Unit,
    modifier: Modifier = Modifier
) {
    PcCompactCommandGrid(
        columns = 3,
        minTileHeightDp = 52,
        cells = pcKeyboardNavigationKeys().map { key ->
            PcCompactCommandCell(
                labelResId = key.labelResId,
                enabled = enabled,
                onClick = { onKeySelected(key) }
            )
        },
        modifier = modifier
    )
}

fun pcKeyboardNavigationKeys(): List<PcKeyboardKey> {
    return listOf(
        PcKeyboardKey.Escape,
        PcKeyboardKey.ArrowUp,
        PcKeyboardKey.Enter,
        PcKeyboardKey.ArrowLeft,
        PcKeyboardKey.ArrowDown,
        PcKeyboardKey.ArrowRight
    )
}
