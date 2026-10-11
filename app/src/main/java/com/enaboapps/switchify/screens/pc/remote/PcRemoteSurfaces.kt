package com.enaboapps.switchify.screens.pc.remote

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.enaboapps.switchify.R
import com.enaboapps.switchify.components.Panel
import com.enaboapps.switchify.pc.protocol.PcCommandTypes
import com.enaboapps.switchify.pc.protocol.PcPlatform
import com.enaboapps.switchify.pc.remote.PcLiveTypingFailure
import com.enaboapps.switchify.pc.remote.PcRemoteSessionState
import com.enaboapps.switchify.pc.remote.PcTypingMode
import com.enaboapps.switchify.pc.remote.actions.PcActionContext
import com.enaboapps.switchify.pc.remote.actions.PcActionRuntime
import com.enaboapps.switchify.pc.remote.actions.PcResolvedAction
import com.enaboapps.switchify.pc.remote.actions.PcTypingContext
import com.enaboapps.switchify.pc.remote.layouts.PcLayoutSections
import com.enaboapps.switchify.pc.remote.layouts.PcLayoutSurface
import com.enaboapps.switchify.pc.remote.layouts.PcSurfaceLayouts
import com.enaboapps.switchify.theme.Dimens

@Composable
private fun rememberActions(
    viewModel: PcRemoteViewModel,
    context: PcActionContext
): Pair<List<PcResolvedAction>, (PcResolvedAction) -> Unit> {
    val current: State<PcActionContext> = rememberUpdatedState(context)
    val controls = PcActionRuntime.resolve(context)
    return controls to { action -> viewModel.perform(action.definition) { current.value } }
}

