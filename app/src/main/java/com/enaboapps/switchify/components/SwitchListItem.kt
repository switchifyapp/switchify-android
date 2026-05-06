package com.enaboapps.switchify.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.enaboapps.switchify.R
import com.enaboapps.switchify.theme.Dimens

@Composable
fun SwitchListItem(
    // Core switch information
    switchName: String,
    switchType: SwitchType,

    // Action information
    primaryAction: SwitchAction,
    secondaryActions: List<SwitchAction> = emptyList(),

    // State and meta
    isEnabled: Boolean = true,
    hasConfigurationIssues: Boolean = false,

    // Interaction
    onClick: () -> Unit
) {
    Panel(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = Dimens.spaceXs),
        shape = MaterialTheme.shapes.medium,
        onClick = { if (isEnabled) onClick() }
    ) {
        Column(
            modifier = Modifier.padding(Dimens.spaceM)
        ) {
            // Header: Switch name, type indicator, status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    // Switch type icon
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(
                                switchType.color.copy(alpha = 0.12f)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = switchType.icon,
                            contentDescription = null,
                            tint = switchType.color,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(Dimens.spaceS))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = switchName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isEnabled)
                                MaterialTheme.colorScheme.onSurface
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Text(
                            text = stringResource(switchType.displayNameRes),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Status indicators
                    if (hasConfigurationIssues) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Configuration issues",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(Dimens.spaceXs))
                    }

                    if (!isEnabled) {
                        Icon(
                            imageVector = Icons.Default.Block,
                            contentDescription = "Disabled",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(Dimens.spaceXs))
                    }

                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(Dimens.spaceS))

            // Primary action - most prominent
            ActionCard(
                action = primaryAction,
                isPrimary = true,
                isEnabled = isEnabled
            )

            // Secondary actions - less prominent but clearly organized
            if (secondaryActions.isNotEmpty()) {
                Spacer(modifier = Modifier.height(Dimens.spaceS))

                Text(
                    text = stringResource(R.string.switch_additional_actions),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )

                Spacer(modifier = Modifier.height(Dimens.spaceXs))

                Column(
                    verticalArrangement = Arrangement.spacedBy(Dimens.spaceXs)
                ) {
                    secondaryActions.take(2).forEach { action ->
                        ActionCard(
                            action = action,
                            isPrimary = false,
                            isEnabled = isEnabled
                        )
                    }

                    if (secondaryActions.size > 2) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(Dimens.spaceXs))
                                .background(MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp))
                                .padding(horizontal = Dimens.spaceS, vertical = Dimens.spaceXs),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = stringResource(
                                    R.string.switch_more_actions,
                                    secondaryActions.size - 2
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionCard(
    action: SwitchAction,
    isPrimary: Boolean,
    isEnabled: Boolean
) {
    val backgroundColor = if (isPrimary) {
        MaterialTheme.colorScheme.primaryContainer.copy(
            alpha = if (isEnabled) 1f else 0.4f
        )
    } else {
        MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp).copy(
            alpha = if (isEnabled) 1f else 0.4f
        )
    }

    val textColor = if (isPrimary) {
        MaterialTheme.colorScheme.onPrimaryContainer.let {
            if (isEnabled) it else it.copy(alpha = 0.6f)
        }
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.let {
            if (isEnabled) it else it.copy(alpha = 0.6f)
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.spaceXs))
            .background(backgroundColor)
            .padding(horizontal = Dimens.spaceS, vertical = Dimens.spaceXs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = action.trigger,
            style = if (isPrimary)
                MaterialTheme.typography.labelMedium
            else
                MaterialTheme.typography.labelSmall,
            color = textColor,
            fontWeight = if (isPrimary) FontWeight.SemiBold else FontWeight.Medium
        )

        Spacer(modifier = Modifier.width(Dimens.spaceXs))

        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = textColor,
            modifier = Modifier.size(12.dp)
        )

        Spacer(modifier = Modifier.width(Dimens.spaceXs))

        Text(
            text = action.actionName,
            style = if (isPrimary)
                MaterialTheme.typography.bodyMedium
            else
                MaterialTheme.typography.bodySmall,
            color = textColor,
            fontWeight = if (isPrimary) FontWeight.Medium else FontWeight.Normal,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// Data classes for the new structure
data class SwitchAction(
    val trigger: String,      // "Press", "Hold", "Blink", etc.
    val actionName: String    // "Left Click", "Open App", etc.
)

enum class SwitchType(
    val displayNameRes: Int,
    val icon: ImageVector,
    val color: androidx.compose.ui.graphics.Color
) {
    EXTERNAL(
        R.string.switch_type_external,
        Icons.Default.Cable,
        androidx.compose.ui.graphics.Color(0xFF2196F3)
    ),
    CAMERA(
        R.string.switch_type_camera,
        Icons.Default.Camera,
        androidx.compose.ui.graphics.Color(0xFF4CAF50)
    )
}

