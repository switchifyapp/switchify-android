package com.enaboapps.switchify.screens.settings.switches.actions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.enaboapps.switchify.R
import com.enaboapps.switchify.components.BaseView
import com.enaboapps.switchify.switches.RequiredActionsPolicy
import com.enaboapps.switchify.switches.SupportedActionsPolicy
import com.enaboapps.switchify.switches.SwitchAction
import com.enaboapps.switchify.switches.SwitchEventStore
import com.enaboapps.switchify.theme.Dimens

/**
 * Navigation result key for the selected action ID.
 */
const val SELECTED_ACTION_ID_KEY = "selected_action_id"

/**
 * Full-screen for selecting a switch action.
 *
 * Displays a list of available actions based on the current scan mode,
 * highlighting the currently selected action and showing recommended
 * missing actions as chips.
 *
 * @param navController Navigation controller for returning the result.
 * @param currentActionId The ID of the currently selected action.
 */
@Composable
fun SwitchActionSelectionScreen(
    navController: NavController,
    currentActionId: Int
) {
    val context = LocalContext.current
    val availableActions = remember { SupportedActionsPolicy.supportedActions(context) }
    var missingActions by remember { mutableStateOf(listOf<SwitchAction>()) }

    // Compute missing required actions
    LaunchedEffect(currentActionId, availableActions) {
        val required = RequiredActionsPolicy.requiredActionIds(context)
        val configured = SwitchEventStore.getInstance().getSwitchEvents()
            .flatMap { listOf(it.pressAction.id) + it.holdActions.map { a -> a.id } }
            .toSet()
        val current = setOf(currentActionId)
        val allowedIds = availableActions.map { it.id }.toSet()
        val missingIds = required - (configured + current)
        missingActions = missingIds
            .filter { allowedIds.contains(it) }
            .map { SwitchAction(it) }
    }

    fun selectAction(action: SwitchAction) {
        navController.previousBackStackEntry
            ?.savedStateHandle
            ?.set(SELECTED_ACTION_ID_KEY, action.id)
        navController.popBackStack()
    }

    BaseView(
        titleResId = R.string.screen_title_select_action,
        navController = navController,
        enableScroll = false
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.spaceM)
        ) {
            items(availableActions) { action ->
                ActionItem(
                    action = action,
                    isSelected = action.id == currentActionId,
                    onClick = { selectAction(action) }
                )
            }

            if (missingActions.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(Dimens.spaceL))
                    RecommendedActionsSection(
                        missingActions = missingActions,
                        onActionClick = { selectAction(it) }
                    )
                }
            }
        }
    }
}

/**
 * Displays a single action item as a selectable card.
 */
@Composable
private fun ActionItem(
    action: SwitchAction,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .semantics { selected = isSelected }
            .clickable(onClick = onClick),
        color = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimens.spaceM),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = action.getActionName(),
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
                Text(
                    text = action.getActionDescription(),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }

            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = stringResource(R.string.content_desc_selected),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

/**
 * Section displaying recommended missing actions as chips.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecommendedActionsSection(
    missingActions: List<SwitchAction>,
    onActionClick: (SwitchAction) -> Unit
) {
    Column {
        Text(
            text = stringResource(R.string.section_title_recommended),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = Dimens.spaceS)
        )

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Dimens.spaceS)
        ) {
            missingActions.forEach { action ->
                AssistChip(
                    onClick = { onActionClick(action) },
                    label = { Text(action.getActionName()) },
                    colors = AssistChipDefaults.assistChipColors()
                )
            }
        }
    }
}
