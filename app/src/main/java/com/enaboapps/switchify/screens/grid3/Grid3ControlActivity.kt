package com.enaboapps.switchify.screens.grid3

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.RadioButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.enaboapps.switchify.R
import com.enaboapps.switchify.activities.ui.theme.SwitchifyTheme
import com.enaboapps.switchify.components.ActionButton
import com.enaboapps.switchify.components.ActionButtonType
import com.enaboapps.switchify.components.EqualHeightGridRow
import com.enaboapps.switchify.components.Panel
import com.enaboapps.switchify.components.Section
import com.enaboapps.switchify.service.core.ServiceCore
import com.enaboapps.switchify.service.grid3.Grid3ConnectionStatus
import com.enaboapps.switchify.service.grid3.Grid3ForwardingState
import com.enaboapps.switchify.service.grid3.Grid3SwitchMapping
import com.enaboapps.switchify.service.grid3.Grid3StartResult
import com.enaboapps.switchify.service.grid3.PcSwitchCatalogResult
import com.enaboapps.switchify.pc.PcSwitchProfileCatalog
import com.enaboapps.switchify.pc.PcSwitchProfileSummary
import com.enaboapps.switchify.pc.PcSwitchProfilePreferenceStore
import com.enaboapps.switchify.pc.selectPcSwitchProfile
import com.enaboapps.switchify.theme.Dimens
import kotlinx.coroutines.launch

open class PcSwitchControlActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val forwarder = ServiceCore.getPcSwitchControlForwarder()
        if (forwarder == null) {
            finish()
            return
        }
        setContent {
            SwitchifyTheme {
                val state by forwarder.state.collectAsState()
                var showChooser by remember { mutableStateOf(!state.active) }
                var changingProfile by remember { mutableStateOf(false) }
                LaunchedEffect(state.active, changingProfile) {
                    if (changingProfile && !state.active) {
                        changingProfile = false
                        showChooser = true
                    }
                }
                if (state.active && !showChooser) {
                    Grid3ControlScreen(
                        state = state,
                        onChangeProfile = {
                            changingProfile = true
                            forwarder.requestStop()
                        },
                        onStop = {
                            forwarder.requestStop()
                            finish()
                        }
                    )
                } else {
                    PcSwitchProfileChooser(
                        forwarder = forwarder,
                        onStarted = { showChooser = false },
                        onClose = { finish() }
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        if (isFinishing) {
            ServiceCore.getPcSwitchControlForwarder()?.requestStop()
        }
        super.onDestroy()
    }

    companion object {
        fun createIntent(context: Context): Intent {
            return Intent(context, PcSwitchControlActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
    }
}

@Composable
private fun Grid3ControlScreen(
    state: Grid3ForwardingState,
    onChangeProfile: () -> Unit,
    onStop: () -> Unit
) {
    BackHandler(onBack = onStop)
    val holdDuration = holdDurationLabel(state.holdToStopDurationMs)
    Scaffold(
        bottomBar = {
            Grid3BottomBar(
                holdDuration = holdDuration,
                onChangeProfile = onChangeProfile,
                onStop = onStop
            )
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
            Grid3StatusHero(
                connectionStatus = state.connectionStatus,
                pcName = state.pcName,
                profileName = state.profileName
            )
            Section(titleResId = R.string.grid3_switch_mapping) {
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
                                Grid3SwitchTile(
                                    mapping = mapping,
                                    modifier = itemModifier
                                )
                            }
                        }
                    }
                }
            }
            if (state.overflowSwitches.isNotEmpty()) {
                Grid3OverflowPanel(names = state.overflowSwitches)
            }
        }
    }
}

