package com.enaboapps.switchify.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.enaboapps.switchify.theme.Dimens

@Composable
fun PreferenceRowScaffold(
    title: String,
    summary: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    leadingContent: (@Composable () -> Unit)? = null,
    belowContent: (@Composable () -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val rowModifier = modifier
        .fillMaxWidth()
        .heightIn(min = 56.dp)
        .background(scheme.surfaceColorAtElevation(1.dp))
        .let { if (onClick != null) it.clickable(enabled = enabled, onClick = onClick) else it }
        .padding(Dimens.spaceM)

    if (belowContent == null) {
        Row(
            modifier = rowModifier,
            horizontalArrangement = Arrangement.spacedBy(Dimens.spaceM),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PreferenceRowText(
                title = title,
                summary = summary,
                modifier = Modifier.weight(1f),
                leadingContent = leadingContent
            )
            trailing()
        }
    } else {
        Column(
            modifier = rowModifier,
            verticalArrangement = Arrangement.spacedBy(Dimens.spaceS)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Dimens.spaceM),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PreferenceRowText(
                    title = title,
                    summary = summary,
                    modifier = Modifier.weight(1f),
                    leadingContent = leadingContent
                )
                trailing()
            }
            belowContent()
        }
    }
}

@Composable
private fun PreferenceRowText(
    title: String,
    summary: String,
    modifier: Modifier = Modifier,
    leadingContent: (@Composable () -> Unit)? = null
) {
    leadingContent?.invoke()
    Column(modifier = modifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = summary,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun PreferenceRowLeadingIcon(
    modifier: Modifier = Modifier,
    iconModifier: Modifier = Modifier,
    imageVector: ImageVector? = null,
    painter: Painter? = null,
    contentDescription: String? = null
) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(scheme.primary.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center
    ) {
        if (imageVector != null) {
            Icon(
                imageVector = imageVector,
                contentDescription = contentDescription,
                tint = scheme.primary,
                modifier = iconModifier.size(22.dp)
            )
        } else if (painter != null) {
            Icon(
                painter = painter,
                contentDescription = contentDescription,
                tint = scheme.primary,
                modifier = iconModifier.size(22.dp)
            )
        }
    }
}
