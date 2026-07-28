package com.enaboapps.switchify.screens.pcswitchcontrol

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.enaboapps.switchify.R
import com.enaboapps.switchify.activities.ui.theme.SwitchifyTheme
import com.enaboapps.switchify.components.ActionButton
import com.enaboapps.switchify.components.ActionButtonType
import com.enaboapps.switchify.components.EqualHeightGridRow
import com.enaboapps.switchify.components.Panel
import com.enaboapps.switchify.components.Section
import com.enaboapps.switchify.service.core.ServiceCore
import com.enaboapps.switchify.service.pcswitchcontrol.PcSwitchControlConnectionStatus
import com.enaboapps.switchify.service.pcswitchcontrol.PcSwitchControlState
import com.enaboapps.switchify.service.pcswitchcontrol.PcSwitchControlForwarder
import com.enaboapps.switchify.service.pcswitchcontrol.PcSwitchControlMapping
import com.enaboapps.switchify.service.pcswitchcontrol.PcSwitchControlExitReason
import com.enaboapps.switchify.service.utils.DeviceLockObserver
import com.enaboapps.switchify.pc.PcSwitchProfileCatalog
import com.enaboapps.switchify.pc.PcSwitchProfileSummary
import com.enaboapps.switchify.pc.PcSwitcherUiState
import com.enaboapps.switchify.screens.pc.PcSwitcherApprovalDialog
import com.enaboapps.switchify.screens.pc.PcSwitcherDialog
import com.enaboapps.switchify.screens.pc.PcSwitcherStrip
import com.enaboapps.switchify.theme.Dimens
import java.util.UUID

