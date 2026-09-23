package com.enaboapps.switchify.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun AppNameRow(
    name: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    leadingContent: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null
) {
    BoxWithConstraints(modifier.fillMaxWidth().then(
        if (onClick == null) Modifier else Modifier.clickable(onClick = onClick)
    ).padding(horizontal = 16.dp, vertical = 12.dp)) {
        if (maxWidth < 360.dp && leadingContent != null) {
            Column {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    leadingContent()
                    trailingContent?.invoke()
                }
                Text(name, style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
            }
        } else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                leadingContent?.invoke()
                Text(name, style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp))
                trailingContent?.invoke()
            }
        }
    }
}
