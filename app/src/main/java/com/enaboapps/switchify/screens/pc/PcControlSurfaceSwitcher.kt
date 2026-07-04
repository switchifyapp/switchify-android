package com.enaboapps.switchify.screens.pc

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.enaboapps.switchify.R

private val SwitcherHeight = 48.dp
private val CloseButtonWidth = 56.dp
private val IndicatorPadding = 4.dp
private val IndicatorShape = RoundedCornerShape(20.dp)

@Composable
fun PcControlSurfaceSwitcher(
    selectedSurface: PcControlSurface,
    onSurfaceSelected: (PcControlSurface) -> Unit,
    enabled: Boolean = true,
    onClose: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(SwitcherHeight)
        ) {
            val surfaces = PcControlSurface.entries
            val closeWidth = if (onClose != null) CloseButtonWidth else 0.dp
            val tabWidth = (maxWidth - closeWidth) / surfaces.size
            val selectedIndex = surfaces.indexOf(selectedSurface)
            val indicatorOffset by animateDpAsState(
                targetValue = tabWidth * selectedIndex,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow
                ),
                label = "surfaceIndicatorOffset"
            )

            Box(
                modifier = Modifier
                    .offset(x = indicatorOffset)
                    .width(tabWidth)
                    .fillMaxHeight()
                    .padding(IndicatorPadding)
                    .background(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = IndicatorShape
                    )
            )

            Row(modifier = Modifier.fillMaxSize()) {
                surfaces.forEach { surface ->
                    val label = stringResource(surface.labelResId)
                    val selected = selectedSurface == surface
                    val contentColor = when {
                        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant
                        selected -> MaterialTheme.colorScheme.onPrimaryContainer
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                    Row(
                        modifier = Modifier
                            .width(tabWidth)
                            .fillMaxHeight()
                            .padding(IndicatorPadding)
                            .clip(IndicatorShape)
                            .clickable(
                                enabled = enabled,
                                role = Role.Button,
                                onClick = { onSurfaceSelected(surface) }
                            )
                            .semantics { contentDescription = label },
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = surface.icon(),
                            contentDescription = null,
                            modifier = Modifier.size(22.dp),
                            tint = contentColor
                        )
                        AnimatedVisibility(
                            visible = selected,
                            enter = fadeIn() + expandHorizontally(),
                            exit = fadeOut() + shrinkHorizontally()
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelLarge,
                                color = contentColor,
                                maxLines = 1,
                                modifier = Modifier.padding(start = 6.dp)
                            )
                        }
                    }
                }
                onClose?.let { close ->
                    val closeLabel = stringResource(R.string.pc_control_close)
                    val contentColor = if (enabled) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    Box(
                        modifier = Modifier
                            .width(CloseButtonWidth)
                            .fillMaxHeight()
                            .padding(IndicatorPadding)
                            .clip(IndicatorShape)
                            .clickable(
                                enabled = enabled,
                                role = Role.Button,
                                onClick = close
                            )
                            .semantics { contentDescription = closeLabel },
                        contentAlignment = Alignment.Center
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
