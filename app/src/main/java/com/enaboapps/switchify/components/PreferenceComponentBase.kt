package com.enaboapps.switchify.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.enaboapps.switchify.R
import com.enaboapps.switchify.theme.Dimens

@Composable
fun PreferenceComponentBase(
    titleResId: Int,
    summaryResId: Int,
    explanationResId: Int? = null,
    leadingIcon: ImageVector? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val rowModifier = Modifier
        .fillMaxWidth()
        .heightIn(min = 56.dp)
        .background(scheme.surfaceColorAtElevation(1.dp))
        .let { if (onClick != null) it.clickable(onClick = onClick) else it }
        .padding(Dimens.spaceM)

    Row(
        modifier = rowModifier,
        horizontalArrangement = Arrangement.spacedBy(Dimens.spaceM),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leadingIcon != null) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(scheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = scheme.primary,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(titleResId),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = stringResource(summaryResId),
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (explanationResId != null) {
            ExplanationButton(titleResId = titleResId, explanationResId = explanationResId)
        }
        trailing()
    }
}

@Composable
private fun ExplanationButton(titleResId: Int, explanationResId: Int) {
    var showExplanationDialog by remember { mutableStateOf(false) }

    IconButton(onClick = { showExplanationDialog = true }) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.Help,
            contentDescription = stringResource(R.string.help),
            tint = MaterialTheme.colorScheme.primary
        )
    }

    if (showExplanationDialog) {
        AlertDialog(
            onDismissRequest = { showExplanationDialog = false },
            title = { Text(stringResource(titleResId)) },
            text = { Text(stringResource(explanationResId)) },
            confirmButton = {
                TextButton(onClick = { showExplanationDialog = false }) {
                    Text(stringResource(R.string.ok))
                }
            }
        )
    }
}
