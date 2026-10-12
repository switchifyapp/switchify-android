package com.enaboapps.switchify.screens.pc

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Mouse
import androidx.compose.material.icons.rounded.SwitchAccessShortcut
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.WebAsset
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.navigation.NavController
import com.enaboapps.switchify.R
import com.enaboapps.switchify.components.ActionButton
import com.enaboapps.switchify.components.ActionButtonType
import com.enaboapps.switchify.components.BaseView
import com.enaboapps.switchify.components.Panel
import com.enaboapps.switchify.pc.control.PcControlSetupPhase
import com.enaboapps.switchify.pc.remote.PcRemoteSurface
import com.enaboapps.switchify.theme.Dimens

const val SWITCHIFY_PC_RELEASES_URL = "https://github.com/switchifyapp/switchify-pc/releases"

@Composable
fun PcControlSetupScreen(
    navController: NavController,
    phase: PcControlSetupPhase,
    surface: PcRemoteSurface,
    onContinue: () -> Unit,
    onBack: () -> Boolean,
    onChooseSurface: (PcRemoteSurface) -> Unit,
    onFinish: (searchForPcs: Boolean) -> Unit
) {
    val goBack = { if (!onBack()) navController.popBackStack() }
    BackHandler(enabled = phase != PcControlSetupPhase.Welcome) { goBack() }

    val titleRes = when (phase) {
        PcControlSetupPhase.OpeningSurface -> R.string.pc_setup_surface_title
        PcControlSetupPhase.Bluetooth -> R.string.pc_setup_bluetooth_title
        else -> R.string.pc_setup_welcome_title
    }
    BaseView(
        titleResId = titleRes,
        navController = navController,
        onBackPressed = { goBack() }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Dimens.spaceM)
        ) {
            Text(
                text = stringResource(R.string.pc_setup_step, phase.stepNumber, PcControlSetupPhase.STEP_COUNT),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
            )
            when (phase) {
                PcControlSetupPhase.OpeningSurface -> SurfaceStep(surface, onChooseSurface, onContinue, goBack)
                PcControlSetupPhase.Bluetooth -> BluetoothStep(onFinish, goBack)
                else -> WelcomeStep(onContinue)
            }
        }
    }
}

