package com.enaboapps.switchify.screens.settings.switches

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.enaboapps.switchify.theme.Dimens
import com.enaboapps.switchify.R
import com.enaboapps.switchify.components.ActionButton
import com.enaboapps.switchify.components.ActionButtonType
import com.enaboapps.switchify.components.BaseView
import com.enaboapps.switchify.components.TextArea
import com.enaboapps.switchify.nav.NavigationRoute
import com.enaboapps.switchify.screens.settings.switches.actions.SwitchActionField
import com.enaboapps.switchify.screens.settings.switches.models.AddEditExternalSwitchScreenModel
import com.enaboapps.switchify.service.core.ServiceBridge
import kotlinx.coroutines.CancellationException
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
        var refresh by remember { mutableStateOf(0) }
        LaunchedEffect(Unit) {
            try {
                ServiceBridge.serviceEvents.collect { event ->
                    if (event is ServiceBridge.ServiceEvent.ConfigurationUpdated) {
                        // Reload long press actions when configuration changes (e.g., from LongPressActionsScreen)
                        if (code != null) {
                            addEditExternalSwitchScreenModel.reloadLongPressActionsFromStore(context)
                        }
                        refresh++
                    }
                }
            } catch (_: CancellationException) {
                // Expected when leaving composition
            }
        }

        BaseView(
            titleResId = screenTitle,
            navController = navController,
            bottomBar = {
                val context = LocalContext.current
                val scope = rememberCoroutineScope()
                androidx.compose.foundation.layout.Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ActionButton(
                        textResId = R.string.button_save,
                        enabled = (shouldSave == true) && (isValid == true),
                        onClick = {
                            addEditExternalSwitchScreenModel.save(context) { success ->
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
                    if (editing) {
                        ActionButton(
                            textResId = R.string.button_delete,
                            type = ActionButtonType.DESTRUCTIVE,
                            onClick = { showDeleteConfirmation.value = true },
                            modifier = Modifier.weight(1f),
                            applyPadding = false
                        )
                    }
                }
            }
        ) {
            key(refresh) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    SwitchName(
                        name = addEditExternalSwitchScreenModel.name,
                        onNameChange = { addEditExternalSwitchScreenModel.updateName(it) }
                    )
                    Spacer(modifier = Modifier.padding(8.dp))
                    SwitchActionSection(navController, addEditExternalSwitchScreenModel, code)
                    Spacer(modifier = Modifier.padding(12.dp))
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
    Column(
        modifier = Modifier
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
fun SwitchActionSection(
    navController: NavController,
    viewModel: AddEditExternalSwitchScreenModel,
    switchCode: String?
) {
    val allowLongPress = viewModel.allowLongPress.observeAsState()
    val longPressActions = viewModel.longPressActions.observeAsState()
    val refreshingLongPressActions = viewModel.refreshingLongPressActions.observeAsState()
    val context = LocalContext.current

    SwitchActionField(
        navController = navController,
        titleResId = R.string.section_title_press_action,
        switchAction = viewModel.pressAction.value!!,
        onChange = {
            viewModel.setPressAction(it, context)
        }
    )

    Spacer(modifier = Modifier.height(16.dp))

    if (allowLongPress.value == true && refreshingLongPressActions.value != true) {
        val actions = longPressActions.value ?: emptyList()
        val actionCount = actions.size

        LongPressActionsCard(
            actionCount = actionCount,
            onClick = {
                if (switchCode != null) {
                    navController.navigate("${NavigationRoute.LongPressActions.name}/$switchCode")
                }
            },
            enabled = switchCode != null
        )

        if (switchCode == null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.long_press_actions_save_first),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
        }
    }
}

@Composable
private fun LongPressActionsCard(
    actionCount: Int,
    onClick: () -> Unit,
    enabled: Boolean
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .then(
                if (enabled) Modifier.clickable(onClick = onClick) else Modifier
            )
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
                    text = stringResource(R.string.section_title_long_press_actions).uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(Dimens.spaceXs))
                Text(
                    text = when (actionCount) {
                        0 -> stringResource(R.string.long_press_actions_none)
                        1 -> stringResource(R.string.long_press_actions_count_one)
                        else -> stringResource(R.string.long_press_actions_count_other, actionCount)
                    },
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(Dimens.spaceXs))
                Text(
                    text = stringResource(R.string.switch_listener_each_switch_can_have_multiple_actions_for_long_press),
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                modifier = Modifier.padding(start = Dimens.spaceM)
            )
        }
    }
}