class PcSwitchControlActivity : ComponentActivity() {
    private var forwarder: PcSwitchControlForwarder? = null
    private val chooserViewModel: PcSwitchControlChooserViewModel by viewModels {
        viewModelFactory {
            initializer {
                PcSwitchControlChooserViewModel(
                    applicationContext,
                    requireNotNull(forwarder)
                )
            }
        }
    }
    private lateinit var connectionEpisodeId: String
    private var screenOffReceiverRegistered = false
    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != Intent.ACTION_SCREEN_OFF) return
            forwarder?.requestCloseForScreenSleep(connectionEpisodeId)
            finishAndRemoveTask()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        connectionEpisodeId =
            savedInstanceState?.getString(STATE_CONNECTION_EPISODE_ID)
                ?: UUID.randomUUID().toString()
        val forwarder = ServiceCore.getPcSwitchControlForwarder()
        if (forwarder == null) {
            finish()
            return
        }
        this.forwarder = forwarder
        forwarder.acquireConnectionForEpisode(connectionEpisodeId)
        if (DeviceLockObserver.isKeyguardLocked(this)) {
            forwarder.requestCloseForScreenSleep(connectionEpisodeId)
            finishAndRemoveTask()
            return
        }
        ContextCompat.registerReceiver(
            this,
            screenOffReceiver,
            IntentFilter(Intent.ACTION_SCREEN_OFF),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        screenOffReceiverRegistered = true
        setContent {
            SwitchifyTheme {
                val state by forwarder.state.collectAsState()
                val pcSwitcherState by chooserViewModel.pcSwitcherState.collectAsState()
                val context = LocalContext.current
                var showChooser by remember { mutableStateOf(!state.active) }
                var hasShownActiveSession by remember {
                    mutableStateOf(state.active)
                }
                var transitionAnnouncement by remember { mutableStateOf<String?>(null) }
                val terminalExitReason by forwarder.terminalExitReason.collectAsState()
                LaunchedEffect(terminalExitReason) {
                    val reason = terminalExitReason
                    if (reason == PcSwitchControlExitReason.InactivityTimeout) {
                        finishAndRemoveTask()
                        forwarder.acknowledgeTerminalExit(reason)
                    }
                }
                LaunchedEffect(state.active, showChooser) {
                    if (state.active) {
                        if (showChooser) {
                            showChooser = false
                            transitionAnnouncement =
                                context.getString(
                                    R.string.pc_switch_control_connected,
                                    state.pcName
                                        ?: context.getString(
                                            R.string.pc_switch_control_pc_fallback_name
                                        )
                                )
                        }
                        hasShownActiveSession = true
                    } else if (!state.active && hasShownActiveSession) {
                        hasShownActiveSession = false
                        showChooser = true
                        transitionAnnouncement =
                            context.getString(R.string.pc_switch_chooser_announcement)
                    }
                }
                ModeTransitionAnnouncement(
                    message = transitionAnnouncement,
                    onAnnounced = { transitionAnnouncement = null }
                )
                if (state.active && !showChooser) {
                    PcSwitchControlScreen(
                        state = state,
                        pcSwitcherState = pcSwitcherState,
                        onChangeProfile = {
                            forwarder.requestChangeProfile()
                        },
                        onSwitchPc = chooserViewModel::openPcSwitcher,
                        onStop = {
                            forwarder.requestClose(connectionEpisodeId)
                            finish()
                        }
                    )
                } else {
                    PcSwitchProfileChooser(
                        viewModel = chooserViewModel,
                        pcSwitcherState = pcSwitcherState,
                        configuredExternalSwitchCount =
                            forwarder.configuredExternalSwitchCount(),
                        onSwitchPc = chooserViewModel::openPcSwitcher,
                        onClose = {
                            forwarder.requestClose(connectionEpisodeId)
                            finish()
                        }
                    )
                }
                if (pcSwitcherState.visible) {
                    PcSwitcherDialog(
                        rows = pcSwitcherState.rows,
                        isDiscovering = pcSwitcherState.isDiscovering,
                        switchingDesktopId = pcSwitcherState.switchingDesktopId,
                        message = pcSwitcherState.message,
                        onDismiss = chooserViewModel::dismissPcSwitcher,
                        onRefresh = chooserViewModel::refreshPcSwitcher,
                        onPcSelected = chooserViewModel::switchToPc
                    )
                }
                pcSwitcherState.approvalCode?.let { approvalCode ->
                    PcSwitcherApprovalDialog(
                        approvalCode = approvalCode,
                        onCancel = chooserViewModel::cancelPcSwitchPairing
                    )
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(STATE_CONNECTION_EPISODE_ID, connectionEpisodeId)
        super.onSaveInstanceState(outState)
    }

    override fun onResume() {
        super.onResume()
        val currentForwarder = forwarder ?: run {
            finishAndRemoveTask()
            return
        }
        if (shouldCloseForStaleForwarder(
                currentForwarder,
                ServiceCore.getPcSwitchControlForwarder()
            )
        ) {
            finishAndRemoveTask()
            return
        }
        if (DeviceLockObserver.isKeyguardLocked(this)) {
            currentForwarder.requestCloseForScreenSleep(connectionEpisodeId)
            finishAndRemoveTask()
        }
    }

    override fun onDestroy() {
        if (screenOffReceiverRegistered) {
            unregisterReceiver(screenOffReceiver)
            screenOffReceiverRegistered = false
        }
        if (isFinishing) {
            forwarder?.requestClose(connectionEpisodeId)
        }
        forwarder = null
        super.onDestroy()
    }

    companion object {
        private const val STATE_CONNECTION_EPISODE_ID = "pc_switch_control_connection_episode_id"

        fun createIntent(context: Context): Intent {
            return Intent(context, PcSwitchControlActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
    }
}

internal fun shouldCloseForStaleForwarder(
    activityForwarder: PcSwitchControlForwarder?,
    serviceForwarder: PcSwitchControlForwarder?
): Boolean = activityForwarder == null || serviceForwarder !== activityForwarder

@Composable
private fun PcSwitchControlScreen(
    state: PcSwitchControlState,
    pcSwitcherState: PcSwitcherUiState,
    onChangeProfile: () -> Unit,
    onSwitchPc: () -> Unit,
    onStop: () -> Unit
) {
    BackHandler(onBack = onStop)
    val holdDuration = holdDurationLabel(state.holdToStopDurationMs)
    Scaffold(
        bottomBar = {
            Column {
                PcSwitchBottomBar(
                    holdDuration = holdDuration,
                    onChangeProfile = onChangeProfile,
                    onStop = onStop
                )
                PcSwitcherStrip(
                    connectedDisplayName = pcSwitcherState.connectedDisplayName
                        ?: state.pcName,
                    enabled = true,
                    isDiscovering = pcSwitcherState.isDiscovering,
                    switching = pcSwitcherState.isPreparing ||
                        pcSwitcherState.switchingDesktopId != null,
                    onSwitchClick = onSwitchPc
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(Dimens.spaceM),
            verticalArrangement = Arrangement.spacedBy(Dimens.spaceM)
        ) {
            PcSwitchStatusHero(
                connectionStatus = state.connectionStatus,
                pcName = state.pcName,
                profileName = state.profileName
            )
            Section(titleResId = R.string.pc_switch_control_switch_mapping) {
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Dimens.spaceS)
                ) {
                    val gap = Dimens.spaceXs
                    val fontScale = LocalDensity.current.fontScale.coerceIn(1f, 1.6f)
                    val minimumTileWidth = 146.dp * fontScale
                    val columns = if (maxWidth >= minimumTileWidth * 2 + gap) 2 else 1
                    val tileWidth = (maxWidth - gap * (columns - 1)) / columns
                    Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                        state.mappings.chunked(columns).forEach { rowMappings ->
                            EqualHeightGridRow(
                                items = rowMappings,
                                columns = columns,
                                itemWidth = tileWidth,
                                minItemHeight = 72.dp,
                                horizontalGap = gap
                            ) { mapping, itemModifier ->
                                PcSwitchTile(
                                    mapping = mapping,
                                    modifier = itemModifier
                                )
                            }
                        }
                    }
                }
            }
            if (state.overflowSwitches.isNotEmpty()) {
                PcSwitchOverflowPanel(names = state.overflowSwitches)
            }
        }
    }
}

@Composable
private fun PcSwitchProfileChooser(
    viewModel: PcSwitchControlChooserViewModel,
    pcSwitcherState: PcSwitcherUiState,
    configuredExternalSwitchCount: Int,
    onSwitchPc: () -> Unit,
    onClose: () -> Unit
) {
    val chooserState by viewModel.state.collectAsState()
    BackHandler(onBack = onClose)
    val primaryActionResId = when (chooserState) {
        PcSwitchChooserState.Empty,
        is PcSwitchChooserState.Error -> R.string.pc_switch_retry
        else -> R.string.pc_switch_start
    }
    val primaryActionEnabled = when (val state = chooserState) {
        PcSwitchChooserState.Empty,
        is PcSwitchChooserState.Error -> true
        is PcSwitchChooserState.Ready -> configuredExternalSwitchCount > 0
        PcSwitchChooserState.Loading,
        is PcSwitchChooserState.Starting -> false
    }
    Scaffold(
        bottomBar = {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Dimens.spaceM),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.spaceS)
                ) {
                    ActionButton(
                        textResId = R.string.pc_switch_close,
                        onClick = onClose,
                        modifier = Modifier.weight(1f),
                        type = ActionButtonType.SECONDARY
                    )
                    ActionButton(
                        textResId = primaryActionResId,
                        onClick = {
                            when (val state = chooserState) {
                                PcSwitchChooserState.Empty,
                                is PcSwitchChooserState.Error -> viewModel.retry()
                                is PcSwitchChooserState.Ready -> viewModel.start()
                                PcSwitchChooserState.Loading,
                                is PcSwitchChooserState.Starting -> Unit
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = primaryActionEnabled
                    )
                }
                PcSwitcherStrip(
                    connectedDisplayName = pcSwitcherState.connectedDisplayName,
                    enabled = true,
                    isDiscovering = pcSwitcherState.isDiscovering,
                    switching = pcSwitcherState.isPreparing ||
                        pcSwitcherState.switchingDesktopId != null,
                    onSwitchClick = onSwitchPc
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(Dimens.spaceM),
            verticalArrangement = Arrangement.spacedBy(Dimens.spaceM)
        ) {
            Text(
                text = stringResource(R.string.pc_switch_control_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { heading() }
            )
            Text(
                text = stringResource(R.string.pc_switch_choose_profile),
                style = MaterialTheme.typography.bodyLarge
            )
            when (val state = chooserState) {
                PcSwitchChooserState.Loading -> {
                    PcSwitchChooserProgress(R.string.pc_switch_loading_profiles)
                }
                PcSwitchChooserState.Empty -> {
                    PcSwitchChooserNotice(
                        text = stringResource(R.string.pc_switch_empty_catalog),
                        severity = PcSwitchNoticeSeverity.Information
                    )
                }
                is PcSwitchChooserState.Error -> {
                    PcSwitchChooserNotice(
                        text = state.message,
                        severity = PcSwitchNoticeSeverity.Error
                    )
                }
                is PcSwitchChooserState.Ready -> {
                    state.notice?.let {
                        PcSwitchChooserNotice(text = it.text, severity = it.severity)
                    }
                    if (configuredExternalSwitchCount == 0) {
                        PcSwitchChooserNotice(
                            text = stringResource(R.string.pc_switch_control_no_external_switches),
                            severity = PcSwitchNoticeSeverity.Warning
                        )
                    }
                    PcSwitchChooserProfileContent(
                        catalog = state.catalog,
                        selected = state.selected,
                        enabled = true,
                        onSelected = viewModel::select
                    )
                }
                is PcSwitchChooserState.Starting -> {
                    PcSwitchChooserProgress(R.string.pc_switch_starting)
                    PcSwitchChooserProfileContent(
                        catalog = state.catalog,
                        selected = state.selected,
                        enabled = false,
                        onSelected = {}
                    )
                }
            }
        }
    }
}

@Composable
private fun PcSwitchChooserProgress(textResId: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                liveRegion = LiveRegionMode.Polite
            }
            .padding(vertical = Dimens.spaceS),
        horizontalArrangement = Arrangement.spacedBy(
            Dimens.spaceS,
            Alignment.CenterHorizontally
        ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(20.dp),
            strokeWidth = 2.dp
        )
        Text(
            text = stringResource(textResId),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PcSwitchChooserNotice(
    text: String,
    severity: PcSwitchNoticeSeverity
) {
    val view = LocalView.current
    LaunchedEffect(text) {
        view.announceForAccessibility(text)
    }
    val colors = MaterialTheme.colorScheme
    val containerColor = when (severity) {
        PcSwitchNoticeSeverity.Information -> colors.secondaryContainer
        PcSwitchNoticeSeverity.Warning -> colors.tertiaryContainer
        PcSwitchNoticeSeverity.Error -> colors.errorContainer
    }
    val contentColor = when (severity) {
        PcSwitchNoticeSeverity.Information -> colors.onSecondaryContainer
        PcSwitchNoticeSeverity.Warning -> colors.onTertiaryContainer
        PcSwitchNoticeSeverity.Error -> colors.onErrorContainer
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {},
        shape = RoundedCornerShape(Dimens.spaceS),
        color = containerColor,
        contentColor = contentColor
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(Dimens.spaceM)
        )
    }
}

@Composable
private fun PcSwitchChooserProfileContent(
    catalog: PcSwitchProfileCatalog,
    selected: PcSwitchProfileSummary,
    enabled: Boolean,
    onSelected: (PcSwitchProfileSummary) -> Unit
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val useTwoPanes = maxWidth >= 720.dp
        if (useTwoPanes) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Dimens.spaceM),
                verticalAlignment = Alignment.Top
            ) {
                PcSwitchProfileList(
                    catalog = catalog,
                    selected = selected,
                    enabled = enabled,
                    onSelected = onSelected,
                    modifier = Modifier.weight(0.42f)
                )
                PcSwitchMappingPreview(
                    profile = selected,
                    modifier = Modifier.weight(0.58f)
                )
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(Dimens.spaceM)
            ) {
                PcSwitchProfileList(
                    catalog = catalog,
                    selected = selected,
                    enabled = enabled,
                    onSelected = onSelected
                )
                PcSwitchMappingPreview(profile = selected)
            }
        }
    }
}

