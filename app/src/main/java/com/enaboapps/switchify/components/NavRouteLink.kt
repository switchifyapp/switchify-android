package com.enaboapps.switchify.components

import androidx.compose.runtime.Composable
import androidx.navigation.NavController

@Composable
fun NavRouteLink(
    titleResId: Int? = null,
    runtimeTitle: String? = null,
    summaryResId: Int? = null,
    navController: NavController,
    route: String
) {
    PanelListRow(
        titleResId = titleResId,
        runtimeTitle = runtimeTitle,
        summaryResId = summaryResId,
        onClick = { navController.navigate(route) }
    )
}
