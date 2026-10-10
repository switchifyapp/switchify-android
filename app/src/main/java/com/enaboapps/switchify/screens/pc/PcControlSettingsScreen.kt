package com.enaboapps.switchify.screens.pc

import android.content.ActivityNotFoundException
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.enaboapps.switchify.R
import com.enaboapps.switchify.components.ActionButton
import com.enaboapps.switchify.components.ActionButtonType
import com.enaboapps.switchify.components.BaseView
import com.enaboapps.switchify.components.Panel
import com.enaboapps.switchify.components.PanelListRow
import com.enaboapps.switchify.components.PreferenceValueSelector
import com.enaboapps.switchify.components.SwitchifyTextField
import com.enaboapps.switchify.nav.NavigationRoute
import com.enaboapps.switchify.pc.connection.PcConnectionState
import com.enaboapps.switchify.pc.connection.PcRemoteName
import com.enaboapps.switchify.pc.connection.PcRemoteNameError
import com.enaboapps.switchify.pc.control.asControlConnection
import com.enaboapps.switchify.pc.forwarding.PcForwardingPreferences
import com.enaboapps.switchify.pc.forwarding.SharedPreferencesPcForwardingPreferenceStore
import com.enaboapps.switchify.pc.protocol.PcPointerProfile
import com.enaboapps.switchify.pc.remote.PcRemoteGraph
import com.enaboapps.switchify.pc.remote.PcTypingMode
import com.enaboapps.switchify.pc.storage.PcSavedPc
import com.enaboapps.switchify.theme.Dimens
import kotlin.math.roundToInt

