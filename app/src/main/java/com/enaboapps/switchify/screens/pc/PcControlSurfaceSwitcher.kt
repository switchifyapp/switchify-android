package com.enaboapps.switchify.screens.pc

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable
fun PcControlSurfaceSwitcher(
    selectedSurface: PcControlSurface,
    onSurfaceSelected: (PcControlSurface) -> Unit,
    enabled: Boolean = true,
    onClose: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            PcControlSurface.entries.forEach { surface ->
                val label = stringResource(surface.labelResId)
                val selected = selectedSurface == surface
                val contentColor = when {
                    !enabled -> MaterialTheme.colorScheme.onSurfaceVariant
                    selected -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurface
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .padding(4.dp)
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp),
                        shape = RoundedCornerShape(20.dp),
                        color = if (selected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surface
                        }
                    ) {
                        IconButton(
                            onClick = { onSurfaceSelected(surface) },
                            enabled = enabled,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp)
                                .semantics { contentDescription = label }
                        ) {
                            Icon(
                                imageVector = surface.icon(),
                                contentDescription = null,
                                modifier = Modifier.size(if (selected) 25.dp else 22.dp),
                                tint = contentColor
                            )
                        }
                    }
                }
            }
            onClose?.let { close ->
                val contentColor = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
                Box(
                    modifier = Modifier
                        .width(56.dp)
                        .heightIn(min = 48.dp)
                        .padding(4.dp)
                ) {
                    IconButton(
                        onClick = close,
                        enabled = enabled,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp)
                            .semantics { contentDescription = "Close PC controls" }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp),
                            tint = contentColor
                        )
                    }
                }
            }
        }
    }
}

private fun PcControlSurface.icon(): ImageVector {
    return when (this) {
        PcControlSurface.Mouse -> Icons.Default.TouchApp
        PcControlSurface.Typing -> Icons.Default.Keyboard
        PcControlSurface.Window -> Icons.Rounded.Computer
    }
}