@Composable
internal fun PcSwitchProfileList(
    catalog: PcSwitchProfileCatalog,
    selected: PcSwitchProfileSummary,
    enabled: Boolean,
    onSelected: (PcSwitchProfileSummary) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.selectableGroup(),
        verticalArrangement = Arrangement.spacedBy(Dimens.spaceS)
    ) {
        Text(
            text = stringResource(R.string.pc_switch_profiles_heading),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        catalog.profiles.forEach { profile ->
            PcSwitchProfileRow(
                profile = profile,
                selected = selected.id == profile.id,
                enabled = enabled,
                onSelected = { onSelected(profile) }
            )
        }
    }
}

@Composable
private fun PcSwitchProfileRow(
    profile: PcSwitchProfileSummary,
    selected: Boolean,
    enabled: Boolean,
    onSelected: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val mappedSwitchCount = profile.bindings.count { it.behavior != "unassigned" }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                onClick = onSelected,
                enabled = enabled,
                role = Role.RadioButton
            ),
        shape = RoundedCornerShape(Dimens.spaceM),
        color = if (selected) {
            colors.surfaceContainerHighest
        } else {
            colors.surfaceContainer
        },
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) colors.primary else colors.outlineVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(Dimens.spaceM),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.spaceS)
            ) {
                Text(
                    text = profile.name,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                PcSwitchProfileSelectionIndicator(
                    selected = selected,
                    enabled = enabled
                )
            }
            Text(
                text = pcSwitchProviderLabel(profile.kind),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant
            )
            Text(
                text = pluralStringResource(
                    R.plurals.pc_switch_profile_mapping_count,
                    mappedSwitchCount,
                    mappedSwitchCount
                ),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PcSwitchProfileSelectionIndicator(
    selected: Boolean,
    enabled: Boolean
) {
    val colors = MaterialTheme.colorScheme
    val indicatorColor = when {
        !enabled -> colors.onSurface.copy(alpha = 0.38f)
        selected -> colors.primary
        else -> colors.onSurfaceVariant
    }
    Box(
        modifier = Modifier
            .size(24.dp)
            .border(2.dp, indicatorColor, CircleShape)
            .padding(5.dp),
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(indicatorColor, CircleShape)
            )
        }
    }
}

