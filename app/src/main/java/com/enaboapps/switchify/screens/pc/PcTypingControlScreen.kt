package com.enaboapps.switchify.screens.pc

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.automirrored.filled.KeyboardReturn
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.enaboapps.switchify.R
import com.enaboapps.switchify.pc.PcKeyboardKey
import com.enaboapps.switchify.pc.isSafePcTypedText

data class PcTypingKeySpec(
    @param:StringRes val labelResId: Int,
    val key: PcKeyboardKey
)

sealed class PcTypingCompactCommandSpec {
    data class Key(val spec: PcTypingKeySpec) : PcTypingCompactCommandSpec()
}

enum class PcTypingTextAction {
    Send,
    SendAndEnter,
    Clear
}

@Composable
fun PcTypingControlScreen(
    typingText: String,
    typingMessage: String?,
    enabled: Boolean,
    onTextChanged: (String) -> Unit,
    onSend: () -> Unit,
    onSendAndEnter: () -> Unit,
    onClear: () -> Unit,
    onKeySelected: (PcKeyboardKey) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        PcTypingTextSection(
            text = typingText,
            message = typingMessage,
            onTextChanged = onTextChanged,
            onSend = onSend,
            onSendAndEnter = onSendAndEnter,
            onClear = onClear,
            enabled = enabled
        )
        PcTypingCompactCommandGrid(
            keysEnabled = enabled,
            onKeySelected = onKeySelected
        )
        PcKeyboardNavigationCluster(
            enabled = enabled,
            onKeySelected = onKeySelected
        )
    }
}

@Composable
private fun PcTypingTextSection(
    text: String,
    message: String?,
    onTextChanged: (String) -> Unit,
    onSend: () -> Unit,
    onSendAndEnter: () -> Unit,
    onClear: () -> Unit,
    enabled: Boolean
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PcTypingTextBox(
            text = text,
            message = message,
            enabled = enabled,
            sendEnabled = enabled && text.isNotEmpty() && isSafePcTypedText(text),
            clearEnabled = enabled && text.isNotEmpty(),
            onTextChanged = onTextChanged,
            onSend = onSend,
            onSendAndEnter = onSendAndEnter,
            onClear = onClear
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
    enabled: Boolean,
    sendEnabled: Boolean,
    clearEnabled: Boolean,
    onTextChanged: (String) -> Unit,
    onSend: () -> Unit,
    onSendAndEnter: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = when {
        message != null -> MaterialTheme.colorScheme.error
        enabled -> MaterialTheme.colorScheme.outline
        else -> MaterialTheme.colorScheme.outlineVariant
    }
    val textColor = if (enabled) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = stringResource(R.string.pc_typing_text_label),
                style = MaterialTheme.typography.labelLarge,
                color = if (message != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
            )
            BasicTextField(
                value = text,
                onValueChange = onTextChanged,
                enabled = enabled,
                minLines = 3,
                maxLines = 5,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.None),
                textStyle = MaterialTheme.typography.bodyLarge.merge(TextStyle(color = textColor)),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 96.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
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
                        modifier = Modifier.weight(1f)
                    )
                }
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

    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 48.dp),
        shape = RoundedCornerShape(8.dp)
    ) {
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
}

@Composable
private fun PcTypingCompactCommandGrid(
    keysEnabled: Boolean,
    onKeySelected: (PcKeyboardKey) -> Unit
) {
    PcCompactCommandGrid(
        columns = 4,
        minTileHeightDp = 52,
        cells = pcTypingCompactCommandSpecs().map { spec ->
            when (spec) {
                is PcTypingCompactCommandSpec.Key -> PcCompactCommandCell(
                    labelResId = spec.spec.labelResId,
                    enabled = keysEnabled,
                    onClick = { onKeySelected(spec.spec.key) }
                )
            }
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

fun pcTypingCompactCommandSpecs(): List<PcTypingCompactCommandSpec> {
    return listOf(
        PcTypingCompactCommandSpec.Key(PcTypingKeySpec(R.string.pc_key_backspace, PcKeyboardKey.Backspace)),
        PcTypingCompactCommandSpec.Key(PcTypingKeySpec(R.string.pc_key_delete, PcKeyboardKey.Delete)),
        PcTypingCompactCommandSpec.Key(PcTypingKeySpec(R.string.pc_key_space, PcKeyboardKey.Space)),
        PcTypingCompactCommandSpec.Key(PcTypingKeySpec(R.string.pc_key_tab, PcKeyboardKey.Tab)),
        PcTypingCompactCommandSpec.Key(PcTypingKeySpec(R.string.pc_key_home, PcKeyboardKey.Home)),
        PcTypingCompactCommandSpec.Key(PcTypingKeySpec(R.string.pc_key_end, PcKeyboardKey.End)),
        PcTypingCompactCommandSpec.Key(PcTypingKeySpec(R.string.pc_key_page_up, PcKeyboardKey.PageUp)),
        PcTypingCompactCommandSpec.Key(PcTypingKeySpec(R.string.pc_key_page_down, PcKeyboardKey.PageDown))
    ) + pcFunctionKeySpecs().map { PcTypingCompactCommandSpec.Key(it) }
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
