package com.enaboapps.switchify.screens.settings.switches

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.enaboapps.switchify.R
import com.enaboapps.switchify.components.ActionButton
import com.enaboapps.switchify.components.BaseView
import com.enaboapps.switchify.components.ReorderMode
import com.enaboapps.switchify.components.ReorderableList
import com.enaboapps.switchify.screens.settings.switches.actions.SwitchActionField
import com.enaboapps.switchify.switches.SwitchAction
import com.enaboapps.switchify.switches.SwitchEventStore

/**
 * A dedicated screen for managing long press actions for an external switch.
 * Changes are saved directly to the store immediately.
 *
 * @param navController Navigation controller for navigation.
 * @param code The switch code to load/save long press actions for.
 */
@Composable
fun LongPressActionsScreen(navController: NavController, code: String) {
    val context = LocalContext.current
    val store = remember { SwitchEventStore.getInstance() }

    // Load actions from store - refresh key triggers reload
    var refreshKey by remember { mutableStateOf(0) }
    val actions = remember(refreshKey) {
        store.find(code)?.holdActions ?: emptyList()
    }

    // Save helper that updates store and refreshes UI
    fun saveAndRefresh(newActions: List<SwitchAction>) {
        val event = store.find(code) ?: return
        val updatedEvent = event.copy(holdActions = newActions)
        store.update(updatedEvent, context) { success ->
            if (success) {
                refreshKey++
            }
        }
    }

    BaseView(
        titleResId = R.string.screen_title_long_press_actions,
        navController = navController,
        enableScroll = false
    ) {
        if (actions.isEmpty()) {
            // Empty state
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(R.string.long_press_actions_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.padding(16.dp))
                    ActionButton(
                        textResId = R.string.button_add_long_press_action,
                        onClick = {
                            saveAndRefresh(actions + SwitchAction(SwitchAction.ACTION_SELECT))
                        }
                    )
                }
            }
        } else {
            ReorderableList(
                items = actions,
                onMove = { from, to ->
                    val mutableList = actions.toMutableList()
                    val item = mutableList.removeAt(from)
                    mutableList.add(to, item)
                    saveAndRefresh(mutableList)
                },
                key = { action -> "${actions.indexOf(action)}-${action.id}" },
                defaultMode = ReorderMode.DRAG
            ) { action, _, reorderControls ->
                val index = actions.indexOf(action)
                Column {
                    SwitchActionField(
                        navController = navController,
                        titleResId = R.string.section_title_long_press_action,
                        titleResIdArgs = arrayOf(index + 1),
                        switchAction = action,
                        onChange = { newAction ->
                            val mutableList = actions.toMutableList()
                            mutableList[index] = newAction
                            saveAndRefresh(mutableList)
                        },
                        onDelete = {
                            val mutableList = actions.toMutableList()
                            mutableList.removeAt(index)
                            saveAndRefresh(mutableList)
                        },
                        reorderControls = reorderControls
                    )
                    Spacer(modifier = Modifier.padding(4.dp))
                }
            }

            // Add button at the bottom
            ActionButton(
                textResId = R.string.button_add_long_press_action,
                onClick = {
                    saveAndRefresh(actions + SwitchAction(SwitchAction.ACTION_SELECT))
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                applyPadding = false
            )
        }
    }
}