@Composable
private fun PcSwitchMappingPreview(
    profile: PcSwitchProfileSummary,
    modifier: Modifier = Modifier
) {
    val unassigned = stringResource(R.string.pc_switch_control_switch_unassigned)
    val context = LocalContext.current
    val mappings = (1..PcSwitchControlForwarder.MAX_FORWARDED_SWITCHES).map { switchId ->
        switchId to (
            profile.bindings
                .firstOrNull { it.switchId == switchId && it.behavior != "unassigned" }
                ?.label
                ?: unassigned
            )
    }
    val mappingDescription = mappings.joinToString(separator = ", ") { (switchId, label) ->
        context.getString(R.string.pc_switch_binding, switchId, label)
    }
    Panel(
        modifier = modifier
            .fillMaxWidth()
            .clearAndSetSemantics {
                contentDescription = context.getString(
                    R.string.pc_switch_mapping_preview_description,
                    profile.name,
                    mappingDescription
                )
            },
        containerColor = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimens.spaceM),
            verticalArrangement = Arrangement.spacedBy(Dimens.spaceS)
        ) {
            Text(
                text = stringResource(R.string.pc_switch_selected_mapping, profile.name),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            PcSwitchMappingPreviewGrid(mappings = mappings)
        }
    }
}

@Composable
private fun PcSwitchMappingPreviewGrid(mappings: List<Pair<Int, String>>) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val fontScale = LocalDensity.current.fontScale.coerceIn(1f, 1.6f)
        val gap = Dimens.spaceXs
        val minimumTileWidth = 132.dp * fontScale
        val columns = if (maxWidth >= minimumTileWidth * 2 + gap) 2 else 1
        val tileWidth = (maxWidth - gap * (columns - 1)) / columns
        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            mappings.chunked(columns).forEach { rowMappings ->
                EqualHeightGridRow(
                    items = rowMappings,
                    columns = columns,
                    itemWidth = tileWidth,
                    minItemHeight = 52.dp,
                    horizontalGap = gap
                ) { mapping, itemModifier ->
                    PcSwitchMappingPreviewTile(
                        switchId = mapping.first,
                        label = mapping.second,
                        modifier = itemModifier
                    )
                }
            }
        }
    }
}

