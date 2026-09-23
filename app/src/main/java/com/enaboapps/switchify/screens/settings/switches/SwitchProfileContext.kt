package com.enaboapps.switchify.screens.settings.switches

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.enaboapps.switchify.R
import com.enaboapps.switchify.nav.NavigationRoute
import com.enaboapps.switchify.switches.profiles.SwitchProfileDocument
import com.enaboapps.switchify.switches.profiles.SwitchProfileRepository

internal data class SwitchProfileContextState(
    val targetProfileId: String? = null,
    val profileName: String? = null,
    val isActive: Boolean = false,
    val isLoading: Boolean = true,
    val isMissing: Boolean = false
)

internal object SwitchProfileContextPolicy {
    fun resolve(
        document: SwitchProfileDocument,
        targetProfileId: String?,
        initialized: Boolean
    ): SwitchProfileContextState {
        if (!initialized || targetProfileId == null) return SwitchProfileContextState()
        val profile = document.profiles.firstOrNull { it.id == targetProfileId }
            ?: return SwitchProfileContextState(
                targetProfileId = targetProfileId,
                isLoading = false,
                isMissing = true
            )
        return SwitchProfileContextState(
            targetProfileId = targetProfileId,
            profileName = profile.name,
            isActive = targetProfileId == document.activeProfileId,
            isLoading = false
        )
    }
}

internal object SwitchProfileRoutes {
    fun externalSwitches(profileId: String) =
        "${NavigationRoute.ExternalSwitches.name}/$profileId"

    fun cameraSwitches(profileId: String) =
        "${NavigationRoute.CameraSwitches.name}/$profileId"

    fun addExternalSwitch(profileId: String) =
        "${NavigationRoute.AddNewExternalSwitch.name}/$profileId"

    fun editExternalSwitch(profileId: String, code: String) =
        "${NavigationRoute.EditExternalSwitch.name}/$profileId/$code"

    fun addCameraSwitch(profileId: String) =
        "${NavigationRoute.AddNewCameraSwitch.name}/$profileId"

    fun editCameraSwitch(profileId: String, code: String) =
        "${NavigationRoute.EditCameraSwitch.name}/$profileId/$code"

    fun longPressActions(profileId: String, code: String) =
        "${NavigationRoute.LongPressActions.name}/$profileId/$code"

    fun actionSelection(profileId: String, actionId: Int) =
        "${NavigationRoute.SwitchActionSelection.name}/$profileId/$actionId"
}

@Composable
internal fun rememberSwitchProfileContext(
    requestedProfileId: String?
): SwitchProfileContextState {
    val context = LocalContext.current
    val repository = remember { SwitchProfileRepository.getInstance(context) }
    val document by repository.document.collectAsState()
    var initialized by remember(requestedProfileId) { mutableStateOf(false) }
    var targetProfileId by rememberSaveable(requestedProfileId) {
        mutableStateOf(requestedProfileId)
    }

    LaunchedEffect(requestedProfileId) {
        repository.initialize()
        if (targetProfileId == null) {
            targetProfileId = repository.document.value.activeProfileId
        }
        initialized = true
    }

    return SwitchProfileContextPolicy.resolve(document, targetProfileId, initialized)
}

@Composable
internal fun SwitchProfileIndicator(
    profileContext: SwitchProfileContextState,
    navController: NavController,
    confirmBeforeLeaving: Boolean = false
) {
    val profileName = profileContext.profileName ?: return
    var showDiscardDialog by remember { mutableStateOf(false) }
    val status = stringResource(
        if (profileContext.isActive) {
            R.string.switch_profile_status_active
        } else {
            R.string.switch_profile_status_inactive
        }
    )
    val description = stringResource(
        R.string.switch_profile_indicator_description,
        profileName,
        status
    )

    fun openProfiles(discardCurrentScreen: Boolean = false) {
        if (discardCurrentScreen) navController.popBackStack()
        navController.navigate(NavigationRoute.SwitchProfiles.name) {
            launchSingleTop = true
        }
    }

    Surface(
        modifier = Modifier
            .widthIn(max = 132.dp)
            .padding(end = 4.dp)
            .semantics {
                contentDescription = description
                role = androidx.compose.ui.semantics.Role.Button
            }
            .clickable {
                if (confirmBeforeLeaving) showDiscardDialog = true else openProfiles()
            },
        shape = CircleShape,
        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.14f),
        contentColor = MaterialTheme.colorScheme.onPrimary
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .then(
                        if (profileContext.isActive) {
                            Modifier.background(
                                MaterialTheme.colorScheme.onPrimary,
                                CircleShape
                            )
                        } else {
                            Modifier.border(
                                1.5.dp,
                                MaterialTheme.colorScheme.onPrimary,
                                CircleShape
                            )
                        }
                    )
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = profileName,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text(stringResource(R.string.switch_profile_discard_title)) },
            text = { Text(stringResource(R.string.switch_profile_discard_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showDiscardDialog = false
                    openProfiles(discardCurrentScreen = true)
                }) {
                    Text(stringResource(R.string.switch_profile_discard_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) {
                    Text(stringResource(R.string.button_cancel))
                }
            }
        )
    }
}

@Composable
internal fun HandleMissingSwitchProfile(
    profileContext: SwitchProfileContextState,
    navController: NavController
) {
    LaunchedEffect(profileContext.isMissing) {
        if (profileContext.isMissing) {
            navController.popBackStack()
            navController.navigate(NavigationRoute.SwitchProfiles.name) {
                launchSingleTop = true
            }
        }
    }
}
