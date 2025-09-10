package com.enaboapps.switchify.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun NavigationHintCard(
    titleResId: Int,
    descriptionResId: Int,
    onNavigate: () -> Unit,
    modifier: Modifier = Modifier
) {
    UICard(
        modifier = modifier,
        titleResId = titleResId,
        descriptionResId = descriptionResId,
        rightIcon = Icons.AutoMirrored.Rounded.ArrowForward,
        onClick = onNavigate
    )
}