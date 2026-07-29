package com.enaboapps.switchify.screens.pc

import android.content.res.ColorStateList
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.automirrored.filled.KeyboardReturn
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.ViewCompat
import com.enaboapps.switchify.R
import com.enaboapps.switchify.components.AdaptiveStack
import com.enaboapps.switchify.components.SwitchifyTextField
import com.enaboapps.switchify.pc.PcKeyboardKey
import com.enaboapps.switchify.pc.isSafePcTypedText
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.roundToInt

data class PcTypingKeySpec(
    @param:StringRes val labelResId: Int,
    val key: PcKeyboardKey
)

enum class PcTypingTextAction {
    Send,
    SendAndEnter,
    Clear
}

@Composable
fun PcTypingControlScreen(
    typingText: String,
    typingMessage: String?,
    typingDraftReviewWarning: Boolean,
    typingMode: PcTypingMode,
    liveTypingAvailable: Boolean,
    liveTypingPaused: Boolean,
    liveTypingMessage: String?,
    liveTypingAnnouncement: String?,
    liveTypingText: String = "",
    enabled: Boolean,
    onTypingModeSelected: (PcTypingMode) -> Unit,
    onTextChanged: (String) -> Unit,
    onSend: () -> Unit,
    onSendAndEnter: () -> Unit,
    onClear: () -> Unit,
    onClearLiveField: () -> Unit,
    onLiveTypingStarted: () -> Unit,
    onLiveTypingStopped: (String) -> Unit,
    onLiveSessionTextChanged: (String) -> Unit,
    onLiveTextCommitted: (String) -> Boolean,
    onLiveKeyCommitted: (PcKeyboardKey) -> Unit,
    onLiveTypingRetry: () -> Unit,
    onMoveLiveToDraft: () -> Unit,
    onLiveTypingAnnouncementShown: () -> Unit,
    onKeySelected: (PcKeyboardKey) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        PcTypingInput(
            text = typingText,
            message = typingMessage,
            draftReviewWarning = typingDraftReviewWarning,
            typingMode = typingMode,
            liveTypingAvailable = liveTypingAvailable,
            liveTypingPaused = liveTypingPaused,
            liveTypingMessage = liveTypingMessage,
            liveTypingAnnouncement = liveTypingAnnouncement,
            liveTypingText = liveTypingText,
            onTypingModeSelected = onTypingModeSelected,
            onTextChanged = onTextChanged,
            onSend = onSend,
            onSendAndEnter = onSendAndEnter,
            onClear = onClear,
            onClearLiveField = onClearLiveField,
            onLiveTypingStarted = onLiveTypingStarted,
            onLiveTypingStopped = onLiveTypingStopped,
            onLiveSessionTextChanged = onLiveSessionTextChanged,
            onLiveTextCommitted = onLiveTextCommitted,
            onLiveKeyCommitted = onLiveKeyCommitted,
            onLiveTypingRetry = onLiveTypingRetry,
            onMoveLiveToDraft = onMoveLiveToDraft,
            onLiveTypingAnnouncementShown = onLiveTypingAnnouncementShown,
            enabled = enabled
        )
        PcTypingSectionTitle(R.string.pc_typing_section_keys)
        PcTypingKeyGrid(
            specs = pcEditingKeySpecs() + pcSpacingKeySpecs(),
            enabled = enabled && !(typingMode == PcTypingMode.Live && liveTypingPaused),
            onKeySelected = onKeySelected,
            columns = 3
        )
        PcTypingSectionTitle(R.string.pc_typing_section_cursor)
        PcTypingKeyGrid(
            specs = pcCursorKeySpecs(),
            enabled = enabled && !(typingMode == PcTypingMode.Live && liveTypingPaused),
            onKeySelected = onKeySelected,
            columns = 4
        )
        var moreKeysVisible by rememberSaveable { mutableStateOf(false) }
        OutlinedButton(
            onClick = { moreKeysVisible = !moreKeysVisible },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
        ) {
            Text(
                stringResource(
                    if (moreKeysVisible) {
                        R.string.pc_typing_hide_more_keys
                    } else {
                        R.string.pc_typing_more_keys
                    }
                )
            )
        }
        if (moreKeysVisible) {
            PcTypingSectionTitle(R.string.pc_typing_section_document)
                PcTypingKeyGrid(
                    specs = pcDocumentKeySpecs(),
                    enabled = enabled && !(typingMode == PcTypingMode.Live && liveTypingPaused),
                onKeySelected = onKeySelected,
                columns = 4
            )
            PcTypingSectionTitle(R.string.pc_typing_function_keys)
                PcTypingKeyGrid(
                    specs = pcFunctionKeySpecs(),
                    enabled = enabled && !(typingMode == PcTypingMode.Live && liveTypingPaused),
                onKeySelected = onKeySelected,
                columns = 4
            )
        }
    }
}

