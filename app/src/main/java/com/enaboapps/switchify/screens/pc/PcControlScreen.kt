package com.enaboapps.switchify.screens.pc

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SettingsRemote
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.enaboapps.switchify.R
import com.enaboapps.switchify.components.BaseView
import com.enaboapps.switchify.pc.control.PcControlSetup
import com.enaboapps.switchify.pc.control.PcControlSetupPhase
import com.enaboapps.switchify.pc.control.PcControlTab
import com.enaboapps.switchify.pc.control.SharedPreferencesPcStorage
import com.enaboapps.switchify.pc.control.asControlConnection
import com.enaboapps.switchify.pc.remote.PcRemoteGraph
import com.enaboapps.switchify.pc.transport.PcBluetoothPermissions
import com.enaboapps.switchify.screens.pc.remote.PcRemoteScreen
import com.enaboapps.switchify.theme.Dimens

@Composable
fun PcControlScreen(navController: NavController) {
    val context = LocalContext.current
    val graph = remember { PcRemoteGraph.getInstance(context) }
    val viewModel: PcControlViewModel = viewModel {
        PcControlViewModel(
            connection = graph.connection.manager.asControlConnection(),
            setup = PcControlSetup(SharedPreferencesPcStorage(context, PcControlSetup.FILE_NAME)),
            permissions = graph.connection.permissions,
            preferences = graph.preferences,
            cleanupScope = graph.scope
        )
    }
    val phase by viewModel.setupPhase.collectAsStateWithLifecycle()
    val tab by viewModel.tab.collectAsStateWithLifecycle()
    val permissionRequested by viewModel.permissionRequested.collectAsStateWithLifecycle()
    var permissionPromptShown by rememberSaveable { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        permissionPromptShown = false
        viewModel.onPermissionResult(PcBluetoothPermissions.allGranted(results))
    }

    LaunchedEffect(permissionRequested) {
        if (permissionRequested && !permissionPromptShown) {
            permissionPromptShown = true
            permissionLauncher.launch(PcBluetoothPermissions.required().toTypedArray())
        } else if (!permissionRequested) {
            permissionPromptShown = false
        }
    }

    val activity = context.findActivity()
    DisposableEffect(activity, viewModel) {
        val owner = activity as? LifecycleOwner
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> viewModel.onStart()
                Lifecycle.Event.ON_STOP -> viewModel.onStop(changingConfigurations = activity?.isChangingConfigurations == true)
                else -> Unit
            }
        }
        owner?.lifecycle?.addObserver(observer)
        onDispose { owner?.lifecycle?.removeObserver(observer) }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onResume() }

    if (phase != PcControlSetupPhase.Complete) {
        val surface by viewModel.surface.collectAsStateWithLifecycle()
        PcControlSetupScreen(
            navController = navController,
            phase = phase,
            surface = surface,
            onContinue = viewModel::continueSetup,
            onBack = viewModel::backInSetup,
            onChooseSurface = viewModel::chooseOpeningSurface,
            onFinish = viewModel::finishSetup
        )
        return
    }

    val current = tab
    if (current == null) {
        BaseView(titleResId = R.string.screen_title_pc_control, navController = navController, enableScroll = false) {
            val loading = stringResource(R.string.pc_control_loading)
            Box(modifier = Modifier.fillMaxWidth().padding(Dimens.spaceL), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.semantics { contentDescription = loading })
            }
        }
        return
    }

    val tabBar: @Composable () -> Unit = { PcControlTabBar(current, viewModel::selectTab) }
    when (current) {
        PcControlTab.Pcs -> PcConnectionScreen(navController, tabBar)
        PcControlTab.Remote -> PcRemoteScreen(
            navController = navController,
            tabBar = tabBar,
            onManagePcs = { viewModel.selectTab(PcControlTab.Pcs) }
        )
        PcControlTab.Settings -> PcControlSettingsScreen(navController, tabBar)
    }
}

@Composable
private fun PcControlTabBar(selected: PcControlTab, onSelect: (PcControlTab) -> Unit) {
    NavigationBar {
        PcControlTab.entries.forEach { tab ->
            NavigationBarItem(
                selected = tab == selected,
                onClick = { onSelect(tab) },
                icon = { Icon(imageVector = tab.icon, contentDescription = null) },
                label = { Text(stringResource(tab.labelRes)) }
            )
        }
    }
}

private val PcControlTab.labelRes: Int
    get() = when (this) {
        PcControlTab.Pcs -> R.string.pc_control_tab_pcs
        PcControlTab.Remote -> R.string.pc_control_tab_remote
        PcControlTab.Settings -> R.string.pc_control_tab_settings
    }

private val PcControlTab.icon: ImageVector
    get() = when (this) {
        PcControlTab.Pcs -> Icons.Rounded.Computer
        PcControlTab.Remote -> Icons.Rounded.SettingsRemote
        PcControlTab.Settings -> Icons.Rounded.Settings
    }

internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
