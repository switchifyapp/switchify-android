package com.enaboapps.switchify.screens.settings.switches

import android.widget.Toast
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.enaboapps.switchify.components.BaseView
import com.enaboapps.switchify.components.FullWidthButton
import com.enaboapps.switchify.components.TextArea
import com.enaboapps.switchify.screens.settings.switches.actions.SwitchActionPicker
import com.enaboapps.switchify.screens.settings.switches.models.AddEditExternalSwitchScreenModel
import com.enaboapps.switchify.switches.SwitchAction
import kotlinx.coroutines.launch

@Composable
fun AddEditExternalSwitchScreen(navController: NavController, code: String? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val addEditExternalSwitchScreenModel = remember {
        AddEditExternalSwitchScreenModel().apply {
            init(code, context)
        }
    }
    val shouldSave by addEditExternalSwitchScreenModel.shouldSave.observeAsState()
    val isValid by addEditExternalSwitchScreenModel.isValid.observeAsState()
    val editing = code != null
    val captured by addEditExternalSwitchScreenModel.switchCaptured.observeAsState()
    val screenTitle = if (editing) "Edit Switch" else "Add New Switch"
    val showDeleteConfirmation = remember { mutableStateOf(false) }

    if (!captured!!) {
        SwitchListener(navController = navController, onKeyEvent = { keyEvent: KeyEvent ->
            addEditExternalSwitchScreenModel.processKeyCode(keyEvent.key, context)
        })
    } else {
        BaseView(
            title = screenTitle,
            navController = navController
        ) {
            SwitchName(name = addEditExternalSwitchScreenModel.name, onNameChange = {
                addEditExternalSwitchScreenModel.updateName(it)
            })
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally
            ) {
                SwitchActionSection(addEditExternalSwitchScreenModel)
                if (shouldSave!!) {
                    FullWidthButton(text = "Save", enabled = isValid!!, onClick = {
                        addEditExternalSwitchScreenModel.save(context) { success ->
                            scope.launch {
                                if (success) {
                                    navController.popBackStack()
                                } else {
                                    Toast.makeText(
                                        context,
                                        "Error saving switch",
                                        Toast.LENGTH_SHORT
                                    )
                                        .show()
                                }
                            }
                        }
                    })
                }
                if (editing) {
                    FullWidthButton(text = "Delete", onClick = {
                        showDeleteConfirmation.value = true
                    })
                }
            }

            if (showDeleteConfirmation.value) {
                AlertDialog(
                    onDismissRequest = { showDeleteConfirmation.value = false },
                    title = { Text("Confirm Deletion") },
                    text = { Text("Are you sure you want to delete this switch?") },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showDeleteConfirmation.value = false
                                addEditExternalSwitchScreenModel.delete(context) { success ->
                                    scope.launch {
                                        if (success) {
                                            navController.popBackStack()
                                        } else {
                                            Toast.makeText(
                                                context,
                                                "Error deleting switch",
                                                Toast.LENGTH_SHORT
                                            ).show()
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
                            onClick = { showDeleteConfirmation.value = false }
                        ) {
                            Text("Cancel")
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun SwitchListener(navController: NavController, onKeyEvent: (KeyEvent) -> Unit) {
    val requester = remember { FocusRequester() }
    Column(modifier = Modifier
        .padding(16.dp)
        .onKeyEvent { keyEvent ->
            onKeyEvent(keyEvent)
            true
        }
        .fillMaxWidth()
        .focusRequester(requester)
        .focusable(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center) {
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = "Press the switch that you want to use",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.padding(8.dp))
        Text(
            text = "Is your switch not working? " +
                    "If you are using a USB switch, please make sure that you have it plugged in and that it is turned on. " +
                    "If you are using a Bluetooth switch, please make sure that it is paired with your device.",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.padding(16.dp))
        FullWidthButton(text = "Cancel", onClick = {
            navController.popBackStack()
        })
        Spacer(modifier = Modifier.weight(1f))
    }
    LaunchedEffect(requester) {
        requester.requestFocus()
    }
}

@Composable
fun SwitchName(
    name: String = "",
    onNameChange: (String) -> Unit
) {
    var name by remember { mutableStateOf(name) }

    TextArea(
        value = name,
        onValueChange = {
            name = it
            onNameChange(it)
        },
        label = "Switch Name",
        isError = name.isBlank(),
        supportingText = "Switch name is required"
    )
}

@Composable
fun SwitchActionSection(viewModel: AddEditExternalSwitchScreenModel) {
    val allowLongPress = viewModel.allowLongPress.observeAsState()
    val longPressActions = viewModel.longPressActions.observeAsState()
    val refreshingLongPressActions = viewModel.refreshingLongPressActions.observeAsState()
    val context = LocalContext.current
    SwitchActionPicker(
        title = "Press Action",
        switchAction = viewModel.pressAction.value!!,
        onChange = {
            viewModel.setPressAction(it, context)
        }
    )

    Spacer(modifier = Modifier.padding(16.dp))

    if (allowLongPress.value!! && !refreshingLongPressActions.value!!) {
        Text(
            text = "Each switch can have multiple actions for long press. " +
                    "You can add or remove actions below. " +
                    "The actions will be executed in the order they are listed based on the duration of the long press.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 20.dp)
        )

        longPressActions.value?.forEachIndexed { index, action ->
            SwitchActionPicker(
                title = "Long Press Action ${index + 1}",
                switchAction = action,
                onChange = { newAction ->
                    viewModel.updateLongPressAction(action, newAction)
                },
                onDelete = {
                    viewModel.removeLongPressAction(index)
                }
            )
        }
        FullWidthButton(text = "Add Long Press Action", onClick = {
            viewModel.addLongPressAction(SwitchAction(SwitchAction.ACTION_SELECT))
        })
    }
}