@Composable
fun PcControlSettingsScreen(navController: NavController, tabBar: @Composable () -> Unit, onBackPressed: () -> Unit) {
    val context = LocalContext.current
    val graph = remember { PcRemoteGraph.getInstance(context) }
    val viewModel: PcControlSettingsViewModel = viewModel {
        PcControlSettingsViewModel(
            connection = graph.connection.manager.asControlConnection(),
            preferences = graph.preferences,
            remoteNames = graph.connection.remoteNames,
            forwarding = SharedPreferencesPcForwardingPreferenceStore(context)
        )
    }
    val connection by viewModel.state.collectAsStateWithLifecycle()
    val surface by viewModel.preferences.surface.collectAsStateWithLifecycle()
    val typingMode by viewModel.preferences.typingMode.collectAsStateWithLifecycle()
    val saved by viewModel.saved.collectAsStateWithLifecycle()
    val defaultDesktopId by viewModel.defaultDesktopId.collectAsStateWithLifecycle()
    val holdToStopMs by viewModel.holdToStopMs.collectAsStateWithLifecycle()
    val remoteName by viewModel.remoteName.collectAsStateWithLifecycle()
    val remoteNameSaving by viewModel.remoteNameSaving.collectAsStateWithLifecycle()
    val remoteNameStatus by viewModel.remoteNameStatus.collectAsStateWithLifecycle()
    val linkErrorMessage = stringResource(R.string.error_no_app_to_open_link)

    LaunchedEffect(viewModel) { viewModel.refresh() }

    BaseView(
        titleResId = R.string.pc_control_tab_settings,
        onBackPressed = onBackPressed,
        navController = navController,
        bottomBar = tabBar
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Dimens.spaceM)
        ) {
            Text(
                text = stringResource(R.string.pc_settings_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            SettingCard(R.string.pc_settings_surface_title, R.string.pc_settings_surface_summary) {
                PcSurfaceRadioGroup(selected = surface, onSelect = viewModel::setSurface)
            }

            SettingCard(R.string.pc_settings_remote_name_title, R.string.pc_settings_remote_name_summary) {
                RemoteNameEditor(
                    savedName = remoteName,
                    automaticName = viewModel.automaticRemoteName,
                    saving = remoteNameSaving,
                    status = remoteNameStatus,
                    onEdit = viewModel::clearRemoteNameStatus,
                    onSave = viewModel::saveRemoteName
                )
            }

            SettingCard(R.string.pc_settings_typing_title, R.string.pc_settings_typing_summary) {
                ChoiceGroup(
                    options = PcTypingMode.entries.map { mode ->
                        Choice(
                            label = stringResource(if (mode == PcTypingMode.Live) R.string.pc_typing_live else R.string.pc_typing_draft),
                            selected = mode == typingMode,
                            onSelect = { viewModel.setTypingMode(mode) }
                        )
                    }
                )
            }

            SettingCard(R.string.pc_settings_default_title, R.string.pc_settings_default_summary) {
                DefaultPcChoices(saved, defaultDesktopId, viewModel::setDefault)
            }

            SettingCard(R.string.pc_settings_pointer_title, R.string.pc_settings_pointer_summary) {
                PointerControls((connection as? PcConnectionState.Connected)?.profile)
            }

            SettingCard(R.string.pc_settings_forwarding_title, R.string.pc_settings_forwarding_summary) {
                val labels = PcForwardingPreferences.HOLD_TO_STOP_OPTIONS_MS.associate { milliseconds ->
                    val seconds = (milliseconds / 1000).toInt()
                    milliseconds.toInt() to pluralStringResource(R.plurals.pc_forwarding_seconds, seconds, seconds)
                }
                Panel(modifier = Modifier.fillMaxWidth()) {
                    PreferenceValueSelector(
                        value = holdToStopMs.toInt(),
                        titleResId = R.string.pc_forwarding_hold_to_stop_title,
                        summaryResId = R.string.pc_forwarding_hold_to_stop_summary,
                        values = PcForwardingPreferences.HOLD_TO_STOP_OPTIONS_MS.map { it.toInt() }.toIntArray(),
                        buttonLabelFormatter = { labels.getValue(it) },
                        displayFormatter = { labels.getValue(it) },
                        onValueChanged = { viewModel.setHoldToStopMs(it.toLong()) }
                    )
                }
            }

            Panel(modifier = Modifier.fillMaxWidth()) {
                PanelListRow(
                    titleResId = R.string.screen_title_pc_diagnostics,
                    summaryResId = R.string.pc_diagnostics_summary,
                    leadingIcon = Icons.Rounded.History,
                    onClick = { navController.navigate(NavigationRoute.PcDiagnostics.name) }
                )
                PanelListRow(
                    titleResId = R.string.button_privacy_policy,
                    summaryResId = R.string.pc_settings_privacy_summary,
                    leadingIcon = Icons.Rounded.PrivacyTip,
                    onClick = {
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, PRIVACY_POLICY_URL.toUri()))
                        } catch (_: ActivityNotFoundException) {
                            Toast.makeText(context, linkErrorMessage, Toast.LENGTH_LONG).show()
                        }
                    },
                    trailing = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun SettingCard(titleRes: Int, summaryRes: Int, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.spaceXs)) {
        Text(
            text = stringResource(titleRes),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { heading() }
        )
        Text(
            text = stringResource(summaryRes),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        content()
    }
}

private data class Choice(val label: String, val selected: Boolean, val onSelect: () -> Unit)

@Composable
private fun ChoiceGroup(options: List<Choice>) {
    Panel(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.selectableGroup()) {
            options.forEach { option ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(selected = option.selected, role = Role.RadioButton, onClick = option.onSelect)
                        .padding(horizontal = Dimens.spaceM, vertical = Dimens.spaceS),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.spaceS),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = option.selected, onClick = null)
                    Text(text = option.label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun DefaultPcChoices(saved: List<PcSavedPc>, defaultDesktopId: String?, onSelect: (String?) -> Unit) {
    if (saved.isEmpty()) {
        InfoText(R.string.pc_settings_no_saved_title, R.string.pc_settings_no_saved_body)
        return
    }
    val selectedId = defaultDesktopId?.takeIf { id -> saved.any { it.desktopId == id } }
    ChoiceGroup(
        options = listOf(
            Choice(
                label = stringResource(R.string.pc_settings_default_recent),
                selected = selectedId == null,
                onSelect = { onSelect(null) }
            )
        ) + saved.map { pc ->
            Choice(label = pc.displayName, selected = pc.desktopId == selectedId, onSelect = { onSelect(pc.desktopId) })
        }
    )
}

@Composable
private fun PointerControls(profile: PcPointerProfile?) {
    if (profile == null) {
        InfoText(R.string.pc_settings_pointer_connect_title, R.string.pc_settings_pointer_connect_body)
        return
    }
    val capabilities = profile.capabilities
    Panel(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(Dimens.spaceM), verticalArrangement = Arrangement.spacedBy(Dimens.spaceS)) {
            ReadOnlyRow(
                R.string.pc_settings_pointer_speed,
                stringResource(R.string.pc_settings_percent, capabilities.pointerSpeed.scalePercent.roundToInt())
            )
            ReadOnlyRow(
                R.string.pc_settings_movement_repeat,
                stringResource(if (capabilities.mouseRepeat.enabled) R.string.pc_settings_on else R.string.pc_settings_off)
            )
            ReadOnlyRow(
                R.string.pc_settings_displays,
                capabilities.displayNavigation.displayCount.roundToInt().toString()
            )
        }
    }
}

@Composable
private fun ReadOnlyRow(titleRes: Int, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = stringResource(titleRes), style = MaterialTheme.typography.bodyLarge)
        Text(text = value, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun InfoText(titleRes: Int, bodyRes: Int) {
    Panel(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(Dimens.spaceM).semantics(mergeDescendants = true) {}) {
            Text(text = stringResource(titleRes), style = MaterialTheme.typography.titleMedium)
            Text(
                text = stringResource(bodyRes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun RemoteNameEditor(
    savedName: String?,
    automaticName: String,
    saving: Boolean,
    status: PcRemoteNameStatus?,
    onEdit: () -> Unit,
    onSave: (String?) -> Unit
) {
    var draft by rememberSaveable(savedName) { mutableStateOf(savedName ?: automaticName) }
    val error = if (draft.isNotEmpty()) PcRemoteName.error(draft) else null
    val valid = PcRemoteName.validate(draft)
    val unchanged = valid != null && valid == (savedName ?: automaticName)
    val label = stringResource(R.string.pc_settings_remote_name_title)
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.spaceS)) {
        SwitchifyTextField(
            value = draft,
            onValueChange = {
                draft = it.take(MAX_INPUT_CHARACTERS)
                onEdit()
            },
            enabled = !saving,
            label = { Text(label) },
            isError = error != null,
            supportingText = error?.let { { Text(stringResource(remoteNameErrorText(it))) } },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (valid != null && !unchanged && !saving) onSave(valid) }),
            modifier = Modifier.fillMaxWidth()
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Dimens.spaceXs),
            verticalArrangement = Arrangement.spacedBy(Dimens.spaceXs)
        ) {
            ActionButton(
                textResId = R.string.pc_settings_remote_name_save,
                enabled = !saving && valid != null && !unchanged,
                applyPadding = false,
                onClick = { valid?.let(onSave) }
            )
            ActionButton(
                textResId = R.string.pc_settings_remote_name_use_model,
                type = ActionButtonType.SECONDARY,
                enabled = !saving && !(savedName == null && draft == automaticName),
                applyPadding = false,
                onClick = {
                    draft = automaticName
                    onSave(null)
                }
            )
        }
        Text(
            text = stringResource(R.string.pc_settings_remote_name_model, automaticName),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        status?.let {
            Text(
                text = stringResource(remoteNameStatusText(it)),
                style = MaterialTheme.typography.bodySmall,
                color = if (it == PcRemoteNameStatus.SaveFailed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
            )
        }
    }
}

private const val MAX_INPUT_CHARACTERS = 80
private const val PRIVACY_POLICY_URL = "https://www.switchifyapp.com/privacy"

private fun remoteNameErrorText(error: PcRemoteNameError): Int = when (error) {
    PcRemoteNameError.Empty -> R.string.pc_settings_remote_name_error_empty
    PcRemoteNameError.ControlCharacters -> R.string.pc_settings_remote_name_error_control
    PcRemoteNameError.TooLong -> R.string.pc_settings_remote_name_error_long
}

private fun remoteNameStatusText(status: PcRemoteNameStatus): Int = when (status) {
    PcRemoteNameStatus.Synced -> R.string.pc_settings_remote_name_synced
    PcRemoteNameStatus.Deferred -> R.string.pc_settings_remote_name_deferred
    PcRemoteNameStatus.SyncFailed -> R.string.pc_settings_remote_name_sync_failed
    PcRemoteNameStatus.SaveFailed -> R.string.pc_settings_remote_name_save_failed
}
