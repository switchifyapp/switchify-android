package com.enaboapps.switchify.screens.settings.switches

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.enaboapps.switchify.R
import com.enaboapps.switchify.backend.preferences.PreferenceManager
import com.enaboapps.switchify.components.BaseView
import com.enaboapps.switchify.components.LoadingIndicator
import com.enaboapps.switchify.components.ScrollableView
import com.enaboapps.switchify.components.Section
import com.enaboapps.switchify.components.SwitchAction
import com.enaboapps.switchify.components.SwitchListItem
import com.enaboapps.switchify.components.SwitchType
import com.enaboapps.switchify.screens.settings.switches.models.ExternalSwitchesScreenModel
import com.enaboapps.switchify.switches.SwitchEvent
import com.enaboapps.switchify.switches.SwitchHoldPolicy

@Composable
fun ExternalSwitchesScreen(navController: NavController, profileId: String? = null) {
    val context = LocalContext.current
    val profileContext = rememberSwitchProfileContext(profileId)
    val targetProfileId = profileContext.targetProfileId
    HandleMissingSwitchProfile(profileContext, navController)
    val switchHoldEnabled = remember {
        SwitchHoldPolicy.isEnabled(PreferenceManager(context))
    }
    val externalSwitchesScreenModel = remember {
        ExternalSwitchesScreenModel()
    }
    val uiState by externalSwitchesScreenModel.uiState.collectAsState()

    LaunchedEffect(targetProfileId) {
        targetProfileId?.let { externalSwitchesScreenModel.setup(context, it) }
    }

    BaseView(
        titleResId = R.string.screen_title_external_switches,
        navController = navController,
        navBarTrailingContent = {
            SwitchProfileIndicator(profileContext, navController)
        },
        padding = 0.dp,
        enableScroll = false,
        floatingActionButton = {
            targetProfileId?.let { resolvedProfileId ->
                FloatingActionButton(
                    onClick = {
                        navController.navigate(
                            SwitchProfileRoutes.addExternalSwitch(resolvedProfileId)
                        )
                    }
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_baseline_add_24),
                        contentDescription = "Add"
                    )
                }
            }
        }
    ) {
        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    LoadingIndicator()
                }
            }

            targetProfileId != null -> {
                ExternalSwitchesContent(
                    externalSwitches = uiState.externalSwitches,
                    navController = navController,
                    profileId = targetProfileId,
                    switchHoldEnabled = switchHoldEnabled
                )
            }
        }

    }
}

@Composable
private fun ExternalSwitchesContent(
    externalSwitches: List<SwitchEvent>,
    navController: NavController,
    profileId: String,
    switchHoldEnabled: Boolean
) {
    if (externalSwitches.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No external switches found",
                style = MaterialTheme.typography.titleMedium
            )
        }
    } else {
        ScrollableView {
            Section(titleResId = R.string.section_title_switches) {
                externalSwitches.forEach { event ->
                    SwitchEventItem(
                        navController = navController,
                        switchEvent = event,
                        profileId = profileId,
                        switchHoldEnabled = switchHoldEnabled
                    )
                }
            }
        }
    }
}

@Composable
private fun SwitchEventItem(
    navController: NavController,
    switchEvent: SwitchEvent,
    profileId: String,
    switchHoldEnabled: Boolean
) {
    val primaryAction = SwitchAction(
        trigger = "Press",
        actionName = switchEvent.pressAction.getDisplayName(LocalContext.current)
    )

    val secondaryActions = SwitchHoldPolicy.effectiveHoldActions(
        switchEvent,
        switchHoldEnabled
    ).map { holdAction ->
        SwitchAction(
            trigger = "Hold",
            actionName = holdAction.getDisplayName(LocalContext.current)
        )
    }

    SwitchListItem(
        switchName = switchEvent.name,
        switchType = SwitchType.EXTERNAL,
        primaryAction = primaryAction,
        secondaryActions = secondaryActions,
        isEnabled = true,
        hasConfigurationIssues = false,
        onClick = {
            navController.navigate(
                SwitchProfileRoutes.editExternalSwitch(profileId, switchEvent.code)
            )
        }
    )
}
