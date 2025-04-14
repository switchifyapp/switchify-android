package com.enaboapps.switchify.screens.settings.switches.actions

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.enaboapps.switchify.components.Picker
import com.enaboapps.switchify.switches.SwitchAction

@Composable
fun SwitchActionPicker(
    titleResId: Int,
    titleResIdArgs: Array<Any>? = null,
    switchAction: SwitchAction,
    onChange: (SwitchAction) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    var currentAction by remember { mutableStateOf(switchAction) }

    Column {
        Picker(
            titleResId = titleResId,
            titleResIdArgs = titleResIdArgs,
            selectedItem = currentAction,
            items = SwitchAction.actions,
            onItemSelected = { newAction ->
                currentAction = newAction
                onChange(newAction)
            },
            onDelete = onDelete,
            itemToString = { it.getActionName() },
            itemDescription = { it.getActionDescription() }
        )
    }
}