package com.enaboapps.switchify.screens.settings.switches

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.foundation.layout.size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.enaboapps.switchify.R
import com.enaboapps.switchify.components.BaseView
import com.enaboapps.switchify.components.ActionButton
import com.enaboapps.switchify.components.ActionButtonType
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
    val screenTitle =
        if (editing) R.string.screen_title_edit_switch else R.string.screen_title_add_switch
    val showDeleteConfirmation = remember { mutableStateOf(false) }

    if (!captured!!) {
        SwitchListener(navController = navController, onKeyEvent = { keyEvent: KeyEvent ->
            addEditExternalSwitchScreenModel.processKeyCode(keyEvent.key, context)
        })
    } else {
        BaseView(
            titleResId = screenTitle,
            navController = navController
        ) {
            SwitchName(
                name = addEditExternalSwitchScreenModel.name,
                onNameChange = { addEditExternalSwitchScreenModel.updateName(it) }
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally
            ) {
                SwitchActionSection(addEditExternalSwitchScreenModel)
                if (shouldSave!!) {
                    ActionButton(
                        textResId = R.string.button_save,
                        enabled = isValid!!,
                        onClick = {
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
                        }
                    )
                }
                if (editing) {
                    ActionButton(
                        textResId = R.string.button_delete,
                        type = ActionButtonType.DESTRUCTIVE,
                        onClick = {
                            showDeleteConfirmation.value = true
                        }
                    )
                }
            }

            if (showDeleteConfirmation.value) {
                AlertDialog(
                    onDismissRequest = { showDeleteConfirmation.value = false },
                    title = { Text(stringResource(R.string.dialog_title_delete)) },
                    text = { Text(stringResource(R.string.dialog_message_delete)) },
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
                            Text(stringResource(R.string.button_delete))
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = { showDeleteConfirmation.value = false }) {
                            Text(stringResource(R.string.button_cancel))
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
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.background)
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
        Image(
            painter = painterResource(id = R.drawable.ic_hand_switch_press),
            contentDescription = stringResource(R.string.switch_listener_press_switch),
            modifier = Modifier.size(160.dp),
            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface)
        )
        Spacer(modifier = Modifier.padding(16.dp))
        Text(
            text = stringResource(R.string.switch_listener_press_switch),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.padding(8.dp))
        Text(
            text = stringResource(R.string.switch_listener_is_switch_not_working),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.padding(16.dp))
        ActionButton(
            textResId = R.string.button_cancel,
            type = ActionButtonType.SECONDARY,
            onClick = {
                navController.popBackStack()
            }
        )
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
    var localName by remember { mutableStateOf(name) }
    
    LaunchedEffect(name) {
        localName = name
    }

    TextArea(
        value = localName,
        onValueChange = {
            localName = it
            onNameChange(it)
        },
        labelResId = R.string.label_switch_name,
        isError = localName.isBlank(),
        supportingTextResId = R.string.error_switch_name_required
    )
}

@Composable
fun SwitchActionSection(viewModel: AddEditExternalSwitchScreenModel) {
    val allowLongPress = viewModel.allowLongPress.observeAsState()
    val longPressActions = viewModel.longPressActions.observeAsState()
    val refreshingLongPressActions = viewModel.refreshingLongPressActions.observeAsState()
    val context = LocalContext.current
    SwitchActionPicker(
        titleResId = R.string.section_title_press_action,
        switchAction = viewModel.pressAction.value!!,
        onChange = {
            viewModel.setPressAction(it, context)
        }
    )

    Spacer(modifier = Modifier.padding(16.dp))

    if (allowLongPress.value!! && !refreshingLongPressActions.value!!) {
        Text(
            text = stringResource(R.string.switch_listener_each_switch_can_have_multiple_actions_for_long_press),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp)
        )

        Spacer(modifier = Modifier.padding(8.dp))

        longPressActions.value?.forEachIndexed { index, action ->
            SwitchActionPicker(
                titleResId = R.string.section_title_long_press_action,
                titleResIdArgs = arrayOf(index + 1),
                switchAction = action,
                onChange = { newAction ->
                    viewModel.updateLongPressAction(action, newAction)
                },
                onDelete = {
                    viewModel.removeLongPressAction(index)
                }
            )
            Spacer(modifier = Modifier.padding(8.dp))
        }
        ActionButton(
            textResId = R.string.button_add_long_press_action,
            onClick = {
                viewModel.addLongPressAction(SwitchAction(SwitchAction.ACTION_SELECT))
            }
        )
    }
}
