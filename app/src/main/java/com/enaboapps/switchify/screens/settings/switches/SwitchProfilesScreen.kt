package com.enaboapps.switchify.screens.settings.switches

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.enaboapps.switchify.R
import com.enaboapps.switchify.components.BaseView
import com.enaboapps.switchify.components.InfoCard
import com.enaboapps.switchify.components.NavRouteLink
import com.enaboapps.switchify.components.Panel
import com.enaboapps.switchify.components.Section
import com.enaboapps.switchify.nav.NavigationRoute
import com.enaboapps.switchify.service.core.ServiceBridge
import com.enaboapps.switchify.service.utils.ServiceUtils
import com.enaboapps.switchify.switches.SWITCH_EVENT_TYPE_CAMERA
import com.enaboapps.switchify.switches.SWITCH_EVENT_TYPE_EXTERNAL
import com.enaboapps.switchify.switches.profiles.SwitchProfile
import com.enaboapps.switchify.switches.profiles.SwitchProfileMutationResult
import com.enaboapps.switchify.switches.profiles.SwitchProfileRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SwitchProfilesScreen(navController: NavController) {
    val context = LocalContext.current
    val repository = remember { SwitchProfileRepository.getInstance(context) }
    val document by repository.document.collectAsState()
    val scope = rememberCoroutineScope()
    var dialog by remember { mutableStateOf<ProfileDialog?>(null) }
    var deleteTarget by remember { mutableStateOf<SwitchProfile?>(null) }
    var verification by remember { mutableStateOf<VerificationUi?>(null) }

    LaunchedEffect(Unit) {
        repository.initialize()
        ServiceBridge.serviceEvents.collect { event ->
            when (event) {
                is ServiceBridge.ServiceEvent.SwitchProfileVerificationStarted -> {
                    if (!event.usesConfirmationMenu) {
                        verification = VerificationUi(
                            event.profileName,
                            event.expiresAtMillis
                        )
                    }
                }
                is ServiceBridge.ServiceEvent.SwitchProfileActivated,
                is ServiceBridge.ServiceEvent.SwitchProfileActivationFailed,
                ServiceBridge.ServiceEvent.SwitchProfileActivationCancelled -> verification = null
                else -> Unit
            }
        }
    }

    BaseView(
        titleResId = R.string.screen_title_switch_profiles,
        navController = navController,
        padding = 0.dp,
        enableScroll = false,
        floatingActionButton = {
            FloatingActionButton(onClick = { dialog = ProfileDialog.Create }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.switch_profile_create))
            }
        }
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            InfoCard(
                titleResId = R.string.switch_profiles_guide_title,
                descriptionResId = R.string.switch_profiles_guide_description
            )
            document.profiles.forEach { profile ->
                key(profile.id) {
                    val active = profile.id == document.activeProfileId
                    ProfileRow(
                        profile = profile,
                        active = active,
                        onEdit = {
                            navController.navigate(
                                "${NavigationRoute.SwitchProfileDetail.name}/${profile.id}"
                            )
                        },
                        onActivate = {
                            if (ServiceUtils().isAccessibilityServiceEnabled(context)) {
                                ServiceBridge.sendCommand(
                                    ServiceBridge.ServiceCommand.BeginSwitchProfileActivation(profile.id)
                                )
                            } else {
                                Toast.makeText(
                                    context,
                                    R.string.switch_profile_service_required,
                                    Toast.LENGTH_LONG
                                ).show()
                                navController.navigate(NavigationRoute.EnableAccessibilityService.name)
                            }
                        },
                        onDuplicate = { dialog = ProfileDialog.Duplicate(profile) },
                        onRename = { dialog = ProfileDialog.Rename(profile) },
                        onDelete = { deleteTarget = profile }
                    )
                }
            }
            Spacer(Modifier.height(72.dp))
        }
    }

    dialog?.let { current ->
        ProfileNameDialog(
            dialog = current,
            onDismiss = { dialog = null },
            onSave = { name ->
                scope.launch {
                    val result = when (current) {
                        ProfileDialog.Create -> repository.createEmpty(name)
                        is ProfileDialog.Duplicate -> repository.duplicate(current.profile.id, name)
                        is ProfileDialog.Rename -> repository.rename(current.profile.id, name)
                    }
                    when (result) {
                        is SwitchProfileMutationResult.Success -> dialog = null
                        is SwitchProfileMutationResult.InvalidName -> {
                            val message = if (result.reason == "duplicate") {
                                R.string.switch_profile_name_duplicate
                            } else R.string.switch_profile_name_required
                            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                        }
                        else -> Toast.makeText(
                            context,
                            R.string.switch_profile_activation_failed,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        )
    }

    deleteTarget?.let { profile ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.switch_profile_delete_title)) },
            text = { Text(stringResource(R.string.switch_profile_delete_message)) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        repository.delete(profile.id)
                        deleteTarget = null
                    }
                }) { Text(stringResource(R.string.button_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text(stringResource(R.string.button_cancel))
                }
            }
        )
    }

    verification?.let { current ->
        VerificationDialog(
            verification = current,
            onCancel = {
                ServiceBridge.sendCommand(ServiceBridge.ServiceCommand.CancelSwitchProfileActivation)
            }
        )
    }
}

