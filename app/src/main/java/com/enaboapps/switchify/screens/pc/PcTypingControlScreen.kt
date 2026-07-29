package com.enaboapps.switchify.screens.pc

import android.content.res.ColorStateList
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.ViewCompat
import com.enaboapps.switchify.R
import com.enaboapps.switchify.components.SwitchifyTextField
import com.enaboapps.switchify.pc.PcKeyboardKey
import com.enaboapps.switchify.pc.isSafePcTypedText
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.roundToInt

data class PcTypingKeySpec(
    @param:StringRes val labelResId: Int,
    val key: PcKeyboardKey
)

internal enum class PcTypingPage {
    Editor,
    Keys
}

@Composable
fun PcTypingControlScreen(
    typingText: String,
    typingMessage: String?,
    typingDraftReviewWarning: Boolean,
    typingMode: PcTypingMode,
    draftPressEnterAfterSending: Boolean,
    liveTypingAvailable: Boolean,
    liveTypingPaused: Boolean,
    liveTypingMessage: String?,
    liveTypingAnnouncement: String?,
    liveTypingNotice: String?,
    liveTypingText: String,
    enabled: Boolean,
    onTypingModeSelected: (PcTypingMode) -> Unit,
    onDraftEnterChanged: (Boolean) -> Unit,
    onTextChanged: (String) -> Unit,
    onSendDraft: () -> Unit,
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
    onLiveTypingNoticeShown: () -> Unit,
    onKeySelected: (PcKeyboardKey) -> Unit,
    modifier: Modifier = Modifier,
    manageLiveSessionLifecycle: Boolean = true
) {
    var page by rememberSaveable { mutableStateOf(PcTypingPage.Editor) }
    var restoreKeyboardOnEditorReturn by rememberSaveable { mutableStateOf(false) }
    val fieldFocusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val ownsLiveSession = typingMode == PcTypingMode.Live && liveTypingAvailable
    val latestLiveText by rememberUpdatedState(liveTypingText)

    DisposableEffect(ownsLiveSession, manageLiveSessionLifecycle) {
        if (ownsLiveSession && manageLiveSessionLifecycle) onLiveTypingStarted()
        onDispose {
            if (ownsLiveSession && manageLiveSessionLifecycle) onLiveTypingStopped(latestLiveText)
        }
    }
    LaunchedEffect(liveTypingPaused) {
        if (liveTypingPaused) page = PcTypingPage.Editor
    }
    LaunchedEffect(page, restoreKeyboardOnEditorReturn, liveTypingPaused, enabled) {
        if (page == PcTypingPage.Editor && restoreKeyboardOnEditorReturn) {
            if (!liveTypingPaused && enabled) {
                fieldFocusRequester.requestFocus()
                keyboardController?.show()
            }
            restoreKeyboardOnEditorReturn = false
        }
    }

    if (page == PcTypingPage.Keys) {
        PcTypingKeysPage(
            enabled = enabled,
            onBackToTyping = {
                restoreKeyboardOnEditorReturn = true
                page = PcTypingPage.Editor
            },
            onKeySelected = onKeySelected,
            modifier = modifier
        )
    } else {
        PcTypingEditor(
            headingResId = R.string.pc_typing_type_on_pc,
            typingText = typingText,
            typingMessage = typingMessage,
            typingDraftReviewWarning = typingDraftReviewWarning,
            typingMode = typingMode,
            draftPressEnterAfterSending = draftPressEnterAfterSending,
            liveTypingAvailable = liveTypingAvailable,
            liveTypingPaused = liveTypingPaused,
            liveTypingMessage = liveTypingMessage,
            liveTypingAnnouncement = liveTypingAnnouncement,
            liveTypingNotice = liveTypingNotice,
            liveTypingText = liveTypingText,
            enabled = enabled,
            onTypingModeSelected = onTypingModeSelected,
            onDraftEnterChanged = onDraftEnterChanged,
            onTextChanged = onTextChanged,
            onSendDraft = onSendDraft,
            onClear = onClear,
            onClearLiveField = onClearLiveField,
            onLiveSessionTextChanged = onLiveSessionTextChanged,
            onLiveTextCommitted = onLiveTextCommitted,
            onLiveKeyCommitted = onLiveKeyCommitted,
            onLiveTypingRetry = onLiveTypingRetry,
            onMoveLiveToDraft = onMoveLiveToDraft,
            onLiveTypingAnnouncementShown = onLiveTypingAnnouncementShown,
            onLiveTypingNoticeShown = onLiveTypingNoticeShown,
            onOpenKeys = {
                focusManager.clearFocus(force = true)
                keyboardController?.hide()
                page = PcTypingPage.Keys
            },
            fieldFocusRequester = fieldFocusRequester,
            modifier = modifier
        )
    }
}

