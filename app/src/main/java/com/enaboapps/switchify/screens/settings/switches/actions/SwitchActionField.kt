package com.enaboapps.switchify.screens.settings.switches.actions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import com.enaboapps.switchify.components.Panel
import com.enaboapps.switchify.screens.settings.switches.SwitchProfileRoutes
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
    profileId: String,
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
    val requestId = "$profileId:$titleResId:${titleResIdArgs?.joinToString()}"

    // Observe the SavedStateHandle result using LaunchedEffect with the result as key
    val entry = androidx.lifecycle.compose.LocalLifecycleOwner.current as androidx.navigation.NavBackStackEntry
    val savedStateHandle = entry.savedStateHandle
    val resultActionId by savedStateHandle.getStateFlow<Int?>(SELECTED_ACTION_ID_KEY, null).collectAsState()

    // When we have a result and we're waiting for it, consume it
    LaunchedEffect(resultActionId, waitingForResult) {
        val selectedId = resultActionId
        if (selectedId != null && savedStateHandle.get<String>("action_request_id") == requestId) {
            onChange(SwitchAction(selectedId, savedStateHandle.get<String>(SELECTED_ACTION_PACKAGE_KEY)))
            savedStateHandle.remove<String>(SELECTED_ACTION_PACKAGE_KEY)
            savedStateHandle.set<Int?>(SELECTED_ACTION_ID_KEY, null)
            waitingForResult = false
        }
    }

    Panel(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        shape = MaterialTheme.shapes.medium,
        onClick = {
            waitingForResult = true
            savedStateHandle?.set("action_request_id", requestId)
            navController.navigate(
                SwitchProfileRoutes.actionSelection(profileId, switchAction.id)
            )
        }
    ) {
        BoxWithConstraints {
            val compact = maxWidth < 360.dp
            if (compact) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (reorderControls != null) {
                            reorderControls()
                        } else {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (onDelete != null) {
                                IconButton(onClick = onDelete) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = stringResource(R.string.button_delete),
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(Dimens.spaceS))
                    SwitchActionText(
                        title = title,
                        switchAction = switchAction,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(20.dp)
                ) {
                    if (reorderControls != null) {
                        reorderControls()
                    }

                    SwitchActionText(
                        title = title,
                        switchAction = switchAction,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    )

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
    }
}

@Composable
private fun SwitchActionText(
    title: String,
    switchAction: SwitchAction,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
    ) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(Dimens.spaceXs))
        Text(
            text = switchAction.getDisplayName(androidx.compose.ui.platform.LocalContext.current),
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(modifier = Modifier.height(Dimens.spaceXs))
        Text(
            text = switchAction.getActionDescription(),
            style = MaterialTheme.typography.bodySmall
        )
    }
}