@Composable
internal fun PcTypingInput(
    text: String,
    message: String?,
    draftReviewWarning: Boolean = false,
    typingMode: PcTypingMode,
    liveTypingAvailable: Boolean,
    liveTypingPaused: Boolean,
    liveTypingMessage: String?,
    liveTypingAnnouncement: String? = null,
    liveTypingText: String = "",
    onTypingModeSelected: (PcTypingMode) -> Unit,
    onTextChanged: (String) -> Unit,
    onSend: () -> Unit,
    onSendAndEnter: () -> Unit,
    onClear: () -> Unit,
    onClearLiveField: () -> Unit = {},
    onLiveTypingStarted: () -> Unit,
    onLiveTypingStopped: (String) -> Unit,
    onLiveSessionTextChanged: (String) -> Unit = {},
    onLiveTextCommitted: (String) -> Boolean,
    onLiveKeyCommitted: (PcKeyboardKey) -> Unit,
    onLiveTypingRetry: () -> Unit,
    onMoveLiveToDraft: () -> Unit = {},
    onLiveTypingAnnouncementShown: () -> Unit = {},
    enabled: Boolean,
    modifier: Modifier = Modifier,
    textFieldModifier: Modifier = Modifier,
    textFieldMinLines: Int = 3,
    textFieldMaxLines: Int = 5,
    textFieldMinHeight: Dp = 96.dp,
    manageLiveSessionLifecycle: Boolean = true
) {
    val effectiveMode = if (typingMode == PcTypingMode.Live && liveTypingAvailable) {
        PcTypingMode.Live
    } else {
        PcTypingMode.Draft
    }
    val draftFocusRequester = remember { FocusRequester() }
    LaunchedEffect(draftReviewWarning, effectiveMode) {
        if (draftReviewWarning && effectiveMode == PcTypingMode.Draft) {
            draftFocusRequester.requestFocus()
        }
    }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        PcTypingModeSelector(
            selectedMode = effectiveMode,
            liveAvailable = liveTypingAvailable,
            enabled = enabled,
            onModeSelected = onTypingModeSelected
        )
        Text(
            text = stringResource(
                when {
                    !liveTypingAvailable -> R.string.pc_typing_live_unavailable
                    effectiveMode == PcTypingMode.Live -> R.string.pc_typing_mode_live_consequence
                    else -> R.string.pc_typing_mode_draft_consequence
                }
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = stringResource(R.string.pc_typing_editor_title),
                        style = MaterialTheme.typography.titleMedium
                    )
                    PcTypingStatusBadge(
                        mode = effectiveMode,
                        paused = liveTypingPaused
                    )
                }
                if (effectiveMode == PcTypingMode.Live) {
                    PcLiveTypingComposer(
                        enabled = enabled,
                        paused = liveTypingPaused,
                        message = liveTypingMessage,
                        announcement = liveTypingAnnouncement,
                        sessionText = liveTypingText,
                        onStarted = onLiveTypingStarted,
                        onStopped = onLiveTypingStopped,
                        onSessionTextChanged = onLiveSessionTextChanged,
                        onTextCommitted = onLiveTextCommitted,
                        onKeyCommitted = onLiveKeyCommitted,
                        onRetry = onLiveTypingRetry,
                        onMoveToDraft = onMoveLiveToDraft,
                        onClearField = onClearLiveField,
                        onAnnouncementShown = onLiveTypingAnnouncementShown,
                        modifier = textFieldModifier,
                        minHeight = textFieldMinHeight,
                        manageSessionLifecycle = manageLiveSessionLifecycle
                    )
                } else {
                    PcTypingComposer(
                        text = text,
                        message = message,
                        draftReviewWarning = draftReviewWarning,
                        onTextChanged = onTextChanged,
                        onSend = onSend,
                        onSendAndEnter = onSendAndEnter,
                        onClear = onClear,
                        enabled = enabled,
                        textFieldModifier = textFieldModifier
                            .focusRequester(draftFocusRequester),
                        textFieldMinLines = textFieldMinLines,
                        textFieldMaxLines = textFieldMaxLines,
                        textFieldMinHeight = textFieldMinHeight
                    )
                }
            }
        }
    }
}