@Composable
fun PcMouseSurface(
    viewModel: PcRemoteViewModel,
    holder: PcRemoteSessionHolder,
    state: PcRemoteSessionState,
    platform: PcPlatform?,
    layouts: PcSurfaceLayouts,
    physicalSwitchStopAvailable: Boolean
) {
    val session = holder.session
    val (controls, onAction) = rememberActions(
        viewModel,
        PcActionContext(PcLayoutSurface.Mouse, session, platform, viewModel.preferences)
    )
    val profile = session.profile
    val speed = profile?.capabilities?.pointerSpeed
    val display = profile?.capabilities?.displayNavigation
    val fontScale = LocalDensity.current.fontScale
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.spaceM)) {
        PcRepeatStatus(session, state, physicalSwitchStopAvailable, viewModel::stopRepeat)
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val twoPane = maxWidth >= 720.dp && fontScale < LARGE_TEXT_SCALE
            val movement: @Composable (Modifier) -> Unit = { modifier ->
                PcSurfaceLayout(PcLayoutSurface.Mouse, PcLayoutSections.MOUSE_MOVEMENT, controls, layouts, onAction, modifier)
            }
            val secondary: @Composable (Modifier) -> Unit = { modifier ->
                Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Dimens.spaceM)) {
                    PcSurfaceLayout(PcLayoutSurface.Mouse, PcLayoutSections.MOUSE_CLICKS, controls, layouts, onAction)
                    if (speed?.supported == true) {
                        PcSurfaceLayout(
                            PcLayoutSurface.Mouse,
                            PcLayoutSections.MOUSE_SPEED,
                            controls,
                            layouts,
                            onAction,
                            title = stringResource(R.string.pc_section_speed_value, formatPercent(speed.scalePercent))
                        )
                    }
                    if (display != null && display.supported && display.displayCount > 1) {
                        PcSurfaceLayout(PcLayoutSurface.Mouse, PcLayoutSections.MONITORS, controls, layouts, onAction)
                    }
                }
            }
            if (twoPane) {
                Row(horizontalArrangement = Arrangement.spacedBy(Dimens.spaceXl), verticalAlignment = Alignment.Top) {
                    movement(Modifier.weight(1f).widthIn(min = 320.dp, max = 400.dp))
                    secondary(Modifier.weight(1f).widthIn(min = 300.dp))
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.spaceXl)) {
                    movement(Modifier.fillMaxWidth())
                    secondary(Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
fun PcWindowSurface(
    viewModel: PcRemoteViewModel,
    holder: PcRemoteSessionHolder,
    state: PcRemoteSessionState,
    platform: PcPlatform?,
    layouts: PcSurfaceLayouts,
    physicalSwitchStopAvailable: Boolean
) {
    val session = holder.session
    val (controls, onAction) = rememberActions(
        viewModel,
        PcActionContext(PcLayoutSurface.Window, session, platform, viewModel.preferences)
    )
    val display = session.profile?.capabilities?.displayNavigation
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.spaceM)) {
        PcRepeatStatus(session, state, physicalSwitchStopAvailable, viewModel::stopRepeat)
        SurfaceCard {
            PcSurfaceLayout(PcLayoutSurface.Window, PcLayoutSections.WINDOW_MODIFIERS, controls, layouts, onAction)
            Text(
                text = stringResource(R.string.pc_modifiers_caption),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        SurfaceCard {
            PcSurfaceLayout(PcLayoutSurface.Window, PcLayoutSections.WINDOW_WINDOWS, controls, layouts, onAction)
        }
        SurfaceCard {
            PcSurfaceLayout(PcLayoutSurface.Window, PcLayoutSections.WINDOW_SHORTCUTS, controls, layouts, onAction)
        }
        if (display != null && display.supported && display.displayCount > 1) {
            SurfaceCard {
                PcSurfaceLayout(PcLayoutSurface.Window, PcLayoutSections.MONITORS, controls, layouts, onAction)
            }
        }
    }
}

@Composable
fun PcTypingSurface(
    viewModel: PcRemoteViewModel,
    holder: PcRemoteSessionHolder,
    state: PcRemoteSessionState,
    platform: PcPlatform?,
    layouts: PcSurfaceLayouts,
    physicalSwitchStopAvailable: Boolean
) {
    val session = holder.session
    val live = holder.liveTyping
    val mode by viewModel.preferences.typingMode.collectAsState()
    val draft by viewModel.preferences.draft.collectAsState()
    val liveText by live.text.collectAsState()
    val liveFailure by live.failure.collectAsState()
    val submitting by live.submitting.collectAsState()
    val liveSupported = live.supported
    val draftSupported = session.supports(PcCommandTypes.KEYBOARD_TYPE_TEXT)
    val (controls, onAction) = rememberActions(
        viewModel,
        PcActionContext(
            surface = PcLayoutSurface.Typing,
            session = session,
            platform = platform,
            drafts = viewModel.preferences,
            typing = PcTypingContext(mode, draft, submitting) { live.submit() }
        )
    )
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.spaceM)) {
        PcRepeatStatus(session, state, physicalSwitchStopAvailable, viewModel::stopRepeat)
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.spaceXs)) {
            PcControlButton(
                label = stringResource(R.string.pc_typing_live),
                onClick = { viewModel.selectTypingMode(PcTypingMode.Live) },
                selected = mode == PcTypingMode.Live,
                enabled = liveSupported,
                modifier = Modifier.weight(1f)
            )
            PcControlButton(
                label = stringResource(R.string.pc_typing_draft),
                onClick = { viewModel.selectTypingMode(PcTypingMode.Draft) },
                selected = mode == PcTypingMode.Draft,
                enabled = draftSupported,
                modifier = Modifier.weight(1f)
            )
        }
        val fieldLabel = stringResource(if (mode == PcTypingMode.Live) R.string.pc_typing_live_label else R.string.pc_typing_draft_label)
        OutlinedTextField(
            value = if (mode == PcTypingMode.Live) liveText else draft,
            onValueChange = { next ->
                if (mode == PcTypingMode.Live) live.change(next)
                else viewModel.setDraft(next)
            },
            enabled = if (mode == PcTypingMode.Live) liveSupported else draftSupported,
            label = { Text(fieldLabel) },
            placeholder = {
                Text(stringResource(if (mode == PcTypingMode.Live) R.string.pc_typing_live_placeholder else R.string.pc_typing_draft_placeholder))
            },
            keyboardOptions = KeyboardOptions(imeAction = if (mode == PcTypingMode.Live) ImeAction.Send else ImeAction.Default),
            keyboardActions = KeyboardActions(onSend = { viewModel.submitLive() }),
            minLines = 5,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 150.dp)
        )
        if (mode == PcTypingMode.Live) {
            LiveStatus(liveSupported, liveFailure, submitting)
            when (liveFailure) {
                PcLiveTypingFailure.Text -> PcControlButton(
                    label = stringResource(R.string.pc_typing_retry_text),
                    onClick = live::retryText,
                    modifier = Modifier.fillMaxWidth()
                )
                PcLiveTypingFailure.Enter -> PcControlButton(
                    label = stringResource(R.string.pc_typing_retry_enter),
                    onClick = viewModel::submitLive,
                    modifier = Modifier.fillMaxWidth()
                )
                null -> Unit
            }
        } else {
            PcSurfaceLayout(PcLayoutSurface.Typing, PcLayoutSections.TYPING_DRAFT, controls, layouts, onAction)
        }
        PcSurfaceLayout(PcLayoutSurface.Typing, PcLayoutSections.TYPING_KEYS, controls, layouts, onAction)
    }
}

@Composable
private fun LiveStatus(supported: Boolean, failure: PcLiveTypingFailure?, submitting: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val message = stringResource(
        when {
            !supported -> R.string.pc_typing_live_unsupported
            failure == PcLiveTypingFailure.Enter -> R.string.pc_typing_enter_failed
            failure == PcLiveTypingFailure.Text -> R.string.pc_typing_text_failed
            submitting -> R.string.pc_typing_sending_enter
            else -> R.string.pc_typing_live_status
        }
    )
    val failed = failure != null || !supported
    Row(
        modifier = Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.spacedBy(Dimens.spaceXs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (failed) Icons.Rounded.ErrorOutline else Icons.Rounded.CheckCircle,
            contentDescription = null,
            tint = if (failed) scheme.error else scheme.primary
        )
        Text(text = message, style = MaterialTheme.typography.bodyMedium, color = if (failed) scheme.error else scheme.onSurface)
    }
}

@Composable
private fun SurfaceCard(content: @Composable () -> Unit) {
    Panel(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(Dimens.spaceM),
            verticalArrangement = Arrangement.spacedBy(Dimens.spaceXs)
        ) {
            content()
        }
    }
}

private fun formatPercent(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()

private const val LARGE_TEXT_SCALE = 1.3f
