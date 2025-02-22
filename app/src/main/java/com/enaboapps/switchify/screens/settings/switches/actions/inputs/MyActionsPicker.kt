package com.enaboapps.switchify.screens.settings.switches.actions.inputs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.enaboapps.switchify.R
import com.enaboapps.switchify.components.Picker
import com.enaboapps.switchify.service.custom.actions.store.Action
import com.enaboapps.switchify.service.custom.actions.store.ActionStore
import com.enaboapps.switchify.switches.SwitchAction
import com.enaboapps.switchify.switches.SwitchActionExtra
import com.enaboapps.switchify.utils.Resources

@Composable
fun MyActionsPicker(
    currentAction: SwitchAction,
    onChange: (SwitchAction) -> Unit
) {
    val context = LocalContext.current
    val actionStore = ActionStore(context)
    val actions = remember { mutableStateListOf<Action>() }
    val selectedAction = remember { mutableStateOf<Action?>(null) }

    val createAction: (String, String) -> SwitchAction = { id, text ->
        SwitchAction(
            id = SwitchAction.ACTION_PERFORM_USER_ACTION,
            extra = SwitchActionExtra(
                myActionsId = id,
                myActionName = text
            )
        )
    }

    LaunchedEffect(Unit) {
        actions.clear()
        actions.addAll(actionStore.getActions())
        currentAction.extra?.myActionsId?.let { id ->
            selectedAction.value = actions.firstOrNull { it.id == id }
        }
    }

    Picker(
        titleResId = R.string.action_perform_user_action,
        selectedItem = selectedAction.value,
        items = actions,
        onItemSelected = { action ->
            selectedAction.value = action
            onChange(createAction(action.id, action.text))
        },
        itemToString = { it.text },
        itemDescription = { Resources.getString(R.string.action_perform_user_action_desc) }
    )
}