@Composable
fun SwitchProfileDetailScreen(navController: NavController, profileId: String) {
    val context = LocalContext.current
    val repository = remember { SwitchProfileRepository.getInstance(context) }
    val document by repository.document.collectAsState()
    val profile = document.profiles.firstOrNull { it.id == profileId }
    val profileContext = rememberSwitchProfileContext(profileId)

    LaunchedEffect(Unit) { repository.initialize() }
    HandleMissingSwitchProfile(profileContext, navController)

    BaseView(
        titleResId = R.string.screen_title_switch_profiles,
        navController = navController,
        navBarTrailingContent = {
            SwitchProfileIndicator(profileContext, navController)
        }
    ) {
        profile?.let {
            Text(
                text = it.name,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(16.dp)
            )
        }
        Section(titleResId = R.string.section_title_switches) {
            NavRouteLink(
                titleResId = R.string.screen_title_external_switches,
                summaryResId = R.string.external_switches_summary,
                navController = navController,
                route = SwitchProfileRoutes.externalSwitches(profileId)
            )
            NavRouteLink(
                titleResId = R.string.screen_title_camera_switches,
                summaryResId = R.string.camera_switches_summary,
                navController = navController,
                route = SwitchProfileRoutes.cameraSwitches(profileId)
            )
        }
    }
}

@Composable
private fun ProfileRow(
    profile: SwitchProfile,
    active: Boolean,
    onEdit: () -> Unit,
    onActivate: () -> Unit,
    onDuplicate: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    val externalCount = profile.switches.count { it.type == SWITCH_EVENT_TYPE_EXTERNAL }
    val cameraCount = profile.switches.count { it.type == SWITCH_EVENT_TYPE_CAMERA }
    var menuExpanded by remember { mutableStateOf(false) }

    fun selectAction(action: () -> Unit) {
        menuExpanded = false
        action()
    }

    Panel(modifier = Modifier.fillMaxWidth(), onClick = onEdit) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = profile.name,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (active) {
                    Text(
                        text = stringResource(R.string.switch_profile_active),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = stringResource(
                                R.string.switch_profile_more_options,
                                profile.name
                            )
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.switch_profile_edit)) },
                            onClick = { selectAction(onEdit) }
                        )
                        if (!active) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.switch_profile_activate)) },
                                onClick = { selectAction(onActivate) }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.switch_profile_duplicate)) },
                            onClick = { selectAction(onDuplicate) }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.switch_profile_rename)) },
                            onClick = { selectAction(onRename) }
                        )
                        if (!active) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = stringResource(R.string.button_delete),
                                        color = MaterialTheme.colorScheme.error
                                    )
                                },
                                onClick = { selectAction(onDelete) }
                            )
                        }
                    }
                }
            }
            Text(stringResource(R.string.switch_profile_external_count, externalCount))
            Text(stringResource(R.string.switch_profile_camera_count, cameraCount))
        }
    }
}

@Composable
private fun ProfileNameDialog(
    dialog: ProfileDialog,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var name by remember(dialog) {
        mutableStateOf(
            when (dialog) {
                ProfileDialog.Create -> ""
                is ProfileDialog.Duplicate -> "${dialog.profile.name} copy"
                is ProfileDialog.Rename -> dialog.profile.name
            }
        )
    }
    val title = when (dialog) {
        ProfileDialog.Create -> R.string.switch_profile_create
        is ProfileDialog.Duplicate -> R.string.switch_profile_duplicate_title
        is ProfileDialog.Rename -> R.string.switch_profile_rename_title
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.switch_profile_name)) },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(name) }, enabled = name.isNotBlank()) {
                Text(stringResource(R.string.button_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.button_cancel)) }
        }
    )
}

@Composable
private fun VerificationDialog(
    verification: VerificationUi,
    onCancel: () -> Unit
) {
    var remaining by remember(verification) { mutableLongStateOf(60L) }
    LaunchedEffect(verification) {
        while (remaining > 0L) {
            remaining = ((verification.expiresAtMillis - System.currentTimeMillis()) / 1000L)
                .coerceAtLeast(0L)
            delay(250L)
        }
    }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.switch_profile_testing_title, verification.name)) },
        text = {
            Text(
                pluralStringResource(
                    R.plurals.switch_profile_testing_message,
                    remaining.toInt(),
                    remaining
                )
            )
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onCancel) { Text(stringResource(R.string.button_cancel)) }
        }
    )
}

private sealed interface ProfileDialog {
    data object Create : ProfileDialog
    data class Duplicate(val profile: SwitchProfile) : ProfileDialog
    data class Rename(val profile: SwitchProfile) : ProfileDialog
}

private data class VerificationUi(val name: String, val expiresAtMillis: Long)
