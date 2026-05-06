package com.enaboapps.switchify.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.enaboapps.switchify.theme.Dimens

enum class AlertSeverity { ERROR, WARNING, INFO }

private data class AlertColors(val container: Color, val onContainer: Color)

@Composable
private fun colorsFor(severity: AlertSeverity): AlertColors {
    val scheme = MaterialTheme.colorScheme
    return when (severity) {
        AlertSeverity.ERROR -> AlertColors(scheme.errorContainer, scheme.onErrorContainer)
        AlertSeverity.WARNING -> AlertColors(scheme.secondaryContainer, scheme.onSecondaryContainer)
        AlertSeverity.INFO -> AlertColors(scheme.surfaceVariant, scheme.onSurfaceVariant)
    }
}

@Composable
fun InlineAlertCard(
    severity: AlertSeverity,
    titleResId: Int,
    descriptionResId: Int,
    leadingIcon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    descriptionArgs: Array<Any>? = null,
    shape: Shape = MaterialTheme.shapes.small
) {
    val colors = colorsFor(severity)
    Panel(
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        containerColor = colors.container,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimens.spaceM),
            horizontalArrangement = Arrangement.spacedBy(Dimens.spaceS),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = colors.onContainer,
                modifier = Modifier.size(24.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(titleResId),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onContainer
                )
                Text(
                    text = if (descriptionArgs != null) {
                        stringResource(descriptionResId, *descriptionArgs)
                    } else {
                        stringResource(descriptionResId)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onContainer.copy(alpha = 0.85f)
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = colors.onContainer.copy(alpha = 0.7f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