@Composable
private fun PcSwitchMappingPreviewTile(
    switchId: Int,
    label: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.heightIn(min = 52.dp),
        shape = RoundedCornerShape(Dimens.spaceS),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(Dimens.spaceS),
            horizontalArrangement = Arrangement.spacedBy(Dimens.spaceS),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Box(
                    modifier = Modifier.size(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = switchId.toString(),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
            Text(
                text = label,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun pcSwitchProviderLabel(kind: String): String {
    return stringResource(
        if (kind == "grid3") {
            R.string.pc_switch_provider_grid3
        } else {
            R.string.pc_switch_provider_windows
        }
    )
}

@Composable
private fun ModeTransitionAnnouncement(
    message: String?,
    onAnnounced: () -> Unit
) {
    val view = LocalView.current
    LaunchedEffect(message) {
        if (message != null) {
            view.announceForAccessibility(message)
            onAnnounced()
        }
    }
}

@Composable
private fun PcSwitchStatusHero(
    connectionStatus: PcSwitchControlConnectionStatus,
    pcName: String?,
    profileName: String?
) {
    val reconnecting = connectionStatus == PcSwitchControlConnectionStatus.Reconnecting
    val displayName = pcName ?: stringResource(R.string.pc_switch_control_pc_fallback_name)
    val statusText = stringResource(
        if (reconnecting) R.string.pc_switch_control_reconnecting else R.string.pc_switch_control_connected,
        displayName
    )
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(Dimens.spaceL)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {},
        shape = shape,
        color = Color.Transparent,
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            colors.surfaceColorAtElevation(3.dp),
                            colors.surfaceColorAtElevation(1.dp)
                        )
                    ),
                    shape = shape
                )
                .padding(horizontal = Dimens.spaceL, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(Dimens.spaceS)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.spaceXs)
            ) {
                PcSwitchConnectionIndicator(reconnecting = reconnecting)
                Text(
                    text = stringResource(R.string.pc_switch_control_active_eyebrow),
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.onSurfaceVariant
                )
            }
            Text(
                text = stringResource(R.string.pc_switch_control_title),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = colors.onSurface
            )
            profileName?.let {
                Text(
                    text = stringResource(R.string.pc_switch_active_profile, it),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.spaceXs)) {
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (reconnecting) colors.error else colors.onSurface,
                    modifier = Modifier.semantics {
                        liveRegion = LiveRegionMode.Polite
                    }
                )
                Text(
                    text = stringResource(R.string.pc_switch_control_summary),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun PcSwitchConnectionIndicator(reconnecting: Boolean) {
    if (reconnecting) {
        CircularProgressIndicator(
            modifier = Modifier.size(12.dp),
            color = MaterialTheme.colorScheme.error,
            strokeWidth = 2.dp
        )
    } else {
        val pulseTransition = rememberInfiniteTransition(label = "connectionPulse")
        val pulseScale by pulseTransition.animateFloat(
            initialValue = 1f,
            targetValue = 1.25f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 900),
                repeatMode = RepeatMode.Reverse
            ),
            label = "connectionPulseScale"
        )
        Box(
            modifier = Modifier
                .size(12.dp)
                .scale(pulseScale)
                .background(MaterialTheme.colorScheme.primary, CircleShape)
        )
    }
}

