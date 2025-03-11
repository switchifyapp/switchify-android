package com.enaboapps.switchify.screens.settings.actions

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.enaboapps.switchify.R
import com.enaboapps.switchify.components.BaseView
import com.enaboapps.switchify.components.FullWidthButton
import com.enaboapps.switchify.components.LoadingIndicator
import com.enaboapps.switchify.components.Picker
import com.enaboapps.switchify.components.TextArea
import com.enaboapps.switchify.screens.settings.actions.inputs.AppLaunchExtraInput
import com.enaboapps.switchify.screens.settings.actions.inputs.CallNumberExtraInput
import com.enaboapps.switchify.screens.settings.actions.inputs.CopyTextExtraInput
import com.enaboapps.switchify.screens.settings.actions.inputs.OpenLinkExtraInput
import com.enaboapps.switchify.screens.settings.actions.inputs.SendEmailExtraInput
import com.enaboapps.switchify.screens.settings.actions.inputs.SendTextExtraInput
import com.enaboapps.switchify.service.custom.actions.ActionPerformer
import com.enaboapps.switchify.service.custom.actions.store.ActionStore
import com.enaboapps.switchify.service.custom.actions.store.data.ACTION_CALL_NUMBER
import com.enaboapps.switchify.service.custom.actions.store.data.ACTION_COPY_TEXT_TO_CLIPBOARD
import com.enaboapps.switchify.service.custom.actions.store.data.ACTION_OPEN_APP
import com.enaboapps.switchify.service.custom.actions.store.data.ACTION_OPEN_LINK
import com.enaboapps.switchify.service.custom.actions.store.data.ACTION_SEND_EMAIL
import com.enaboapps.switchify.service.custom.actions.store.data.ACTION_SEND_TEXT
import com.enaboapps.switchify.service.custom.actions.store.data.ActionExtra
import com.enaboapps.switchify.service.custom.actions.store.data.getActionDescription
import kotlinx.coroutines.launch

