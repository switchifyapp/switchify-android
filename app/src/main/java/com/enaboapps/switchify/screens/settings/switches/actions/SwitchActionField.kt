package com.enaboapps.switchify.screens.settings.switches.actions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.enaboapps.switchify.R
import com.enaboapps.switchify.nav.NavigationRoute
import com.enaboapps.switchify.switches.SwitchAction
import com.enaboapps.switchify.theme.Dimens

/**
 * A field that displays the current switch action and navigates to
 * [SwitchActionSelectionScreen] when clicked to select a different action.
 *
 * @param navController Navigation controller for navigating and receiving results.
 * @param fieldId Unique identifier for this field to distinguish results when multiple fields exist.
 * @param titleResId Resource ID for the field title.
 * @param titleResIdArgs Optional format arguments for the title resource.
 * @param switchAction The currently selected action.
 * @param onChange Callback invoked when a new action is selected.
 * @param onDelete Optional callback for delete button (for long press actions).
 */
@Composable
fun SwitchActionField(
    navController: NavController,
    fieldId: String,
    titleResId: Int,
    titleResIdArgs: Array<Any>? = null,
    switchAction: SwitchAction,
    onChange: (SwitchAction) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    val title = if (titleResIdArgs != null) {
        stringResource(titleResId, *titleResIdArgs)
    } else {
        stringResource(titleResId)
    }

    // Track if this field initiated the navigation
    var waitingForResult by remember { mutableStateOf(false) }

    // Listen for navigation result only when this field is waiting
    LaunchedEffect(waitingForResult) {
        if (waitingForResult) {
            val result = navController.currentBackStackEntry
                ?.savedStateHandle
                ?.get<Int>(SELECTED_ACTION_ID_KEY)
            if (result != null) {
                onChange(SwitchAction(result))
                navController.currentBackStackEntry
                    ?.savedStateHandle
                    ?.remove<Int>(SELECTED_ACTION_ID_KEY)
                waitingForResult = false
            }
        }
    }

    // Also check result when composition happens (after navigation back)
    LaunchedEffect(navController.currentBackStackEntry) {
        if (waitingForResult) {
            val result = navController.currentBackStackEntry
                ?.savedStateHandle
                ?.get<Int>(SELECTED_ACTION_ID_KEY)
            if (result != null) {
                onChange(SwitchAction(result))
                navController.currentBackStackEntry
                    ?.savedStateHandle
                    ?.remove<Int>(SELECTED_ACTION_ID_KEY)
                waitingForResult = false
            }
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clickable {
                waitingForResult = true
                navController.navigate(
                    "${NavigationRoute.SwitchActionSelection.name}/${switchAction.id}"
                )
            }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(20.dp)
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                Text(
                    text = title.uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(Dimens.spaceXs))
                Text(
                    text = switchAction.getActionName(),
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(Dimens.spaceXs))
                Text(
                    text = switchAction.getActionDescription(),
                    style = MaterialTheme.typography.bodySmall
                )
            }

            if (onDelete != null) {
                TextButton(onClick = onDelete) {
                    Text(text = stringResource(R.string.button_delete))
                }
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                modifier = Modifier.padding(start = Dimens.spaceM)
            )
        }
    }
}