@Composable
private fun PcSwitchTile(
    mapping: PcSwitchControlMapping,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val outputLabel = mapping.outputLabel ?: stringResource(R.string.pc_switch_control_switch_unassigned)
    val shape = RoundedCornerShape(Dimens.spaceS)
    val containerColor by animateColorAsState(
        targetValue = if (mapping.pressed) {
            colors.primaryContainer
        } else {
            colors.surfaceContainerHigh
        },
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "pcSwitchControlFill"
    )
    val tileDescription = stringResource(
        R.string.pc_switch_control_switch_tile_description,
        mapping.name,
        mapping.switchId,
        outputLabel
    )
    val pressedDescription = stringResource(
        if (mapping.pressed) {
            R.string.pc_switch_control_switch_state_pressed
        } else {
            R.string.pc_switch_control_switch_state_idle
        }
    )
    Surface(
        modifier = modifier
            .heightIn(min = 72.dp)
            .clearAndSetSemantics {
                contentDescription = tileDescription
                stateDescription = pressedDescription
            },
        shape = shape,
        color = containerColor,
        border = if (mapping.pressed) BorderStroke(2.dp, colors.primary) else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(Dimens.spaceS),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.spaceS)
        ) {
            Surface(
                shape = CircleShape,
                color = if (mapping.pressed) colors.primary else colors.secondaryContainer
            ) {
                Box(
                    modifier = Modifier.size(36.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = mapping.switchId.toString(),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (mapping.pressed) FontWeight.Bold else FontWeight.Medium,
                        color = if (mapping.pressed) {
                            colors.onPrimary
                        } else {
                            colors.onSecondaryContainer
                        }
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = mapping.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (mapping.pressed) FontWeight.Bold else FontWeight.Medium,
                    color = if (mapping.pressed) {
                        colors.onPrimaryContainer
                    } else {
                        colors.onSurface
                    }
                )
                Text(
                    text = outputLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (mapping.pressed) {
                        colors.onPrimaryContainer
                    } else {
                        colors.onSurfaceVariant
                    },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun PcSwitchOverflowPanel(names: List<String>) {
    Panel(
        modifier = Modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(
            modifier = Modifier.padding(Dimens.spaceM),
            verticalArrangement = Arrangement.spacedBy(Dimens.spaceXs)
        ) {
            Text(
                text = stringResource(R.string.pc_switch_control_overflow_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = stringResource(R.string.pc_switch_control_overflow_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
            )
            Text(
                text = names.joinToString(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PcSwitchBottomBar(
    holdDuration: String,
    onChangeProfile: () -> Unit,
    onStop: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(
            modifier = Modifier
                .padding(
                    horizontal = Dimens.spaceM,
                    vertical = Dimens.spaceS
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Dimens.spaceXs)
        ) {
            Text(
                text = stringResource(R.string.pc_switch_control_hold_to_stop_hint, holdDuration),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Dimens.spaceS)
            ) {
                ActionButton(
                    textResId = R.string.pc_switch_change_profile,
                    onClick = onChangeProfile,
                    modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                    type = ActionButtonType.SECONDARY,
                    applyPadding = false
                )
                ActionButton(
                    textResId = R.string.pc_switch_control_stop,
                    onClick = onStop,
                    modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                    type = ActionButtonType.DESTRUCTIVE,
                    applyPadding = false
                )
            }
        }
    }
}

@Composable
private fun holdDurationLabel(durationMs: Long): String {
    if (durationMs % 1_000L == 0L) {
        val seconds = (durationMs / 1_000L).toInt()
        return pluralStringResource(R.plurals.pc_switch_control_hold_seconds, seconds, seconds)
    }
    return stringResource(
        R.string.pc_switch_control_hold_fractional_seconds,
        durationMs / 1_000.0
    )
}
