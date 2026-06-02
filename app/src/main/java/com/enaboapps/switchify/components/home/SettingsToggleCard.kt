package com.enaboapps.switchify.components.home

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.enaboapps.switchify.R
import com.enaboapps.switchify.components.PreferenceRowLeadingIcon
import com.enaboapps.switchify.components.PreferenceRowScaffold

@Composable
fun SettingsToggleCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    PreferenceRowScaffold(
        title = stringResource(R.string.screen_title_settings),
        summary = stringResource(R.string.screen_summary_settings),
        modifier = modifier,
        onClick = onClick,
        leadingContent = {
            PreferenceRowLeadingIcon(imageVector = Icons.Rounded.Settings)
        }
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = scheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.size(20.dp)
        )
    }
}
