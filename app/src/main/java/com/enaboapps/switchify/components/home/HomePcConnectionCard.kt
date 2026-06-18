package com.enaboapps.switchify.components.home

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.NavController
import com.enaboapps.switchify.R
import com.enaboapps.switchify.components.PanelListRow
import com.enaboapps.switchify.nav.NavigationRoute
import com.enaboapps.switchify.pc.PcConnectionState
import com.enaboapps.switchify.pc.PcConnectionStateHolder

@Composable
fun HomePcConnectionCard(navController: NavController) {
    val connectionState by PcConnectionStateHolder.connectionState.collectAsState()
    val connected = connectionState as? PcConnectionState.Connected
    PanelListRow(
        titleResId = R.string.pc_connection_title,
        runtimeSummary = connected?.let { "Connected to ${it.displayName}." },
        summaryResId = if (connected == null) R.string.pc_connection_home_summary else null,
        leadingIcon = Icons.Rounded.Computer,
        onClick = { navController.navigate(NavigationRoute.PcConnection.name) }
    )
}
