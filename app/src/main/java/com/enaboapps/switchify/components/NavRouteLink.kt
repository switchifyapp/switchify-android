package com.enaboapps.switchify.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.enaboapps.switchify.theme.Dimens
import androidx.navigation.NavController

@Composable
fun NavRouteLink(
    titleResId: Int? = null,
    runtimeTitle: String? = null,
    summaryResId: Int? = null,
    navController: NavController,
    route: String
) {
    val title = titleResId?.let { stringResource(it) } ?: runtimeTitle ?: ""
    UICard(
        modifier = Modifier.padding(bottom = Dimens.spaceXs),
        runtimeTitle = title,
        descriptionResId = summaryResId,
        rightIcon = Icons.AutoMirrored.Filled.ArrowForward,
        onClick = {
            navController.navigate(route)
        })
}
