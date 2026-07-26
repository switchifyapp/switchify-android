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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.enaboapps.switchify.theme.Dimens

class Grid3ControlActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val forwarder = ServiceCore.getGrid3SwitchForwarder()
        if (forwarder == null || !forwarder.state.value.active) {
            finish()
            return
        }
        setContent {
            SwitchifyTheme {
                val state by forwarder.state.collectAsState()
                LaunchedEffect(state.active) {
                    if (!state.active) finish()
                }
                Grid3ControlScreen(
                    state = state,
                    onStop = {
                        forwarder.requestStop()
                        finish()
                    }
                )
            }
        }
    }

    override fun onDestroy() {
        if (isFinishing) {
            ServiceCore.getGrid3SwitchForwarder()?.requestStop()
        }
        super.onDestroy()
    }

    companion object {
        fun createIntent(context: Context): Intent {
            return Intent(context, Grid3ControlActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
    }
}

@Composable
private fun Grid3ControlScreen(
    state: Grid3ForwardingState,
    onStop: () -> Unit
) {
    BackHandler(onBack = onStop)
    val holdDuration = holdDurationLabel(state.holdToStopDurationMs)
    Scaffold(
        bottomBar = {
            Grid3BottomBar(
                holdDuration = holdDuration,
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
                pcName = state.pcName
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
private fun Grid3StatusHero(
    connectionStatus: Grid3ConnectionStatus,
    pcName: String?
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
        mapping.keyCode
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
                    text = stringResource(R.string.grid3_switch_key, mapping.keyCode),
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
            ActionButton(
                textResId = R.string.grid3_stop,
                onClick = onStop,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp),
                type = ActionButtonType.DESTRUCTIVE,
                applyPadding = false
            )
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
