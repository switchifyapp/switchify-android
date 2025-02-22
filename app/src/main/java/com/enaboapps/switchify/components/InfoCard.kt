package com.enaboapps.switchify.components

import androidx.compose.runtime.Composable

@Composable
fun InfoCard(
    titleResId: Int,
    descriptionResId: Int
) {
    UICard(titleResId = titleResId, descriptionResId = descriptionResId, onClick = {})
}