@Composable
fun AddEditActionScreen(navController: NavController, actionId: String? = null) {
    val context = LocalContext.current
    val actionStore = remember { ActionStore(context) }

    val isEditMode = actionId != null
    val screenTitle =
        if (isEditMode) R.string.screen_title_edit_action else R.string.screen_title_add_action

    var availableActions by remember { mutableStateOf(emptyList<String>()) }

    var selectedAction by remember { mutableStateOf("") }
    var selectedExtra by remember { mutableStateOf<ActionExtra?>(null) }
    var actionText by remember { mutableStateOf("") }
    var extraValid by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    // Load existing action data if in edit mode
    LaunchedEffect(actionId) {
        scope.launch {
            loading = true
            availableActions = actionStore.getAvailableActions()
            selectedAction = availableActions.first()
            if (isEditMode) {
                actionStore.getAction(actionId)?.let { action ->
                    actionText = action.text
                    selectedAction = action.action
                    selectedExtra = action.extra
                    println("Loaded action: $action")
                    loading = false
                }
            } else {
                loading = false
            }
        }
    }

    val actionPerformer = remember { ActionPerformer(context) }

    val buttonsEnabled = remember(actionText, selectedAction, selectedExtra, extraValid) {
        actionText.isNotBlank() && selectedAction.isNotBlank() && selectedExtra != null && extraValid
    }

    BaseView(titleResId = screenTitle, navController = navController) {
        if (loading) {
            LoadingIndicator()
        } else {
            ActionTextInput(
                text = actionText,
                onTextChange = { actionText = it }
            )

            Spacer(modifier = Modifier.height(16.dp))

            ActionPicker(
                selectedAction = selectedAction,
                availableActions = availableActions,
                onActionSelected = {
                    selectedAction = it
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            ActionExtraInput(
                selectedAction = selectedAction,
                selectedExtra = selectedExtra,
                onExtraUpdated = { selectedExtra = it },
                onExtraValidated = { extraValid = it }
            )

            Spacer(modifier = Modifier.height(16.dp))

            TestButton(
                isEnabled = buttonsEnabled,
                onTestClicked = {
                    if (selectedAction.isNotBlank() && selectedExtra != null) {
                        actionPerformer.test(selectedAction, selectedExtra)
                    }
                }
            )

            SaveButton(
                isEditMode = isEditMode,
                isSaving = isSaving,
                isEnabled = buttonsEnabled,
                onSaveClicked = {
                    isSaving = true
                    if (isEditMode) {
                        actionStore.updateAction(
                            id = actionId,
                            action = selectedAction,
                            text = actionText,
                            extra = selectedExtra
                        )
                    } else {
                        actionStore.addAction(
                            action = selectedAction,
                            text = actionText,
                            extra = selectedExtra
                        )
                    }
                    navController.popBackStack()
                }
            )
            if (isEditMode) {
                FullWidthButton(
                    textResId = R.string.button_delete,
                    onClick = {
                        showDeleteConfirmation = true
                    }
                )
            }

            if (showDeleteConfirmation) {
                AlertDialog(
                    onDismissRequest = { showDeleteConfirmation = false },
                    title = { Text(stringResource(R.string.action_confirm_deletion)) },
                    text = { Text(stringResource(R.string.action_confirm_deletion_description)) },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showDeleteConfirmation = false
                                actionStore.removeAction(actionId.toString())
                                navController.popBackStack()
                            }
                        ) {
                            Text(stringResource(R.string.action_delete))
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = { showDeleteConfirmation = false }
                        ) {
                            Text(stringResource(R.string.action_cancel))
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun ActionTextInput(
    text: String,
    onTextChange: (String) -> Unit
) {
    TextArea(
        value = text,
        onValueChange = onTextChange,
        labelResId = R.string.action_text,
        imeAction = ImeAction.Next,
        isError = text.isBlank(),
        supportingTextResId = R.string.action_text_required
    )
}

@Composable
private fun ActionPicker(
    selectedAction: String,
    availableActions: List<String>,
    onActionSelected: (String) -> Unit
) {
    Picker(
        titleResId = R.string.action_select_action,
        selectedItem = selectedAction,
        items = availableActions,
        onItemSelected = onActionSelected,
        itemToString = { it },
        itemDescription = { getActionDescription(it) }
    )
}

@Composable
private fun ActionExtraInput(
    selectedAction: String,
    selectedExtra: ActionExtra?,
    onExtraUpdated: (ActionExtra?) -> Unit,
    onExtraValidated: (Boolean) -> Unit
) {
    when (selectedAction) {
        ACTION_OPEN_APP -> AppLaunchExtraInput(
            selectedExtra = selectedExtra,
            onExtraUpdated = onExtraUpdated,
            onExtraValidated = onExtraValidated
        )

        ACTION_COPY_TEXT_TO_CLIPBOARD -> CopyTextExtraInput(
            selectedExtra = selectedExtra,
            onExtraUpdated = onExtraUpdated,
            onExtraValidated = onExtraValidated
        )

        ACTION_CALL_NUMBER -> CallNumberExtraInput(
            selectedExtra = selectedExtra,
            onExtraUpdated = onExtraUpdated,
            onExtraValidated = onExtraValidated
        )

        ACTION_OPEN_LINK -> OpenLinkExtraInput(
            selectedExtra = selectedExtra,
            onExtraUpdated = onExtraUpdated,
            onExtraValidated = onExtraValidated
        )

        ACTION_SEND_TEXT -> SendTextExtraInput(
            selectedExtra = selectedExtra,
            onExtraUpdated = onExtraUpdated,
            onExtraValidated = onExtraValidated
        )

        ACTION_SEND_EMAIL -> SendEmailExtraInput(
            selectedExtra = selectedExtra,
            onExtraUpdated = onExtraUpdated,
            onExtraValidated = onExtraValidated
        )

        else -> {
            // Handle unknown action types or actions without extras
            onExtraUpdated(null)
            onExtraValidated(true)
        }
    }
}

@Composable
private fun SaveButton(
    isEditMode: Boolean,
    isSaving: Boolean,
    isEnabled: Boolean,
    onSaveClicked: () -> Unit
) {
    FullWidthButton(
        textResId = if (isEditMode) R.string.action_update_action else R.string.action_add_action,
        enabled = isEnabled && !isSaving,
        onClick = onSaveClicked
    )
}

@Composable
private fun TestButton(
    isEnabled: Boolean,
    onTestClicked: () -> Unit
) {
    FullWidthButton(
        textResId = R.string.action_test,
        enabled = isEnabled,
        onClick = onTestClicked
    )
}
