package com.enaboapps.switchify.components.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.enaboapps.switchify.components.HeadControlToggleCard

@Composable
fun HomeToggleRow(
    showHeadToggle: Boolean,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        SettingsToggleCard(onClick = onSettingsClick)
        if (showHeadToggle) {
            HeadControlToggleCard()
        }
    }
}