@Composable
internal fun PcTypingEditor(
    @StringRes headingResId: Int,
    typingText: String,
    typingMessage: String?,
    typingDraftReviewWarning: Boolean,
    typingMode: PcTypingMode,
    draftPressEnterAfterSending: Boolean,
    liveTypingAvailable: Boolean,
    liveTypingPaused: Boolean,
    liveTypingMessage: String?,
    liveTypingAnnouncement: String?,
    liveTypingNotice: String?,
    liveTypingText: String,
    enabled: Boolean,
    onTypingModeSelected: (PcTypingMode) -> Unit,
    onDraftEnterChanged: (Boolean) -> Unit,
    onTextChanged: (String) -> Unit,
    onSendDraft: () -> Unit,
    onClear: () -> Unit,
    onClearLiveField: () -> Unit,
    onLiveSessionTextChanged: (String) -> Unit,
    onLiveTextCommitted: (String) -> Boolean,
    onLiveKeyCommitted: (PcKeyboardKey) -> Unit,
    onLiveTypingRetry: () -> Unit,
    onMoveLiveToDraft: () -> Unit,
    onLiveTypingAnnouncementShown: () -> Unit,
    onLiveTypingNoticeShown: () -> Unit,
    onOpenKeys: () -> Unit,
    fieldFocusRequester: FocusRequester,
    modifier: Modifier = Modifier,
    textFieldMinLines: Int = 3,
    textFieldMaxLines: Int = 5,
    textFieldMinHeight: Dp = 96.dp
) {
    val effectiveMode = if (typingMode == PcTypingMode.Live && liveTypingAvailable) {
        PcTypingMode.Live
    } else {
        PcTypingMode.Draft
    }
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(liveTypingNotice) {
        if (liveTypingNotice != null) {
            snackbarHostState.showSnackbar(liveTypingNotice)
            onLiveTypingNoticeShown()
        }
    }
    LaunchedEffect(effectiveMode, liveTypingPaused) {
        if (!liveTypingPaused) fieldFocusRequester.requestFocus()
    }

    Box(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(
                    if (effectiveMode == PcTypingMode.Live) {
                        headingResId
                    } else {
                        R.string.pc_typing_write_draft
                    }
                ),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() }
            )
            if (effectiveMode == PcTypingMode.Live) {
                PcLiveTypingEditor(
                    enabled = enabled,
                    paused = liveTypingPaused,
                    message = liveTypingMessage,
                    announcement = liveTypingAnnouncement,
                    sessionText = liveTypingText,
                    onSessionTextChanged = onLiveSessionTextChanged,
                    onTextCommitted = onLiveTextCommitted,
                    onKeyCommitted = onLiveKeyCommitted,
                    onRetry = onLiveTypingRetry,
                    onMoveToDraft = onMoveLiveToDraft,
                    onClearField = onClearLiveField,
                    onAnnouncementShown = onLiveTypingAnnouncementShown,
                    fieldFocusRequester = fieldFocusRequester,
                    minHeight = textFieldMinHeight
                )
                if (!liveTypingPaused) {
                    OutlinedButton(
                        onClick = { onTypingModeSelected(PcTypingMode.Draft) },
                        enabled = enabled,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    ) {
                        Text(stringResource(R.string.pc_typing_write_draft_instead))
                    }
                    OutlinedButton(
                        onClick = onOpenKeys,
                        enabled = enabled,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    ) {
                        Text(stringResource(R.string.pc_typing_pc_keys))
                    }
                }
            } else {
                PcDraftTypingEditor(
                    text = typingText,
                    message = typingMessage,
                    draftReviewWarning = typingDraftReviewWarning,
                    pressEnterAfterSending = draftPressEnterAfterSending,
                    liveTypingAvailable = liveTypingAvailable,
                    enabled = enabled,
                    onTextChanged = onTextChanged,
                    onPressEnterChanged = onDraftEnterChanged,
                    onSend = onSendDraft,
                    onClear = onClear,
                    onTypeLive = { onTypingModeSelected(PcTypingMode.Live) },
                    onOpenKeys = onOpenKeys,
                    fieldFocusRequester = fieldFocusRequester,
                    minLines = textFieldMinLines,
                    maxLines = textFieldMaxLines,
                    minHeight = textFieldMinHeight
                )
            }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
