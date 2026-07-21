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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.enaboapps.switchify.R
import com.enaboapps.switchify.pc.PcKeyboardKey
import kotlinx.coroutines.launch

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
    connected: Boolean,
    enabled: Boolean,
    onTextChanged: (String) -> Unit,
    onSend: () -> Unit,
    onSendAndEnter: () -> Unit,
    onClear: () -> Unit,
    onKeySelected: (PcKeyboardKey) -> Unit,
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
            connected = connected,
            enabled = enabled,
            onTextChanged = onTextChanged,
            onSend = onSend,
            onSendAndEnter = onSendAndEnter,
            onClear = onClear,
            onKeySelected = onKeySelected,
            onClose = dismiss
        )
    }
}

@Composable
internal fun PcQuickInputContent(
    typingText: String,
    typingMessage: String?,
    connected: Boolean,
    enabled: Boolean,
    onTextChanged: (String) -> Unit,
    onSend: () -> Unit,
    onSendAndEnter: () -> Unit,
    onClear: () -> Unit,
    onKeySelected: (PcKeyboardKey) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()

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
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PcTypingComposer(
                text = typingText,
                message = typingMessage,
                onTextChanged = onTextChanged,
                onSend = onSend,
                onSendAndEnter = onSendAndEnter,
                onClear = onClear,
                enabled = enabled,
                textFieldModifier = Modifier.focusRequester(focusRequester),
                textFieldMinLines = 2,
                textFieldMaxLines = 3,
                textFieldMinHeight = 72.dp
            )
            PcKeyboardNavigationCluster(
                enabled = enabled,
                onKeySelected = onKeySelected
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = {
                    coroutineScope.launch {
                        scrollState.animateScrollTo(
                            (scrollState.value - scrollState.viewportSize).coerceAtLeast(0)
                        )
                    }
                },
                enabled = scrollState.value > 0,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowUp,
                    contentDescription = null
                )
                Text(stringResource(R.string.pc_control_quick_input_previous_controls))
            }
            OutlinedButton(
                onClick = {
                    coroutineScope.launch {
                        scrollState.animateScrollTo(
                            (scrollState.value + scrollState.viewportSize)
                                .coerceAtMost(scrollState.maxValue)
                        )
                    }
                },
                enabled = scrollState.value < scrollState.maxValue,
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(R.string.pc_control_quick_input_next_controls))
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = null
                )
            }
        }
    }

    LaunchedEffect(connected) {
        if (connected) {
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }
}
