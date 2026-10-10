package com.enaboapps.switchify.screens.pc.remote

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.enaboapps.switchify.R
import com.enaboapps.switchify.components.ActionButton
import com.enaboapps.switchify.components.ActionButtonType
import com.enaboapps.switchify.components.BaseView
import com.enaboapps.switchify.components.Panel
import com.enaboapps.switchify.components.PillTab
import com.enaboapps.switchify.components.PillTabRow
import com.enaboapps.switchify.nav.NavigationRoute
import com.enaboapps.switchify.pc.connection.PcConnectionState
import com.enaboapps.switchify.pc.connection.PcProfileStatus
import com.enaboapps.switchify.pc.remote.PcRemoteGraph
import com.enaboapps.switchify.pc.remote.PcRemotePresentation
import com.enaboapps.switchify.pc.remote.PcRemoteSurface
import com.enaboapps.switchify.pc.remote.asRemoteConnection
import com.enaboapps.switchify.theme.Dimens

@Composable
fun PcRemoteScreen(navController: NavController) {
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

    LifecycleEventEffect(Lifecycle.Event.ON_START) { viewModel.onStart() }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        viewModel.onStop(changingConfigurations = context.findActivity()?.isChangingConfigurations == true)
    }

    val managePcs = { navController.navigate(NavigationRoute.PcConnection.name) }

    BaseView(
        titleResId = R.string.screen_title_pc_remote,
        navController = navController,
        bottomBar = {
            PcRemoteDeviceSwitcher(
                connection = connection,
                loadSaved = viewModel::listSaved,
                onSelect = viewModel::switchTo,
                onManagePcs = managePcs,
                modifier = Modifier.padding(horizontal = Dimens.spaceM, vertical = Dimens.spaceXs)
            )
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
                    SurfaceSelector(surface, viewModel::selectSurface)
                    DisconnectedContent(current, onRetry = viewModel::retry, onChoose = managePcs)
                }
                current.profile == null -> ProfileUnavailable(current.profileStatus)
                activeHolder != null && activeHolder.desktopId == current.desktop.desktopId -> {
                    ConnectedHeader(current)
                    SurfaceSelector(surface, viewModel::selectSurface)
                    val state by activeHolder.session.state.collectAsState()
                    LaunchedEffect(state.repeat) { viewModel.refreshPhysicalSwitchStop() }
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
                    }
                }
            }
        }
    }
}

@Composable
private fun SurfaceSelector(selected: PcRemoteSurface, onSelect: (PcRemoteSurface) -> Unit) {
    val surfaces = PcRemoteSurface.entries
    PillTabRow(
        tabs = surfaces.map { PillTab(label = stringResource(surfaceLabel(it))) },
        selectedIndex = surfaces.indexOf(selected),
        onTabSelected = { index -> onSelect(surfaces[index]) }
    )
}

private fun surfaceLabel(surface: PcRemoteSurface) = when (surface) {
    PcRemoteSurface.Mouse -> R.string.pc_surface_mouse
    PcRemoteSurface.Typing -> R.string.pc_surface_typing
    PcRemoteSurface.Window -> R.string.pc_surface_window
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

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