private fun PcLiveTypingEditor(
    enabled: Boolean,
    paused: Boolean,
    message: String?,
    announcement: String?,
    sessionText: String,
    onSessionTextChanged: (String) -> Unit,
    onTextCommitted: (String) -> Boolean,
    onKeyCommitted: (PcKeyboardKey) -> Unit,
    onRetry: () -> Unit,
    onMoveToDraft: () -> Unit,
    onClearField: () -> Unit,
    onAnnouncementShown: () -> Unit,
    fieldFocusRequester: FocusRequester,
    minHeight: Dp
) {
    val context = LocalContext.current
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val hintColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    val focusedColor = MaterialTheme.colorScheme.primary.toArgb()
    val unfocusedColor = MaterialTheme.colorScheme.outline.toArgb()
    val hint = stringResource(R.string.pc_typing_start_typing)
    val pausedDescription = stringResource(R.string.pc_typing_live_field_paused)
    val liveDescription = stringResource(R.string.pc_typing_live_status_line)
    val liveEditText = remember { AtomicReference<PcLiveTypingEditText?>() }

    DisposableEffect(Unit) {
        onDispose {
            val retainedText = liveEditText.get()
                ?.finishCompositionAndTakeSessionText()
                .orEmpty()
            liveEditText.set(null)
            onSessionTextChanged(retainedText)
        }
    }
    LaunchedEffect(announcement) {
        if (announcement != null) {
            liveEditText.get()?.let {
                it.requestFocus()
                it.announceForAccessibility(announcement)
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
                ViewCompat.setStateDescription(
                    editText,
                    if (paused) pausedDescription else liveDescription
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = minHeight)
                .focusRequester(fieldFocusRequester)
        )
        if (!paused) {
            Text(
                text = stringResource(R.string.pc_typing_live_status_line),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        announcement?.let {
            Text(
                text = it,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
        if (paused) {
            Text(
                text = stringResource(R.string.pc_typing_paused),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive }
            )
            Text(
                text = stringResource(R.string.pc_typing_text_safe),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            message?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            Button(
                onClick = {
                    onRetry()
                    liveEditText.get()?.retryCommittedText()
                },
                enabled = enabled,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            ) {
                Text(stringResource(R.string.pc_typing_exit_retry))
            }
            OutlinedButton(
                onClick = onMoveToDraft,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            ) {
                Text(stringResource(R.string.pc_typing_save_as_draft))
            }
        } else {
            message?.let {
                Text(text = it, color = MaterialTheme.colorScheme.error)
            }
            if (sessionText.isNotEmpty()) {
                OutlinedButton(
                    onClick = onClearField,
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                ) {
                    Text(stringResource(R.string.pc_typing_live_clear_field))
                }
            }
        }
    }
}

@Composable
private fun PcDraftTypingEditor(
    text: String,
    message: String?,
    draftReviewWarning: Boolean,
    pressEnterAfterSending: Boolean,
    liveTypingAvailable: Boolean,
    enabled: Boolean,
    onTextChanged: (String) -> Unit,
    onPressEnterChanged: (Boolean) -> Unit,
    onSend: () -> Unit,
    onClear: () -> Unit,
    onTypeLive: () -> Unit,
    onOpenKeys: () -> Unit,
    fieldFocusRequester: FocusRequester,
    minLines: Int,
    maxLines: Int,
    minHeight: Dp
) {
    val sendEnabled = enabled && text.isNotEmpty() && isSafePcTypedText(text)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SwitchifyTextField(
            value = text,
            onValueChange = onTextChanged,
            enabled = enabled,
            minLines = minLines,
            maxLines = maxLines,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.None),
            label = { Text(stringResource(R.string.pc_typing_draft_field_label)) },
            isError = message != null,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = minHeight)
                .focusRequester(fieldFocusRequester)
        )
        Text(
            text = stringResource(R.string.pc_typing_nothing_sent),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (draftReviewWarning) {
            Text(
                text = stringResource(R.string.pc_typing_draft_review_warning),
                color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive }
            )
        }
        message?.let {
            Text(text = it, color = MaterialTheme.colorScheme.error)
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable(enabled = enabled) {
                    onPressEnterChanged(!pressEnterAfterSending)
                }
                .semantics { role = Role.Checkbox },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = pressEnterAfterSending,
                onCheckedChange = onPressEnterChanged,
                enabled = enabled
            )
            Text(stringResource(R.string.pc_typing_press_enter_after_sending))
        }
        Button(
            onClick = onSend,
            enabled = sendEnabled,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
        ) {
            Text(stringResource(R.string.pc_typing_send))
        }
        if (text.isNotEmpty()) {
            OutlinedButton(
                onClick = onClear,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            ) {
                Text(stringResource(R.string.pc_typing_clear_draft))
            }
        }
        OutlinedButton(
            onClick = onTypeLive,
            enabled = enabled && liveTypingAvailable,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
        ) {
            Text(stringResource(R.string.pc_typing_type_live_instead))
        }
        if (!liveTypingAvailable) {
            Text(
                text = stringResource(R.string.pc_typing_live_unavailable),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        OutlinedButton(
            onClick = onOpenKeys,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
        ) {
            Text(stringResource(R.string.pc_typing_pc_keys))
        }
    }
}

@Composable
internal fun PcTypingKeysPage(
    enabled: Boolean,
    onBackToTyping: () -> Unit,
    onKeySelected: (PcKeyboardKey) -> Unit,
    modifier: Modifier = Modifier,
    onOpenAllTypingControls: (() -> Unit)? = null
) {
    var moreKeysVisible by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(R.string.pc_typing_pc_keys),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { heading() }
        )
        OutlinedButton(
            onClick = onBackToTyping,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
        ) {
            Text(stringResource(R.string.pc_typing_back_to_typing))
        }
        PcTypingKeyGrid(
            specs = pcEditingKeySpecs() + pcSpacingKeySpecs(),
            enabled = enabled,
            onKeySelected = onKeySelected,
            columns = 3
        )
        PcTypingKeyGrid(
            specs = pcCursorKeySpecs(),
            enabled = enabled,
            onKeySelected = onKeySelected,
            columns = 4
        )
        OutlinedButton(
            onClick = { moreKeysVisible = !moreKeysVisible },
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
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
            PcTypingKeyGrid(
                specs = pcDocumentKeySpecs(),
                enabled = enabled,
                onKeySelected = onKeySelected,
                columns = 4
            )
            PcTypingKeyGrid(
                specs = pcFunctionKeySpecs(),
                enabled = enabled,
                onKeySelected = onKeySelected,
                columns = 4
            )
        }
        onOpenAllTypingControls?.let { open ->
            OutlinedButton(
                onClick = open,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            ) {
                Text(stringResource(R.string.pc_control_quick_input_open_full))
            }
        }
    }
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

fun pcEditingKeySpecs(): List<PcTypingKeySpec> = listOf(
    PcTypingKeySpec(R.string.pc_key_backspace, PcKeyboardKey.Backspace),
    PcTypingKeySpec(R.string.pc_key_delete, PcKeyboardKey.Delete),
    PcTypingKeySpec(R.string.pc_key_enter, PcKeyboardKey.Enter)
)

fun pcSpacingKeySpecs(): List<PcTypingKeySpec> = listOf(
    PcTypingKeySpec(R.string.pc_key_space, PcKeyboardKey.Space),
    PcTypingKeySpec(R.string.pc_key_tab, PcKeyboardKey.Tab),
    PcTypingKeySpec(R.string.pc_key_escape, PcKeyboardKey.Escape)
)

fun pcCursorKeySpecs(): List<PcTypingKeySpec> = listOf(
    PcTypingKeySpec(R.string.pc_key_arrow_left, PcKeyboardKey.ArrowLeft),
    PcTypingKeySpec(R.string.pc_key_arrow_up, PcKeyboardKey.ArrowUp),
    PcTypingKeySpec(R.string.pc_key_arrow_down, PcKeyboardKey.ArrowDown),
    PcTypingKeySpec(R.string.pc_key_arrow_right, PcKeyboardKey.ArrowRight)
)

fun pcDocumentKeySpecs(): List<PcTypingKeySpec> = listOf(
    PcTypingKeySpec(R.string.pc_key_home, PcKeyboardKey.Home),
    PcTypingKeySpec(R.string.pc_key_end, PcKeyboardKey.End),
    PcTypingKeySpec(R.string.pc_key_page_up, PcKeyboardKey.PageUp),
    PcTypingKeySpec(R.string.pc_key_page_down, PcKeyboardKey.PageDown)
)

fun pcFunctionKeySpecs(): List<PcTypingKeySpec> = listOf(
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
