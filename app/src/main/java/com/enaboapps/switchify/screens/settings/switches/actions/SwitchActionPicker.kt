package com.enaboapps.switchify.screens.settings.switches.actions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.enaboapps.switchify.components.Picker
import com.enaboapps.switchify.switches.RequiredActionsPolicy
import com.enaboapps.switchify.switches.SwitchAction
import com.enaboapps.switchify.switches.SwitchEventStore
import com.enaboapps.switchify.theme.Dimens

@Composable
fun SwitchActionPicker(
    titleResId: Int,
    titleResIdArgs: Array<Any>? = null,
    switchAction: SwitchAction,
    onChange: (SwitchAction) -> Unit,
    onDelete: (() -> Unit)? = null,
    items: List<SwitchAction> = SwitchAction.actions
) {
    var currentAction by remember { mutableStateOf(switchAction) }
    val context = LocalContext.current
    var computedMissing by remember { mutableStateOf(listOf<SwitchAction>()) }

    val computeMissing: () -> Unit = {
        val required = RequiredActionsPolicy.requiredActionIds(context)
        val configured = SwitchEventStore.getInstance().getSwitchEvents()
            .flatMap { listOf(it.pressAction.id) + it.holdActions.map { a -> a.id } }
            .toSet()
        val current = setOf(currentAction.id)
        val allowedIds = items.map { it.id }.toSet()
        val missingIds = required - (configured + current)
        computedMissing = missingIds
            .filter { allowedIds.contains(it) }
            .map { SwitchAction(it) }
    }

    LaunchedEffect(currentAction, items) {
        computeMissing()
    }

    Column {
        Picker(
            titleResId = titleResId,
            titleResIdArgs = titleResIdArgs,
            selectedItem = currentAction,
            items = items,
            onItemSelected = { newAction ->
                currentAction = newAction
                onChange(newAction)
            },
            onDelete = onDelete,
            itemToString = { it.getActionName() },
            itemDescription = { it.getActionDescription() }
        )

        val pills = computedMissing
        if (pills.isNotEmpty()) {
            Spacer(modifier = Modifier.height(Dimens.spaceS))
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(
                modifier = Modifier.padding(horizontal = Dimens.spaceL),
                horizontalArrangement = Arrangement.spacedBy(Dimens.spaceS)
            ) {
                pills.forEach { a ->
                    AssistChip(
                        onClick = {
                            currentAction = a
                            onChange(a)
                        },
                        label = { Text(a.getActionName()) },
                        colors = AssistChipDefaults.assistChipColors()
                    )
                }
            }
        }
    }
}
