package com.enaboapps.switchify.screens.settings.switches

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.enaboapps.switchify.R
import com.enaboapps.switchify.components.ActionButton
import com.enaboapps.switchify.components.ActionButtonType
import com.enaboapps.switchify.components.BaseView
import com.enaboapps.switchify.components.Picker
import com.enaboapps.switchify.screens.settings.switches.actions.SwitchActionPicker
import com.enaboapps.switchify.screens.settings.switches.models.AddEditCameraSwitchScreenModel
import com.enaboapps.switchify.service.core.ServiceBridge
import com.enaboapps.switchify.switches.CameraSwitchFacialGesture
import com.enaboapps.switchify.switches.SupportedActionsPolicy
import kotlinx.coroutines.launch

@Composable
fun AddEditCameraSwitchScreen(navController: NavController, code: String? = null) {
    val context = LocalContext.current
    val viewModel = remember { AddEditCameraSwitchScreenModel().apply { init(code, context) } }

    BaseView(
        titleResId = if (code == null) R.string.screen_title_add_switch else R.string.screen_title_edit_switch,
        navController = navController,
        bottomBar = {
            val context = LocalContext.current
            val scope = rememberCoroutineScope()
            androidx.compose.foundation.layout.Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
            ) {
                ActionButton(
                    textResId = R.string.button_save,
                    enabled = viewModel.isValid.value,
                    onClick = {
                        viewModel.save(context) { success ->
                            scope.launch {
                                if (success) {
                                    navController.popBackStack()
                                } else {
                                    android.widget.Toast.makeText(
                                        context,
                                        "Error saving switch",
                                        android.widget.Toast.LENGTH_SHORT
                                    )
                                        .show()
                                }
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    applyPadding = false
                )
                if (code != null) {
                    ActionButton(
                        textResId = R.string.button_delete,
                        onClick = { viewModel.showDeleteConfirmation.value = true },
                        type = ActionButtonType.DESTRUCTIVE,
                        modifier = Modifier.weight(1f),
                        applyPadding = false
                    )
                }
            }
        }
    ) {
        var refresh by remember { mutableIntStateOf(0) }
        LaunchedEffect(Unit) {
            ServiceBridge.serviceEvents.collect { event ->
                if (event is ServiceBridge.ServiceEvent.ConfigurationUpdated) refresh++
            }
        }
        key(refresh) {
            MainContent(code, viewModel, navController)
        }
    }
}

@Composable
private fun MainContent(
    code: String?,
    viewModel: AddEditCameraSwitchScreenModel,
    navController: NavController
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        // Switch Name
        SwitchName(
            name = viewModel.name,
            onNameChange = { viewModel.updateName(it) }
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (code == null) {
            val gestures = com.enaboapps.switchify.service.face.FacialGestureRegistry
                .switchAssignableIds()
                .map { CameraSwitchFacialGesture(it) }

            Picker(
                titleResId = R.string.section_title_facial_gesture,
                selectedItem = viewModel.selectedGesture.value,
                items = gestures,
                onItemSelected = { gesture ->
                    viewModel.setGesture(gesture)
                },
                itemToString = { it.getName() },
                itemDescription = { it.getDescription() }
            )
        } else {
            Text(
                text = "This switch is already set up with ${viewModel.selectedGesture.value?.getName()}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Action Selection
        SwitchActionPicker(
            titleResId = R.string.section_title_action,
            switchAction = viewModel.action.value,
            onChange = { viewModel.setAction(it) },
            items = SupportedActionsPolicy.supportedActions(context)
        )

        Spacer(modifier = Modifier.height(24.dp))
    }

    // Delete confirmation dialog
    if (viewModel.showDeleteConfirmation.value) {
        AlertDialog(
            onDismissRequest = { viewModel.showDeleteConfirmation.value = false },
            title = { Text(stringResource(R.string.dialog_title_delete)) },
            text = { Text(stringResource(R.string.dialog_message_delete)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.showDeleteConfirmation.value = false
                        viewModel.delete(context) { success ->
                            scope.launch {
                                if (success) {
                                    navController.popBackStack()
                                } else {
                                    Toast.makeText(
                                        context,
                                        "Error deleting switch",
                                        Toast.LENGTH_SHORT
                                    )
                                        .show()
                                }
                            }
                        }
                    }
                ) {
                    Text(stringResource(R.string.button_delete))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { viewModel.showDeleteConfirmation.value = false }
                ) {
                    Text(stringResource(R.string.button_cancel))
                }
            }
        )
    }
}
