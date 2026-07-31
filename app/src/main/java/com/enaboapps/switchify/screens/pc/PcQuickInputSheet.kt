package com.enaboapps.switchify.screens.pc

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.KeyboardHide
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.enaboapps.switchify.R
import com.enaboapps.switchify.pc.PcKeyboardKey

internal fun shouldShowPcQuickInputButton(surface: PcControlSurface): Boolean {
    return surface == PcControlSurface.Mouse || surface == PcControlSurface.Window
}

@Composable
fun PcQuickInputButton(
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        FilledTonalButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .fillMaxWidth()
                .heightIn(min = 48.dp),
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Icon(imageVector = Icons.Default.Keyboard, contentDescription = null)
            Text(
                text = stringResource(R.string.pc_control_quick_input),
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PcQuickInputSheet(
    typingText: String,
    typingMessage: String?,
    typingDraftReviewWarning: Boolean = false,
    typingMode: PcTypingMode = PcTypingMode.Draft,
    draftPressEnterAfterSending: Boolean = false,
    liveTypingAvailable: Boolean = false,
    liveTypingPaused: Boolean = false,
    liveTypingMessage: String? = null,
    liveTypingAnnouncement: String? = null,
    liveTypingNotice: String? = null,
    liveTypingText: String = "",
    connected: Boolean,
    enabled: Boolean,
    onTypingModeSelected: (PcTypingMode) -> Unit = {},
    onDraftEnterChanged: (Boolean) -> Unit = {},
    onTextChanged: (String) -> Unit,
    onSend: () -> Unit,
    onSendAndEnter: () -> Unit = {},
    onClear: () -> Unit,
    onClearLiveField: () -> Unit = {},
    onLiveTypingStarted: () -> Unit = {},
    onLiveTypingStopped: (String) -> Unit = { _ -> },
    onLiveSessionTextChanged: (String) -> Unit = {},
    onLiveTextCommitted: (String) -> Boolean = { false },
    onLiveKeyCommitted: (PcKeyboardKey) -> Unit = {},
    onLiveTypingRetry: () -> Unit = {},
    onMoveLiveToDraft: () -> Unit = {},
    onLiveTypingAnnouncementShown: () -> Unit = {},
    onLiveTypingNoticeShown: () -> Unit = {},
    onKeySelected: (PcKeyboardKey) -> Unit,
    onOpenFullTypingControls: () -> Unit = {},
    onDismissRequest: () -> Unit,
    manageLiveSessionLifecycle: Boolean = true
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val dismiss = {
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
        onDismissRequest()
    }
    ModalBottomSheet(onDismissRequest = dismiss, sheetState = sheetState) {
        PcQuickInputContent(
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
            connected = connected,
            enabled = enabled,
            onTypingModeSelected = onTypingModeSelected,
            onDraftEnterChanged = onDraftEnterChanged,
            onTextChanged = onTextChanged,
            onSend = onSend,
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
            onLiveTypingNoticeShown = onLiveTypingNoticeShown,
            onKeySelected = onKeySelected,
            onOpenFullTypingControls = onOpenFullTypingControls,
            onClose = dismiss,
            manageLiveSessionLifecycle = manageLiveSessionLifecycle
        )
    }
}

@Composable
internal fun PcQuickInputContent(
    typingText: String,
    typingMessage: String?,
    typingDraftReviewWarning: Boolean = false,
    typingMode: PcTypingMode = PcTypingMode.Draft,
    draftPressEnterAfterSending: Boolean = false,
    liveTypingAvailable: Boolean = false,
    liveTypingPaused: Boolean = false,
    liveTypingMessage: String? = null,
    liveTypingAnnouncement: String? = null,
    liveTypingNotice: String? = null,
    liveTypingText: String = "",
    connected: Boolean,
    enabled: Boolean,
    onTypingModeSelected: (PcTypingMode) -> Unit = {},
    onDraftEnterChanged: (Boolean) -> Unit = {},
    onTextChanged: (String) -> Unit,
    onSend: () -> Unit,
    onSendAndEnter: () -> Unit = {},
    onClear: () -> Unit,
    onClearLiveField: () -> Unit = {},
    onLiveTypingStarted: () -> Unit = {},
    onLiveTypingStopped: (String) -> Unit = { _ -> },
    onLiveSessionTextChanged: (String) -> Unit = {},
    onLiveTextCommitted: (String) -> Boolean = { false },
    onLiveKeyCommitted: (PcKeyboardKey) -> Unit = {},
    onLiveTypingRetry: () -> Unit = {},
    onMoveLiveToDraft: () -> Unit = {},
    onLiveTypingAnnouncementShown: () -> Unit = {},
    onLiveTypingNoticeShown: () -> Unit = {},
    onKeySelected: (PcKeyboardKey) -> Unit,
    onOpenFullTypingControls: () -> Unit = {},
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    manageLiveSessionLifecycle: Boolean = true
) {
    var page by rememberSaveable { mutableStateOf(PcTypingPage.Editor) }
    var clearLiveTextOnKeysEntry by remember { mutableStateOf(false) }
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
    LaunchedEffect(page, clearLiveTextOnKeysEntry) {
        if (page == PcTypingPage.Keys && clearLiveTextOnKeysEntry) {
            onClearLiveField()
            clearLiveTextOnKeysEntry = false
        }
    }
    LaunchedEffect(liveTypingPaused) {
        if (liveTypingPaused) page = PcTypingPage.Editor
    }
    LaunchedEffect(connected, page, liveTypingPaused) {
        if (connected && page == PcTypingPage.Editor && !liveTypingPaused) {
            fieldFocusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight(0.9f)
            .imePadding()
            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.pc_control_quick_type),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleLarge
            )
            IconButton(
                onClick = {
                    focusManager.clearFocus(force = true)
                    keyboardController?.hide()
                }
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardHide,
                    contentDescription = stringResource(R.string.pc_control_quick_input_hide_keyboard)
                )
            }
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.pc_control_quick_input_close)
                )
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (page == PcTypingPage.Editor) {
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
                    onSendDraft = onSend,
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
                        clearLiveTextOnKeysEntry = ownsLiveSession
                        page = PcTypingPage.Keys
                    },
                    fieldFocusRequester = fieldFocusRequester,
                    textFieldMinLines = 2,
                    textFieldMaxLines = 3,
                    textFieldMinHeight = 72.dp
                )
            } else {
                PcTypingKeysPage(
                    enabled = enabled,
                    onBackToTyping = {
                        page = PcTypingPage.Editor
                    },
                    onKeySelected = onKeySelected,
                    onOpenAllTypingControls = onOpenFullTypingControls
                )
            }
        }
    }
}
