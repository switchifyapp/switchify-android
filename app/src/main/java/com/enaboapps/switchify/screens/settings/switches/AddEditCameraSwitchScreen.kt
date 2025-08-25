package com.enaboapps.switchify.screens.settings.switches

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.enaboapps.switchify.R
import com.enaboapps.switchify.nav.NavigationRoute
import com.enaboapps.switchify.components.BaseView
import com.enaboapps.switchify.components.ActionButton
import com.enaboapps.switchify.components.ActionButtonType
import com.enaboapps.switchify.components.Picker
import com.enaboapps.switchify.components.PreferenceValueSelector
import com.enaboapps.switchify.components.PreferenceTimeStepper
import com.enaboapps.switchify.components.TextArea
import com.enaboapps.switchify.screens.settings.switches.actions.SwitchActionPicker
import com.enaboapps.switchify.screens.settings.switches.models.AddEditCameraSwitchScreenModel
import com.enaboapps.switchify.service.switches.camera.CameraSwitchManager
import com.enaboapps.switchify.switches.CameraSwitchFacialGesture
import kotlinx.coroutines.launch

@Composable
fun AddEditCameraSwitchScreen(navController: NavController, code: String? = null) {
    val context = LocalContext.current
    val viewModel = remember { AddEditCameraSwitchScreenModel().apply { init(code, context) } }

    BaseView(
        titleResId = if (code == null) R.string.screen_title_add_switch else R.string.screen_title_edit_switch,
        navController = navController
    ) {
        MainContent(code, viewModel, navController)
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
            // Facial Gesture Selection
            val gestures = listOf(
                CameraSwitchFacialGesture(CameraSwitchFacialGesture.SMILE),
                CameraSwitchFacialGesture(CameraSwitchFacialGesture.LEFT_WINK),
                CameraSwitchFacialGesture(CameraSwitchFacialGesture.RIGHT_WINK),
                CameraSwitchFacialGesture(CameraSwitchFacialGesture.BLINK),
                CameraSwitchFacialGesture(CameraSwitchFacialGesture.HEAD_TURN_LEFT),
                CameraSwitchFacialGesture(CameraSwitchFacialGesture.HEAD_TURN_RIGHT),
                CameraSwitchFacialGesture(CameraSwitchFacialGesture.HEAD_TURN_UP),
                CameraSwitchFacialGesture(CameraSwitchFacialGesture.HEAD_TURN_DOWN)
            )

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
            onChange = { viewModel.setAction(it) }
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Save Button
        ActionButton(
            textResId = R.string.button_save,
            enabled = viewModel.isValid.value,
            onClick = {
                viewModel.save(context) { success ->
                    scope.launch {
                        if (success) {
                            navController.popBackStack()
                        } else {
                            Toast.makeText(context, "Error saving switch", Toast.LENGTH_SHORT)
                                .show()
                        }
                    }
                }
            }
        )

        // Delete Button (only show when editing)
        if (code != null) {
            Spacer(modifier = Modifier.height(8.dp))
            ActionButton(
                textResId = R.string.button_delete,
                onClick = { viewModel.showDeleteConfirmation.value = true },
                type = ActionButtonType.DESTRUCTIVE
            )
        }
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