@Composable
private fun PcTypingModeSelector(
    selectedMode: PcTypingMode,
    liveAvailable: Boolean,
    enabled: Boolean,
    onModeSelected: (PcTypingMode) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = stringResource(R.string.pc_typing_mode_heading),
            style = MaterialTheme.typography.labelLarge
        )
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            PcTypingMode.entries.forEachIndexed { index, mode ->
                val consequence = stringResource(
                    if (mode == PcTypingMode.Live) {
                        R.string.pc_typing_mode_live_consequence
                    } else {
                        R.string.pc_typing_mode_draft_consequence
                    }
                )
                SegmentedButton(
                    selected = selectedMode == mode,
                    onClick = { onModeSelected(mode) },
                    enabled = enabled && (mode != PcTypingMode.Live || liveAvailable),
                    shape = SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = PcTypingMode.entries.size
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .semantics { stateDescription = consequence }
                ) {
                    Text(
                        stringResource(
                            if (mode == PcTypingMode.Live) {
                                R.string.pc_typing_mode_live
                            } else {
                                R.string.pc_typing_mode_draft
                            }
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun PcLiveTypingComposer(
    enabled: Boolean,
    paused: Boolean,
    message: String?,
    announcement: String?,
    sessionText: String,
    onStarted: () -> Unit,
    onStopped: (String) -> Unit,
    onSessionTextChanged: (String) -> Unit,
    onTextCommitted: (String) -> Boolean,
    onKeyCommitted: (PcKeyboardKey) -> Unit,
    onRetry: () -> Unit,
    onMoveToDraft: () -> Unit,
    onClearField: () -> Unit,
    onAnnouncementShown: () -> Unit,
    modifier: Modifier,
    minHeight: Dp,
    manageSessionLifecycle: Boolean
) {
    val context = LocalContext.current
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val hintColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    val focusedColor = MaterialTheme.colorScheme.primary.toArgb()
    val unfocusedColor = MaterialTheme.colorScheme.outline.toArgb()
    val hint = stringResource(R.string.pc_typing_live_hint)
    val fieldLabel = stringResource(R.string.pc_typing_live_field_label)
    val pausedDescription = stringResource(R.string.pc_typing_live_field_paused)
    val liveDescription = stringResource(R.string.pc_typing_status_live)
    val liveEditText = remember { AtomicReference<PcLiveTypingEditText?>() }

    DisposableEffect(manageSessionLifecycle) {
        if (manageSessionLifecycle) {
            onStarted()
        }
        onDispose {
            val retainedText = liveEditText.get()
                ?.finishCompositionAndTakeSessionText()
                .orEmpty()
            liveEditText.set(null)
            if (manageSessionLifecycle) {
                onStopped(retainedText)
            } else {
                onSessionTextChanged(retainedText)
            }
        }
    }
    LaunchedEffect(announcement) {
        if (announcement != null) {
            liveEditText.get()?.let { editText ->
                editText.requestFocus()
                editText.announceForAccessibility(announcement)
            }
            onAnnouncementShown()
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AndroidView(
            factory = {
                PcLiveTypingEditText(context).apply {
                    liveEditText.set(this)
                    this.hint = hint
                    val density = resources.displayMetrics.density
                    setPadding(
                        (16 * density).roundToInt(),
                        (8 * density).roundToInt(),
                        (16 * density).roundToInt(),
                        (8 * density).roundToInt()
                    )
                }
            },
            update = { editText ->
                editText.isEnabled = enabled && !paused
                editText.onTextCommitted = onTextCommitted
                editText.onSessionTextChanged = onSessionTextChanged
                editText.onKeyCommitted = onKeyCommitted
                editText.setTextColor(textColor)
                editText.setHintTextColor(hintColor)
                editText.backgroundTintList = ColorStateList(
                    arrayOf(
                        intArrayOf(android.R.attr.state_focused),
                        intArrayOf()
                    ),
                    intArrayOf(focusedColor, unfocusedColor)
                )
                editText.restoreSessionText(sessionText)
                editText.hint = fieldLabel
                editText.contentDescription = null
                ViewCompat.setStateDescription(
                    editText,
                    if (paused) pausedDescription else liveDescription
                )
            },
            modifier = modifier
                .fillMaxWidth()
                .heightIn(min = minHeight)
        )
        Text(
            text = stringResource(R.string.pc_typing_live_support),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedButton(
            onClick = onClearField,
            enabled = enabled && sessionText.isNotEmpty() && !paused,
            modifier = Modifier.heightIn(min = 48.dp)
        ) {
            Text(stringResource(R.string.pc_typing_live_clear_field))
        }
        Text(
            text = stringResource(R.string.pc_typing_live_clear_field_help),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        announcement?.let {
            Text(
                text = it,
                modifier = Modifier.semantics {
                    liveRegion = LiveRegionMode.Polite
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
        message?.let {
            if (paused) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { liveRegion = LiveRegionMode.Assertive }
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.pc_typing_live_paused_title),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            text = stringResource(R.string.pc_typing_live_paused_body),
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Button(
                            onClick = {
                                onRetry()
                                liveEditText.get()?.retryCommittedText()
                            },
                            enabled = enabled,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                        ) {
                            Text(stringResource(R.string.pc_typing_exit_retry))
                        }
                        OutlinedButton(
                            onClick = onMoveToDraft,
                            enabled = sessionText.isNotEmpty(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                        ) {
                            Text(stringResource(R.string.pc_typing_live_move_to_draft))
                        }
                    }
                }
            } else {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
internal fun PcTypingComposer(
    text: String,
    message: String?,
    draftReviewWarning: Boolean = false,
    onTextChanged: (String) -> Unit,
    onSend: () -> Unit,
    onSendAndEnter: () -> Unit,
    onClear: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    textFieldModifier: Modifier = Modifier,
    textFieldMinLines: Int = 3,
    textFieldMaxLines: Int = 5,
    textFieldMinHeight: Dp = 96.dp
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        PcTypingTextBox(
            text = text,
            message = message,
            draftReviewWarning = draftReviewWarning,
            enabled = enabled,
            sendEnabled = enabled && text.isNotEmpty() && isSafePcTypedText(text),
            clearEnabled = enabled && text.isNotEmpty(),
            onTextChanged = onTextChanged,
            onSend = onSend,
            onSendAndEnter = onSendAndEnter,
            onClear = onClear,
            modifier = textFieldModifier,
            textFieldMinLines = textFieldMinLines,
            textFieldMaxLines = textFieldMaxLines,
            textFieldMinHeight = textFieldMinHeight
        )
        message?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun PcTypingTextBox(
    text: String,
    message: String?,
    draftReviewWarning: Boolean,
    enabled: Boolean,
    sendEnabled: Boolean,
    clearEnabled: Boolean,
    onTextChanged: (String) -> Unit,
    onSend: () -> Unit,
    onSendAndEnter: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier,
    textFieldMinLines: Int,
    textFieldMaxLines: Int,
    textFieldMinHeight: Dp
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SwitchifyTextField(
            value = text,
            onValueChange = onTextChanged,
            enabled = enabled,
            minLines = textFieldMinLines,
            maxLines = textFieldMaxLines,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.None),
            label = { Text(stringResource(R.string.pc_typing_text_label)) },
            isError = message != null,
            modifier = modifier
                .fillMaxWidth()
                .heightIn(min = textFieldMinHeight)
        )
        Text(
            text = stringResource(R.string.pc_typing_draft_support),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (draftReviewWarning) {
            Text(
                text = stringResource(R.string.pc_typing_draft_review_warning),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.semantics {
                    liveRegion = LiveRegionMode.Assertive
                }
            )
        }
        AdaptiveStack(
            modifier = Modifier.fillMaxWidth(),
            spacing = 8.dp
        ) {
            pcTypingTextActions().forEach { action ->
                PcTypingTextActionButton(
                    action = action,
                    enabled = when (action) {
                        PcTypingTextAction.Send,
                        PcTypingTextAction.SendAndEnter -> sendEnabled
                        PcTypingTextAction.Clear -> clearEnabled
                    },
                    onClick = when (action) {
                        PcTypingTextAction.Send -> onSend
                        PcTypingTextAction.SendAndEnter -> onSendAndEnter
                        PcTypingTextAction.Clear -> onClear
                    },
                    modifier = Modifier.adaptiveFill()
                )
            }
        }
    }
}

@Composable
private fun PcTypingTextActionButton(
    action: PcTypingTextAction,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val icon = when (action) {
        PcTypingTextAction.Send -> Icons.AutoMirrored.Filled.Send
        PcTypingTextAction.SendAndEnter -> Icons.AutoMirrored.Filled.KeyboardReturn
        PcTypingTextAction.Clear -> Icons.Default.Clear
    }
    val labelResId = when (action) {
        PcTypingTextAction.Send -> R.string.pc_typing_send
        PcTypingTextAction.SendAndEnter -> R.string.pc_typing_send_enter
        PcTypingTextAction.Clear -> R.string.pc_typing_clear
    }
    val buttonModifier = modifier.heightIn(min = 48.dp)
    val content: @Composable RowScope.() -> Unit = {
        Icon(
            imageVector = icon,
            contentDescription = null
        )
        Text(
            text = stringResource(labelResId),
            modifier = Modifier.padding(start = 6.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }

    when (action) {
        PcTypingTextAction.Send -> Button(
            onClick = onClick,
            enabled = enabled,
            modifier = buttonModifier,
            content = content
        )

        PcTypingTextAction.SendAndEnter -> FilledTonalButton(
            onClick = onClick,
            enabled = enabled,
            modifier = buttonModifier,
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.primary
            ),
            content = content
        )

        PcTypingTextAction.Clear -> FilledTonalButton(
            onClick = onClick,
            enabled = enabled,
            modifier = buttonModifier,
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer
            ),
            content = content
        )
    }
}

@Composable
private fun PcTypingStatusBadge(
    mode: PcTypingMode,
    paused: Boolean
) {
    val label = when {
        paused -> stringResource(R.string.pc_typing_status_paused)
        mode == PcTypingMode.Live -> stringResource(R.string.pc_typing_status_live)
        else -> stringResource(R.string.pc_typing_status_draft)
    }
    val containerColor = when {
        paused -> MaterialTheme.colorScheme.tertiaryContainer
        mode == PcTypingMode.Live -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceContainerHighest
    }
    val contentColor = when {
        paused -> MaterialTheme.colorScheme.onTertiaryContainer
        mode == PcTypingMode.Live -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        color = containerColor,
        contentColor = contentColor,
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Text(
            text = "● $label",
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun PcTypingSectionTitle(@StringRes titleResId: Int) {
    Text(
        text = stringResource(titleResId),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface
    )
}

@Composable
internal fun PcTypingKeyGrid(
    specs: List<PcTypingKeySpec>,
    enabled: Boolean,
    onKeySelected: (PcKeyboardKey) -> Unit,
    columns: Int
) {
    PcCompactCommandGrid(
        columns = columns,
        minTileHeightDp = 52,
        cells = specs.map { spec ->
            PcCompactCommandCell(
                labelResId = spec.labelResId,
                enabled = enabled,
                onClick = { onKeySelected(spec.key) }
            )
        }
    )
}

fun pcEditingKeySpecs(): List<PcTypingKeySpec> {
    return listOf(
        PcTypingKeySpec(R.string.pc_key_backspace, PcKeyboardKey.Backspace),
        PcTypingKeySpec(R.string.pc_key_delete, PcKeyboardKey.Delete),
        PcTypingKeySpec(R.string.pc_key_enter, PcKeyboardKey.Enter)
    )
}

fun pcSpacingKeySpecs(): List<PcTypingKeySpec> {
    return listOf(
        PcTypingKeySpec(R.string.pc_key_space, PcKeyboardKey.Space),
        PcTypingKeySpec(R.string.pc_key_tab, PcKeyboardKey.Tab),
        PcTypingKeySpec(R.string.pc_key_escape, PcKeyboardKey.Escape)
    )
}

fun pcTypingTextActions(): List<PcTypingTextAction> {
    return listOf(
        PcTypingTextAction.Send,
        PcTypingTextAction.SendAndEnter,
        PcTypingTextAction.Clear
    )
}

fun pcCursorKeySpecs(): List<PcTypingKeySpec> {
    return listOf(
        PcTypingKeySpec(R.string.pc_key_arrow_left, PcKeyboardKey.ArrowLeft),
        PcTypingKeySpec(R.string.pc_key_arrow_up, PcKeyboardKey.ArrowUp),
        PcTypingKeySpec(R.string.pc_key_arrow_down, PcKeyboardKey.ArrowDown),
        PcTypingKeySpec(R.string.pc_key_arrow_right, PcKeyboardKey.ArrowRight)
    )
}

fun pcDocumentKeySpecs(): List<PcTypingKeySpec> {
    return listOf(
        PcTypingKeySpec(R.string.pc_key_home, PcKeyboardKey.Home),
        PcTypingKeySpec(R.string.pc_key_end, PcKeyboardKey.End),
        PcTypingKeySpec(R.string.pc_key_page_up, PcKeyboardKey.PageUp),
        PcTypingKeySpec(R.string.pc_key_page_down, PcKeyboardKey.PageDown)
    )
}

fun pcFunctionKeySpecs(): List<PcTypingKeySpec> {
    return listOf(
        PcTypingKeySpec(R.string.pc_key_f1, PcKeyboardKey.F1),
        PcTypingKeySpec(R.string.pc_key_f2, PcKeyboardKey.F2),
        PcTypingKeySpec(R.string.pc_key_f3, PcKeyboardKey.F3),
        PcTypingKeySpec(R.string.pc_key_f4, PcKeyboardKey.F4),
        PcTypingKeySpec(R.string.pc_key_f5, PcKeyboardKey.F5),
        PcTypingKeySpec(R.string.pc_key_f6, PcKeyboardKey.F6),
        PcTypingKeySpec(R.string.pc_key_f7, PcKeyboardKey.F7),
        PcTypingKeySpec(R.string.pc_key_f8, PcKeyboardKey.F8),
        PcTypingKeySpec(R.string.pc_key_f9, PcKeyboardKey.F9),
        PcTypingKeySpec(R.string.pc_key_f10, PcKeyboardKey.F10),
        PcTypingKeySpec(R.string.pc_key_f11, PcKeyboardKey.F11),
        PcTypingKeySpec(R.string.pc_key_f12, PcKeyboardKey.F12)
    )
}
