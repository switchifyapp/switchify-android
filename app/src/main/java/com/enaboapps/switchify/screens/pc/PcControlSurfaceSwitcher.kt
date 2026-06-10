package com.enaboapps.switchify.screens.pc

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun PcControlSurfaceSwitcher(
    selectedSurface: PcControlSurface,
    onSurfaceSelected: (PcControlSurface) -> Unit,
    modifier: Modifier = Modifier
) {
    val surfaces = PcControlSurface.entries
    val colors = SegmentedButtonDefaults.colors(
        activeContainerColor = MaterialTheme.colorScheme.primary,
        activeContentColor = MaterialTheme.colorScheme.onPrimary,
        activeBorderColor = MaterialTheme.colorScheme.primary,
        inactiveContainerColor = MaterialTheme.colorScheme.surface,
        inactiveContentColor = MaterialTheme.colorScheme.onSurface,
        inactiveBorderColor = MaterialTheme.colorScheme.outline
    )

    SingleChoiceSegmentedButtonRow(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
    ) {
        surfaces.forEachIndexed { index, surface ->
            SegmentedButton(
                selected = selectedSurface == surface,
                onClick = { onSurfaceSelected(surface) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = surfaces.size),
                colors = colors
            ) {
                Text(
                    text = stringResource(surface.labelResId),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
