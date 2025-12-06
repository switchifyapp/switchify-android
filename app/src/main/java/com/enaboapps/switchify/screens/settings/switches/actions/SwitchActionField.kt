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
import androidx.compose.runtime.saveable.rememberSaveable
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
 * @param titleResId Resource ID for the field title.
 * @param titleResIdArgs Optional format arguments for the title resource.
 * @param switchAction The currently selected action.
 * @param onChange Callback invoked when a new action is selected.
 * @param onDelete Optional callback for delete button (for long press actions).
 * @param reorderControls Optional composable for reorder controls (drag handle or arrows).
 */
@Composable
fun SwitchActionField(
    navController: NavController,
    titleResId: Int,
    titleResIdArgs: Array<Any>? = null,
    switchAction: SwitchAction,
    onChange: (SwitchAction) -> Unit,
    onDelete: (() -> Unit)? = null,
    reorderControls: (@Composable () -> Unit)? = null
) {
    val title = if (titleResIdArgs != null) {
        stringResource(titleResId, *titleResIdArgs)
    } else {
        stringResource(titleResId)
    }

    // Track if this field initiated the navigation - use rememberSaveable to persist across recompositions
    var waitingForResult by rememberSaveable { mutableStateOf(false) }

    // Observe the SavedStateHandle result using LaunchedEffect with the result as key
    val savedStateHandle = navController.currentBackStackEntry?.savedStateHandle
    val resultActionId = savedStateHandle?.get<Int>(SELECTED_ACTION_ID_KEY)

    // When we have a result and we're waiting for it, consume it
    LaunchedEffect(resultActionId, waitingForResult) {
        if (waitingForResult && resultActionId != null) {
            onChange(SwitchAction(resultActionId))
            savedStateHandle?.remove<Int>(SELECTED_ACTION_ID_KEY)
            waitingForResult = false
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
            // Reorder controls (if provided)
            if (reorderControls != null) {
                reorderControls()
            }

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
