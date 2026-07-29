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
import androidx.compose.foundation.focusable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardHide
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.enaboapps.switchify.R
import com.enaboapps.switchify.pc.PcKeyboardKey

internal enum class PcQuickInputPage {
    Type,
    Keys
}

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
            Icon(
                imageVector = Icons.Default.Keyboard,
                contentDescription = null
            )
            Text(
                text = stringResource(R.string.pc_control_quick_input),
                modifier = Modifier.padding(start = 8.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
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
    liveTypingAvailable: Boolean = false,
    liveTypingPaused: Boolean = false,
    liveTypingMessage: String? = null,
    liveTypingAnnouncement: String? = null,
    liveTypingText: String = "",
    connected: Boolean,
    enabled: Boolean,
    onTypingModeSelected: (PcTypingMode) -> Unit = {},
    onTextChanged: (String) -> Unit,
    onSend: () -> Unit,
    onSendAndEnter: () -> Unit,
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
    onKeySelected: (PcKeyboardKey) -> Unit,
    onOpenFullTypingControls: () -> Unit = {},
    onDismissRequest: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val dismiss = {
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
        onDismissRequest()
    }

    ModalBottomSheet(
        onDismissRequest = dismiss,
        sheetState = sheetState
    ) {
        PcQuickInputContent(
            typingText = typingText,
            typingMessage = typingMessage,
            typingDraftReviewWarning = typingDraftReviewWarning,
            typingMode = typingMode,
            liveTypingAvailable = liveTypingAvailable,
            liveTypingPaused = liveTypingPaused,
            liveTypingMessage = liveTypingMessage,
            liveTypingAnnouncement = liveTypingAnnouncement,
            liveTypingText = liveTypingText,
            connected = connected,
            enabled = enabled,
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
            onKeySelected = onKeySelected,
            onOpenFullTypingControls = onOpenFullTypingControls,
            onClose = dismiss
        )
    }
}

@Composable
internal fun PcQuickInputContent(
    typingText: String,
    typingMessage: String?,
    typingDraftReviewWarning: Boolean = false,
    typingMode: PcTypingMode = PcTypingMode.Draft,
    liveTypingAvailable: Boolean = false,
    liveTypingPaused: Boolean = false,
    liveTypingMessage: String? = null,
    liveTypingAnnouncement: String? = null,
    liveTypingText: String = "",
    connected: Boolean,
    enabled: Boolean,
    onTypingModeSelected: (PcTypingMode) -> Unit = {},
    onTextChanged: (String) -> Unit,
    onSend: () -> Unit,
    onSendAndEnter: () -> Unit,
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
    onKeySelected: (PcKeyboardKey) -> Unit,
    onOpenFullTypingControls: () -> Unit = {},
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusRequester = remember { FocusRequester() }
    val pageHeadingFocusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val typeScrollState = rememberScrollState()
    val keysScrollState = rememberScrollState()
    var page by rememberSaveable { mutableStateOf(PcQuickInputPage.Type) }
    val ownsLiveSession = typingMode == PcTypingMode.Live && liveTypingAvailable
    val latestLiveTypingText by rememberUpdatedState(liveTypingText)

    DisposableEffect(ownsLiveSession) {
        if (ownsLiveSession) {
            onLiveTypingStarted()
        }
        onDispose {
            if (ownsLiveSession) {
                onLiveTypingStopped(latestLiveTypingText)
            }
        }
    }

    LaunchedEffect(liveTypingPaused) {
        if (liveTypingPaused) {
            page = PcQuickInputPage.Type
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
                text = stringResource(R.string.pc_control_quick_input_title),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            IconButton(
                onClick = {
                    focusManager.clearFocus(force = true)
                    keyboardController?.hide()
                }
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardHide,
                    contentDescription = stringResource(
                        R.string.pc_control_quick_input_hide_keyboard
                    )
                )
            }
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.pc_control_quick_input_close)
                )
            }
        }
        Text(
            text = stringResource(
                if (page == PcQuickInputPage.Type) {
                    R.string.pc_control_quick_input_type_page
                } else {
                    R.string.pc_control_quick_input_keys_page
                }
            ),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier
                .focusRequester(pageHeadingFocusRequester)
                .focusable()
                .semantics {
                    heading()
                    liveRegion = LiveRegionMode.Polite
                }
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(
                    if (page == PcQuickInputPage.Type) {
                        typeScrollState
                    } else {
                        keysScrollState
                    }
                ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (page == PcQuickInputPage.Type) {
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
                    enabled = enabled,
                    textFieldModifier = Modifier.focusRequester(focusRequester),
                    textFieldMinLines = 2,
                    textFieldMaxLines = 3,
                    textFieldMinHeight = 72.dp,
                    manageLiveSessionLifecycle = false
                )
            } else {
                PcTypingKeyGrid(
                    specs = pcEditingKeySpecs() + pcSpacingKeySpecs(),
                    enabled = enabled && !(typingMode == PcTypingMode.Live && liveTypingPaused),
                    onKeySelected = onKeySelected,
                    columns = 3
                )
                PcTypingKeyGrid(
                    specs = pcCursorKeySpecs(),
                    enabled = enabled && !(typingMode == PcTypingMode.Live && liveTypingPaused),
                    onKeySelected = onKeySelected,
                    columns = 4
                )
                OutlinedButton(
                    onClick = onOpenFullTypingControls,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                ) {
                    Text(stringResource(R.string.pc_control_quick_input_open_full))
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = {
                    page = PcQuickInputPage.Type
                },
                enabled = page == PcQuickInputPage.Keys,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowUp,
                    contentDescription = null
                )
                Text(stringResource(R.string.pc_control_quick_input_previous_controls))
            }
            OutlinedButton(
                onClick = {
                    focusManager.clearFocus(force = true)
                    keyboardController?.hide()
                    page = PcQuickInputPage.Keys
                },
                enabled = page == PcQuickInputPage.Type,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
            ) {
                Text(stringResource(R.string.pc_control_quick_input_next_controls))
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = null
                )
            }
        }
    }

    LaunchedEffect(connected, page, liveTypingPaused) {
        if (page == PcQuickInputPage.Keys || liveTypingPaused) {
            pageHeadingFocusRequester.requestFocus()
        } else if (connected) {
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }
}
