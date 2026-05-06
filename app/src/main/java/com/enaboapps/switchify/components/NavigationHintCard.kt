package com.enaboapps.switchify.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun NavigationHintCard(
    titleResId: Int,
    descriptionResId: Int,
    onNavigate: () -> Unit,
    modifier: Modifier = Modifier
) {
    PanelListRow(
        titleResId = titleResId,
        summaryResId = descriptionResId,
        onClick = onNavigate,
        modifier = modifier
    )
}
