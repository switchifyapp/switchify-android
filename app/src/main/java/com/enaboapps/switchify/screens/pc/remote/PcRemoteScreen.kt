package com.enaboapps.switchify.screens.pc.remote

import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.PortableWifiOff
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.enaboapps.switchify.R
import com.enaboapps.switchify.components.ActionButton
import com.enaboapps.switchify.components.ActionButtonType
import com.enaboapps.switchify.components.BaseView
import com.enaboapps.switchify.components.Panel
import com.enaboapps.switchify.pc.connection.PcConnectionState
import com.enaboapps.switchify.pc.connection.PcProfileStatus
import com.enaboapps.switchify.pc.remote.PcRemoteGraph
import com.enaboapps.switchify.pc.remote.PcRemotePresentation
import com.enaboapps.switchify.pc.remote.PcRemoteSurface
import com.enaboapps.switchify.pc.remote.asRemoteConnection
import com.enaboapps.switchify.pc.remote.layouts.PcLayoutEditBlock
import com.enaboapps.switchify.pc.remote.layouts.PcLayoutEditBlocking
import com.enaboapps.switchify.screens.pc.PcForwardingSurface
import com.enaboapps.switchify.theme.Dimens
import com.enaboapps.switchify.utils.findActivity

@Composable
fun PcRemoteScreen(
    navController: NavController,
    tabBar: @Composable () -> Unit,
    onManagePcs: () -> Unit,
    onBackPressed: () -> Unit
) {
    val context = LocalContext.current
    val graph = remember { PcRemoteGraph.getInstance(context) }
    val viewModel: PcRemoteViewModel = viewModel {
        PcRemoteViewModel(
            manager = graph.connection.manager.asRemoteConnection(),
            preferences = graph.preferences,
            layouts = graph.layouts,
            switchStop = graph.switchStop,
            sessionScope = graph.scope
        )
    }
    val connection by viewModel.connection.collectAsState()
    val holder by viewModel.holder.collectAsState()
    val surface by viewModel.preferences.surface.collectAsState()
    val layouts by viewModel.layouts.layouts.collectAsState()
    val physicalSwitchStopAvailable by viewModel.physicalSwitchStopAvailable.collectAsState()
    val editingLayout by viewModel.editingLayout.collectAsState()
    val layoutEditor by viewModel.layoutEditor.collectAsState()

    LifecycleEventEffect(Lifecycle.Event.ON_START) { viewModel.onVisible() }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        viewModel.onHidden(changingConfigurations = context.findActivity()?.isChangingConfigurations == true)
    }

    DisposableEffect(viewModel) {
        onDispose { viewModel.onHidden(changingConfigurations = context.findActivity()?.isChangingConfigurations == true) }
    }

    val managePcs = onManagePcs
    val scrollState = rememberScrollState()
    val connectedControls = (connection as? PcConnectionState.Connected)?.let { current ->
        current.profile != null && holder?.desktopId == current.desktop.desktopId
    } == true

    BaseView(
        titleResId = R.string.screen_title_pc_remote,
        onBackPressed = onBackPressed,
        navController = navController,
        bottomBar = {
            Column {
                PcRemoteDeviceSwitcher(
                    connection = connection,
                    loadSaved = viewModel::listSaved,
                    onSelect = viewModel::switchTo,
                    onManagePcs = managePcs,
                    modifier = Modifier.padding(horizontal = Dimens.spaceM, vertical = Dimens.spaceXs)
                )
                tabBar()
            }
        },
        scrollState = scrollState,
        contentOverlay = {
            if (connectedControls) {
                PcScrollToTopButton(scrollState, Modifier.align(Alignment.BottomEnd).padding(Dimens.spaceM))
            }
        }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Dimens.spaceM)
        ) {
            val current = connection
            val activeHolder = holder
            when {
                current !is PcConnectionState.Connected -> {
                    PcSurfaceSelector(surface, viewModel::selectSurface)
                    DisconnectedContent(current, onRetry = viewModel::retry, onChoose = managePcs)
                }
                current.profile == null -> ProfileUnavailable(current.profileStatus)
                activeHolder != null && activeHolder.desktopId == current.desktop.desktopId -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.weight(1f)) { ConnectedHeader(current) }
                        if (surface.usesLayouts) PcLayoutEditToggle(editingLayout, viewModel::toggleLayoutEditing)
                    }
                    PcSurfaceSelector(surface, viewModel::selectSurface)
                    val state by activeHolder.session.state.collectAsState()
                    val submittingEnter by activeHolder.liveTyping.submitting.collectAsState()
                    LaunchedEffect(state.repeat) { viewModel.refreshPhysicalSwitchStop() }
                    val blocked = when (PcLayoutEditBlocking.block(state, surface, submittingEnter)) {
                        PcLayoutEditBlock.SendingEnter -> stringResource(R.string.pc_layout_blocked_enter)
                        PcLayoutEditBlock.HeldInput -> stringResource(
                            R.string.pc_layout_blocked_input,
                            stringResource(PcRemotePresentation.repeatStopLabel(state.repeat))
                        )
                        null -> null
                    }
                    val editing = remember(editingLayout, blocked, viewModel) {
                        PcLayoutEditing(
                            enabled = editingLayout,
                            blocked = blocked,
                            open = viewModel::openLayoutEditor,
                            refreshControls = viewModel::refreshLayoutEditorControls
                        )
                    }
                    CompositionLocalProvider(LocalPcLayoutEditing provides editing) {
                        when (surface) {
                            PcRemoteSurface.Mouse -> PcMouseSurface(
                                viewModel, activeHolder, state, current.desktop.platform, layouts, physicalSwitchStopAvailable
                            )
                            PcRemoteSurface.Typing -> PcTypingSurface(
                                viewModel, activeHolder, state, current.desktop.platform, layouts, physicalSwitchStopAvailable
                            )
                            PcRemoteSurface.Window -> PcWindowSurface(
                                viewModel, activeHolder, state, current.desktop.platform, layouts, physicalSwitchStopAvailable
                            )
                            PcRemoteSurface.Forwarding -> PcForwardingSurface(onOpenPcs = managePcs)
                        }
                    }
                    Spacer(modifier = Modifier.height(PcScrollToTop.CLEARANCE))
                }
            }
        }
    }
    layoutEditor?.let { session -> PcLayoutEditorDialog(session, viewModel) }
}

