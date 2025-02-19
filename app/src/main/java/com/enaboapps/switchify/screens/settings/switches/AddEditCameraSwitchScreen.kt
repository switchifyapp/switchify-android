package com.enaboapps.switchify.screens.settings.switches

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.enaboapps.switchify.components.*
import com.enaboapps.switchify.screens.settings.switches.actions.SwitchActionPicker
import com.enaboapps.switchify.screens.settings.switches.models.AddEditCameraSwitchScreenModel
import com.enaboapps.switchify.switches.CameraSwitchFacialGesture
import kotlinx.coroutines.launch

@Composable
fun AddEditCameraSwitchScreen(navController: NavController, code: String? = null) {
    val context = LocalContext.current
    val viewModel = remember { AddEditCameraSwitchScreenModel().apply { init(code) } }

    BaseView(
        title = if (code == null) "Add Camera Switch" else "Edit Camera Switch",
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
            Section(title = "Facial Gesture") {
                val gestures = listOf(
                    CameraSwitchFacialGesture(CameraSwitchFacialGesture.SMILE),
                    CameraSwitchFacialGesture(CameraSwitchFacialGesture.LEFT_WINK),
                    CameraSwitchFacialGesture(CameraSwitchFacialGesture.RIGHT_WINK),
                    CameraSwitchFacialGesture(CameraSwitchFacialGesture.BLINK)
                )

                gestures.forEach { gesture ->
                    RadioButtonItem(
                        text = gesture.getName(),
                        description = gesture.getDescription(),
                        selected = viewModel.selectedGesture.value?.id == gesture.id,
                        onSelect = { viewModel.setGesture(gesture) }
                    )
                }
            }
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
            title = "Action",
            switchAction = viewModel.action.value,
            onChange = { viewModel.setAction(it) }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Facial Gesture Time
        PreferenceTimeStepper(
            value = viewModel.facialGestureTime.longValue,
            title = "Facial Gesture Time",
            summary = "The time to wait for a facial gesture to be detected",
            min = 100,
            max = 10000,
            step = 100,
            onValueChanged = { newValue ->
                viewModel.setFacialGestureTime(newValue)
            }
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Save Button
        FullWidthButton(
            text = "Save",
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
            FullWidthButton(
                text = "Delete",
                onClick = { viewModel.showDeleteConfirmation.value = true }
            )
        }
    }

    // Delete confirmation dialog
    if (viewModel.showDeleteConfirmation.value) {
        AlertDialog(
            onDismissRequest = { viewModel.showDeleteConfirmation.value = false },
            title = { Text("Confirm Deletion") },
            text = { Text("Are you sure you want to delete this switch?") },
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
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { viewModel.showDeleteConfirmation.value = false }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun RadioButtonItem(
    text: String,
    description: String,
    selected: Boolean,
    onSelect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        RadioButton(
            selected = selected,
            onClick = onSelect
        )
        Column(
            modifier = Modifier
                .padding(start = 16.dp)
        ) {
            Text(text = text, style = MaterialTheme.typography.titleMedium)
            Text(text = description, style = MaterialTheme.typography.bodyMedium)
        }
    }
} 