@Composable
private fun PcSwitchProfileChooser(
    forwarder: com.enaboapps.switchify.service.grid3.Grid3SwitchForwarder,
    onStarted: () -> Unit,
    onClose: () -> Unit
) {
    var catalog by remember { mutableStateOf<PcSwitchProfileCatalog?>(null) }
    var selected by remember { mutableStateOf<PcSwitchProfileSummary?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    val preferences = remember { PcSwitchProfilePreferenceStore(context) }

    fun load() {
        loading = true
        message = null
        scope.launch {
            when (val result = forwarder.loadProfileCatalog()) {
                is PcSwitchCatalogResult.Loaded -> {
                    catalog = result.catalog
                    val remembered = preferences.rememberedProfileId(forwarder.currentPcId())
                    val selection = selectPcSwitchProfile(result.catalog.profiles, remembered)
                    selected = selection.profile
                    if (selection.rememberedProfileUnavailable) {
                        message = context.getString(R.string.pc_switch_previous_profile_unavailable)
                    }
                }
                is PcSwitchCatalogResult.Failed -> message = result.message
                PcSwitchCatalogResult.Unsupported ->
                    message = context.getString(R.string.grid3_pc_unsupported)
            }
            loading = false
        }
    }

    LaunchedEffect(Unit) { load() }
    BackHandler(onBack = onClose)
    Scaffold(
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
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
                    textResId = if (catalog == null) R.string.pc_switch_retry else R.string.pc_switch_start,
                    onClick = {
                        if (catalog == null) {
                            load()
                        } else {
                            val profile = selected ?: return@ActionButton
                            scope.launch {
                                loading = true
                                when (val result = forwarder.start(profile, catalog!!.legacy)) {
                                    Grid3StartResult.Started -> {
                                        preferences.rememberProfile(forwarder.currentPcId(), profile.id)
                                        onStarted()
                                    }
                                    Grid3StartResult.NoExternalSwitches ->
                                        message = context.getString(R.string.grid3_no_external_switches)
                                    Grid3StartResult.UnsupportedPc ->
                                        message = context.getString(R.string.grid3_pc_unsupported)
                                    Grid3StartResult.ProfileChanged ->
                                        message = context.getString(R.string.pc_switch_profile_changed)
                                    is Grid3StartResult.Failed -> message = result.message
                                }
                                loading = false
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !loading && (catalog == null || selected != null) &&
                        (catalog == null || forwarder.configuredExternalSwitchCount() > 0)
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
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(R.string.pc_switch_choose_profile),
                style = MaterialTheme.typography.bodyLarge
            )
            if (loading) CircularProgressIndicator()
            message?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive }
                )
            }
            if (forwarder.configuredExternalSwitchCount() == 0) {
                Text(
                    text = stringResource(R.string.grid3_no_external_switches),
                    color = MaterialTheme.colorScheme.error
                )
            }
            catalog?.profiles?.forEach { profile ->
                Surface(
                    onClick = { selected = profile },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(Dimens.spaceM),
                    color = if (selected?.id == profile.id) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainer
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(Dimens.spaceM),
                        verticalAlignment = Alignment.Top
                    ) {
                        RadioButton(
                            selected = selected?.id == profile.id,
                            onClick = { selected = profile }
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(profile.name, fontWeight = FontWeight.SemiBold)
                            Text(
                                if (profile.kind == "grid3") "Native Grid 3" else "Windows input",
                                style = MaterialTheme.typography.bodySmall
                            )
                            profile.bindings.forEach { binding ->
                                Text(
                                    text = "${binding.switchId}. ${binding.label}",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

class Grid3ControlActivity : PcSwitchControlActivity()

@Composable
private fun Grid3StatusHero(
    connectionStatus: Grid3ConnectionStatus,
    pcName: String?,
    profileName: String?
) {
    val reconnecting = connectionStatus == Grid3ConnectionStatus.Reconnecting
    val displayName = pcName ?: stringResource(R.string.grid3_pc_fallback_name)
    val statusText = stringResource(
        if (reconnecting) R.string.grid3_reconnecting else R.string.grid3_connected,
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
                Grid3ConnectionIndicator(reconnecting = reconnecting)
                Text(
                    text = stringResource(R.string.grid3_active_eyebrow),
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.onSurfaceVariant
                )
            }
            Text(
                text = stringResource(R.string.grid3_title),
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
                    text = stringResource(R.string.grid3_summary),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun Grid3ConnectionIndicator(reconnecting: Boolean) {
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
                .background(Color(0xFF66BB6A), CircleShape)
        )
    }
}

@Composable
private fun Grid3SwitchTile(
    mapping: Grid3SwitchMapping,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(Dimens.spaceS)
    val containerColor by animateColorAsState(
        targetValue = if (mapping.pressed) {
            colors.primaryContainer
        } else {
            colors.surfaceContainerHigh
        },
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "grid3SwitchFill"
    )
    val tileDescription = stringResource(
        R.string.grid3_switch_tile_description,
        mapping.name,
        mapping.switchId,
        mapping.outputLabel
    )
    val pressedDescription = stringResource(
        if (mapping.pressed) {
            R.string.grid3_switch_state_pressed
        } else {
            R.string.grid3_switch_state_idle
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
                    text = mapping.outputLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (mapping.pressed) {
                        colors.onPrimaryContainer
                    } else {
                        colors.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun Grid3OverflowPanel(names: List<String>) {
    Panel(
        modifier = Modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(
            modifier = Modifier.padding(Dimens.spaceM),
            verticalArrangement = Arrangement.spacedBy(Dimens.spaceXs)
        ) {
            Text(
                text = stringResource(R.string.grid3_overflow_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = stringResource(R.string.grid3_overflow_description),
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
private fun Grid3BottomBar(
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
                .navigationBarsPadding()
                .padding(
                    horizontal = Dimens.spaceM,
                    vertical = Dimens.spaceS
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Dimens.spaceXs)
        ) {
            Text(
                text = stringResource(R.string.grid3_hold_to_stop_hint, holdDuration),
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
                    textResId = R.string.grid3_stop,
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
        return pluralStringResource(R.plurals.grid3_hold_seconds, seconds, seconds)
    }
    return stringResource(
        R.string.grid3_hold_fractional_seconds,
        durationMs / 1_000.0
    )
}
