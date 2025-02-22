package com.enaboapps.switchify.screens.settings.actions

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.enaboapps.switchify.R
import com.enaboapps.switchify.components.BaseView
import com.enaboapps.switchify.components.NavRouteLink
import com.enaboapps.switchify.components.Section
import com.enaboapps.switchify.nav.NavigationRoute
import com.enaboapps.switchify.service.custom.actions.store.Action
import com.enaboapps.switchify.service.custom.actions.store.ActionStore

@Composable
fun MyActionsScreen(navController: NavController) {
    val context = LocalContext.current
    val actionStore = ActionStore(context)
    remember { SnackbarHostState() }

    val actions = remember { mutableStateListOf<Action>() }
    actions.addAll(actionStore.getActions())

    BaseView(
        titleResId = R.string.screen_title_my_actions,
        navController = navController,
        enableScroll = false,
        floatingActionButton = {
            FloatingActionButton(onClick = {
                navController.navigate(NavigationRoute.AddMyActionsMenuItem.name)
            }) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add"
                )
            }
        }
    ) {
        Text(
            text = stringResource(R.string.actions_my_actions_desc),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(16.dp)
        )
        Section(titleResId = R.string.actions_my_actions_title) {
            if (actions.isEmpty()) {
                Box(modifier = Modifier.padding(16.dp), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.actions_my_actions_empty),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            } else {
                actions.forEach { action ->
                    ActionItem(
                        id = action.id,
                        action = action.text,
                        navController = navController
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionItem(
    id: String,
    action: String,
    navController: NavController
) {
    NavRouteLink(
        runtimeTitle = action,
        summaryResId = R.string.action_edit_summary,
        navController = navController,
        route = "${NavigationRoute.EditMyActionsMenuItem.name}/${id}"
    )
}
