package com.enaboapps.switchify.screens.pc

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardReturn
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
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
        cells = pcKeyboardNavigationCells(
            enabled = enabled,
            onKeySelected = onKeySelected
        ),
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

fun pcKeyboardNavigationCells(
    enabled: Boolean,
    onKeySelected: (PcKeyboardKey) -> Unit
): List<PcCompactCommandCell> {
    return pcKeyboardNavigationKeys().map { key ->
        val visual = pcKeyboardNavigationVisual(key)
        PcCompactCommandCell(
            labelResId = key.labelResId,
            enabled = enabled,
            onClick = { onKeySelected(key) },
            icon = visual.icon,
            tone = visual.tone
        )
    }
}

private data class PcKeyboardNavigationVisual(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val tone: PcCommandTone = PcCommandTone.Neutral
)

private fun pcKeyboardNavigationVisual(key: PcKeyboardKey): PcKeyboardNavigationVisual {
    return when (key) {
        PcKeyboardKey.Escape -> PcKeyboardNavigationVisual(Icons.Default.Close)
        PcKeyboardKey.ArrowUp -> PcKeyboardNavigationVisual(Icons.Default.KeyboardArrowUp)
        PcKeyboardKey.Enter -> PcKeyboardNavigationVisual(Icons.AutoMirrored.Filled.KeyboardReturn, PcCommandTone.Primary)
        PcKeyboardKey.ArrowLeft -> PcKeyboardNavigationVisual(Icons.AutoMirrored.Filled.ArrowBack)
        PcKeyboardKey.ArrowDown -> PcKeyboardNavigationVisual(Icons.Default.KeyboardArrowDown)
        PcKeyboardKey.ArrowRight -> PcKeyboardNavigationVisual(Icons.AutoMirrored.Filled.ArrowForward)
        else -> PcKeyboardNavigationVisual(Icons.Default.KeyboardArrowDown)
    }
}