@Composable
private fun ConnectedHeader(connection: PcConnectionState.Connected) {
    var previous by remember { mutableStateOf<PcProfileStatus?>(null) }
    var announcement by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(connection.profileStatus) {
        announcement = PcRemotePresentation.profileAnnouncement(connection.profileStatus, previous)
        previous = connection.profileStatus
    }
    Column(
        modifier = Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(Dimens.spaceXs)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Dimens.spaceXs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = stringResource(R.string.pc_remote_connected_to, connection.desktop.displayName),
                style = MaterialTheme.typography.titleSmall
            )
        }
        announcement?.let { Text(text = stringResource(it), style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun DisconnectedContent(connection: PcConnectionState, onRetry: () -> Unit, onChoose: () -> Unit) {
    val presentation = PcRemotePresentation.disconnected(connection)
    val icon = when {
        presentation.busy -> Icons.Rounded.Sync
        presentation.failed -> Icons.Rounded.ErrorOutline
        else -> Icons.Rounded.Computer
    }
    EmptyState(
        icon = icon,
        title = stringResource(presentation.titleRes),
        body = presentation.message.resolve()
    ) {
        val context = LocalContext.current
        val settingsIntent = when (connection) {
            is PcConnectionState.PermissionDenied -> R.string.pc_open_settings to Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                "package:${context.packageName}".toUri()
            )
            is PcConnectionState.LocationOff -> R.string.pc_open_location_settings to Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            else -> null
        }
        settingsIntent?.let { (labelRes, intent) ->
            ActionButton(
                textResId = labelRes,
                onClick = {
                    try {
                        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    } catch (_: ActivityNotFoundException) {
                    }
                },
                applyPadding = false,
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (presentation.retry) {
            ActionButton(
                textResId = R.string.pc_remote_retry,
                onClick = onRetry,
                applyPadding = false,
                modifier = Modifier.fillMaxWidth()
            )
        }
        presentation.choose?.let { choose ->
            ActionButton(
                textResId = choose.labelRes,
                onClick = onChoose,
                type = ActionButtonType.SECONDARY,
                applyPadding = false,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun ProfileUnavailable(status: PcProfileStatus) {
    val presentation = PcRemotePresentation.profile(status)
    EmptyState(
        icon = if (status == PcProfileStatus.Recovering) Icons.Rounded.Sync else Icons.Rounded.PortableWifiOff,
        title = stringResource(presentation.titleRes),
        body = stringResource(presentation.bodyRes)
    )
}

@Composable
private fun EmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    actions: @Composable () -> Unit = {}
) {
    Panel(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(Dimens.spaceL),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Dimens.spaceS)
        ) {
            Column(
                modifier = Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Dimens.spaceXs)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = Dimens.spaceXs)
                )
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            actions()
        }
    }
}