@Composable
private fun WelcomeStep(onContinue: () -> Unit) {
    val context = LocalContext.current
    val unableToOpen = stringResource(R.string.pc_setup_download_unavailable, SWITCHIFY_PC_RELEASES_URL)
    HeroPanel(
        titleRes = R.string.pc_setup_welcome_heading,
        bodyRes = R.string.pc_setup_welcome_body,
        features = listOf(
            Icons.Rounded.Mouse to R.string.pc_setup_feature_mouse,
            Icons.Rounded.Keyboard to R.string.pc_setup_feature_typing,
            Icons.Rounded.WebAsset to R.string.pc_setup_feature_window,
            Icons.Rounded.SwitchAccessShortcut to R.string.pc_setup_feature_forwarding
        )
    )
    Panel(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(Dimens.spaceM),
            verticalArrangement = Arrangement.spacedBy(Dimens.spaceS)
        ) {
            Heading(R.string.pc_setup_before_heading)
            Text(
                text = stringResource(R.string.pc_setup_before_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            ActionButton(
                textResId = R.string.pc_setup_get_pc,
                type = ActionButtonType.SECONDARY,
                leadingIcon = Icons.Rounded.Download,
                applyPadding = false,
                onClick = {
                    try {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, SWITCHIFY_PC_RELEASES_URL.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    } catch (_: ActivityNotFoundException) {
                        Toast.makeText(context, unableToOpen, Toast.LENGTH_LONG).show()
                    }
                }
            )
        }
    }
    ActionButton(
        textResId = R.string.pc_setup_continue,
        onClick = onContinue,
        applyPadding = false,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun SurfaceStep(
    selected: PcRemoteSurface,
    onSelect: (PcRemoteSurface) -> Unit,
    onContinue: () -> Unit,
    onBack: () -> Unit
) {
    Text(
        text = stringResource(R.string.pc_setup_surface_body),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    PcSurfaceRadioGroup(selected = selected, onSelect = onSelect)
    ActionButton(
        textResId = R.string.pc_setup_continue,
        onClick = onContinue,
        applyPadding = false,
        modifier = Modifier.fillMaxWidth()
    )
    ActionButton(
        textResId = R.string.pc_setup_back,
        type = ActionButtonType.SECONDARY,
        onClick = onBack,
        applyPadding = false,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun BluetoothStep(onFinish: (Boolean) -> Unit, onBack: () -> Unit) {
    HeroPanel(
        titleRes = R.string.pc_setup_bluetooth_heading,
        bodyRes = R.string.pc_setup_bluetooth_body,
        features = listOf(
            Icons.Rounded.VerifiedUser to R.string.pc_setup_feature_code,
            Icons.Rounded.Computer to R.string.pc_setup_feature_approve,
            Icons.Rounded.CloudOff to R.string.pc_setup_feature_local
        )
    )
    Text(
        text = stringResource(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) R.string.pc_setup_permission_nearby
            else R.string.pc_setup_permission_location
        ),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    ActionButton(
        textResId = R.string.pc_setup_allow_bluetooth,
        leadingIcon = Icons.Rounded.Bluetooth,
        onClick = { onFinish(true) },
        applyPadding = false,
        modifier = Modifier.fillMaxWidth()
    )
    ActionButton(
        textResId = R.string.pc_setup_not_now,
        type = ActionButtonType.SECONDARY,
        onClick = { onFinish(false) },
        applyPadding = false,
        modifier = Modifier.fillMaxWidth()
    )
    ActionButton(
        textResId = R.string.pc_setup_back,
        type = ActionButtonType.SECONDARY,
        onClick = onBack,
        applyPadding = false,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
fun PcSurfaceRadioGroup(selected: PcRemoteSurface, onSelect: (PcRemoteSurface) -> Unit) {
    Panel(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.selectableGroup()) {
            PcRemoteSurface.entries.forEach { surface ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(selected = surface == selected, role = Role.RadioButton, onClick = { onSelect(surface) })
                        .padding(horizontal = Dimens.spaceM, vertical = Dimens.spaceS),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.spaceS),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = surface == selected, onClick = null)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = stringResource(pcSurfaceLabel(surface)), style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = stringResource(pcSurfaceSummary(surface)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

fun pcSurfaceLabel(surface: PcRemoteSurface): Int = when (surface) {
    PcRemoteSurface.Mouse -> R.string.pc_surface_mouse
    PcRemoteSurface.Typing -> R.string.pc_surface_typing
    PcRemoteSurface.Window -> R.string.pc_surface_window
    PcRemoteSurface.Forwarding -> R.string.pc_surface_forwarding
}

private fun pcSurfaceSummary(surface: PcRemoteSurface): Int = when (surface) {
    PcRemoteSurface.Mouse -> R.string.pc_setup_feature_mouse
    PcRemoteSurface.Typing -> R.string.pc_setup_feature_typing
    PcRemoteSurface.Window -> R.string.pc_setup_feature_window
    PcRemoteSurface.Forwarding -> R.string.pc_setup_feature_forwarding
}

@Composable
private fun HeroPanel(titleRes: Int, bodyRes: Int, features: List<Pair<ImageVector, Int>>) {
    Panel(
        modifier = Modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.primaryContainer
    ) {
        Column(
            modifier = Modifier.padding(Dimens.spaceM),
            verticalArrangement = Arrangement.spacedBy(Dimens.spaceS)
        ) {
            Heading(titleRes, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Text(
                text = stringResource(bodyRes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            features.forEach { (icon, textRes) ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Dimens.spaceS),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = stringResource(textRes),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun Heading(textRes: Int, color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface) {
    Text(
        text = stringResource(textRes),
        style = MaterialTheme.typography.titleLarge,
        color = color,
        modifier = Modifier.semantics { heading() }
    )
}
