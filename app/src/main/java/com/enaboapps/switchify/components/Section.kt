package com.enaboapps.switchify.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Feedback
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.enaboapps.switchify.activities.ui.theme.SwitchifyTheme
import com.enaboapps.switchify.theme.Dimens

// Sections use a tighter corner than the theme's large (48.dp) shape.
private val SectionShape = RoundedCornerShape(24.dp)

@Composable
fun Section(
    titleResId: Int,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = Dimens.spaceL)
    ) {
        Text(
            text = stringResource(titleResId).uppercase(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(
                horizontal = Dimens.spaceM,
                vertical = Dimens.spaceXs
            )
        )
        Panel(modifier = Modifier.fillMaxWidth(), shape = SectionShape) {
            Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                content()
            }
        }
    }
}

@Preview(showBackground = true, name = "Section — eyebrow + panel of rows")
@Composable
private fun SectionPreview() {
    SwitchifyTheme {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "GENERAL",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(
                    horizontal = Dimens.spaceM,
                    vertical = Dimens.spaceXs
                )
            )
            Panel(modifier = Modifier.fillMaxWidth(), shape = SectionShape) {
                Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                    PanelListRow(
                        onClick = {},
                        runtimeTitle = "Settings",
                        runtimeSummary = "Configure switches and scanning",
                        leadingIcon = Icons.Rounded.Settings
                    )
                    PanelListRow(
                        onClick = {},
                        runtimeTitle = "Send feedback",
                        runtimeSummary = "Share your thoughts",
                        leadingIcon = Icons.Rounded.Feedback
                    )
                }
            }
        }
    }
}
