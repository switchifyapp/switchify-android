package com.enaboapps.switchify.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.enaboapps.switchify.R

@Composable
fun PreferenceComponentBase(
    titleResId: Int? = null,
    summaryResId: Int? = null,
    runtimeTitle: String? = null,
    runtimeSummary: String? = null,
    explanationResId: Int? = null,
    leadingIcon: ImageVector? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit = {},
    belowContent: (@Composable () -> Unit)? = null
) {
    val title = runtimeTitle ?: titleResId?.let { stringResource(it) }.orEmpty()
    val summary = runtimeSummary ?: summaryResId?.let { stringResource(it) }.orEmpty()

    PreferenceRowScaffold(
        title = title,
        summary = summary,
        onClick = onClick,
        leadingContent = leadingIcon?.let {
            {
                PreferenceRowLeadingIcon(imageVector = it)
            }
        },
        belowContent = belowContent
    ) {
        if (titleResId != null && explanationResId != null) {
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
