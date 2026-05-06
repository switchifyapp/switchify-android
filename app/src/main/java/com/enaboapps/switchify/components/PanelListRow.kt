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
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.enaboapps.switchify.activities.ui.theme.SwitchifyTheme
import com.enaboapps.switchify.theme.Dimens

@Composable
fun PanelListRow(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    titleResId: Int? = null,
    runtimeTitle: String? = null,
    summaryResId: Int? = null,
    runtimeSummary: String? = null,
    leadingIcon: ImageVector? = null,
    trailing: @Composable () -> Unit = { DefaultChevron() }
) {
    val title = titleResId?.let { stringResource(it) } ?: runtimeTitle ?: ""
    val summary = runtimeSummary ?: summaryResId?.let { stringResource(it) }
    val scheme = MaterialTheme.colorScheme

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .background(scheme.surfaceColorAtElevation(1.dp))
            .clickable(onClick = onClick)
            .padding(Dimens.spaceM),
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
                text = title,
                style = MaterialTheme.typography.titleMedium
            )
            if (!summary.isNullOrBlank()) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant
                )
            }
        }
        trailing()
    }
}

@Composable
fun DefaultChevron() {
    Icon(
        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        modifier = Modifier.size(20.dp)
    )
}

@Preview(showBackground = true, name = "PanelListRow — title + summary + chevron")
@Composable
private fun PanelListRowPreview() {
    SwitchifyTheme {
        Panel(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            PanelListRow(
                onClick = {},
                runtimeTitle = "Settings",
                runtimeSummary = "Configure switches and scanning",
                leadingIcon = Icons.Rounded.Settings
            )
        }
    }
}

@Preview(showBackground = true, name = "PanelListRow — no icon, no summary")
@Composable
private fun PanelListRowMinimalPreview() {
    SwitchifyTheme {
        Panel(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            PanelListRow(
                onClick = {},
                runtimeTitle = "Privacy policy"
            )
        }
    }
}
