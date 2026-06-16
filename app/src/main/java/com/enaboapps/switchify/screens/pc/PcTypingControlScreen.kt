package com.enaboapps.switchify.screens.pc

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.enaboapps.switchify.R
import com.enaboapps.switchify.pc.PcKeyboardKey
import com.enaboapps.switchify.pc.isSafePcTypedText

data class PcTypingKeySpec(
    @param:StringRes val labelResId: Int,
    val key: PcKeyboardKey
)

sealed class PcTypingCompactCommandSpec {
    data object Send : PcTypingCompactCommandSpec()
    data object Clear : PcTypingCompactCommandSpec()
    data class Key(val spec: PcTypingKeySpec) : PcTypingCompactCommandSpec()
}

@Composable
fun PcTypingControlScreen(
    typingText: String,
    typingMessage: String?,
    enabled: Boolean,
    onTextChanged: (String) -> Unit,
    onSend: () -> Unit,
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
            enabled = enabled
        )
        PcTypingCompactCommandGrid(
            sendEnabled = enabled && typingText.isNotEmpty() && isSafePcTypedText(typingText),
            clearEnabled = enabled && typingText.isNotEmpty(),
            keysEnabled = enabled,
            onSend = onSend,
            onClear = onClear,
            onKeySelected = onKeySelected
        )
    }
}

@Composable
private fun PcTypingTextSection(
    text: String,
    message: String?,
    onTextChanged: (String) -> Unit,
    enabled: Boolean
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PcTypingSectionTitle(R.string.pc_typing_section_text)
        OutlinedTextField(
            value = text,
            onValueChange = onTextChanged,
            enabled = enabled,
            label = { Text(stringResource(R.string.pc_typing_text_label)) },
            minLines = 3,
            maxLines = 5,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.None),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 96.dp)
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
private fun PcTypingCompactCommandGrid(
    sendEnabled: Boolean,
    clearEnabled: Boolean,
    keysEnabled: Boolean,
    onSend: () -> Unit,
    onClear: () -> Unit,
    onKeySelected: (PcKeyboardKey) -> Unit
) {
    PcCompactCommandGrid(
        columns = 4,
        minTileHeightDp = 52,
        cells = pcTypingCompactCommandSpecs().map { spec ->
            when (spec) {
                PcTypingCompactCommandSpec.Send -> PcCompactCommandCell(
                    labelResId = R.string.pc_typing_send,
                    enabled = sendEnabled,
                    onClick = onSend
                )
                PcTypingCompactCommandSpec.Clear -> PcCompactCommandCell(
                    labelResId = R.string.pc_typing_clear,
                    enabled = clearEnabled,
                    onClick = onClear
                )
                is PcTypingCompactCommandSpec.Key -> PcCompactCommandCell(
                    labelResId = spec.spec.labelResId,
                    enabled = keysEnabled,
                    onClick = { onKeySelected(spec.spec.key) }
                )
            }
        }
    )
}

@Composable
private fun PcTypingSectionTitle(@StringRes titleResId: Int) {
    Text(
        text = stringResource(titleResId),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface
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
        PcTypingCompactCommandSpec.Send,
        PcTypingCompactCommandSpec.Clear,
        PcTypingCompactCommandSpec.Key(PcTypingKeySpec(R.string.pc_key_backspace, PcKeyboardKey.Backspace)),
        PcTypingCompactCommandSpec.Key(PcTypingKeySpec(R.string.pc_key_delete, PcKeyboardKey.Delete)),
        PcTypingCompactCommandSpec.Key(PcTypingKeySpec(R.string.pc_key_enter, PcKeyboardKey.Enter)),
        PcTypingCompactCommandSpec.Key(PcTypingKeySpec(R.string.pc_key_space, PcKeyboardKey.Space)),
        PcTypingCompactCommandSpec.Key(PcTypingKeySpec(R.string.pc_key_tab, PcKeyboardKey.Tab)),
        PcTypingCompactCommandSpec.Key(PcTypingKeySpec(R.string.pc_key_escape, PcKeyboardKey.Escape)),
        PcTypingCompactCommandSpec.Key(PcTypingKeySpec(R.string.pc_key_arrow_left, PcKeyboardKey.ArrowLeft)),
        PcTypingCompactCommandSpec.Key(PcTypingKeySpec(R.string.pc_key_arrow_up, PcKeyboardKey.ArrowUp)),
        PcTypingCompactCommandSpec.Key(PcTypingKeySpec(R.string.pc_key_arrow_down, PcKeyboardKey.ArrowDown)),
        PcTypingCompactCommandSpec.Key(PcTypingKeySpec(R.string.pc_key_arrow_right, PcKeyboardKey.ArrowRight)),
        PcTypingCompactCommandSpec.Key(PcTypingKeySpec(R.string.pc_key_home, PcKeyboardKey.Home)),
        PcTypingCompactCommandSpec.Key(PcTypingKeySpec(R.string.pc_key_end, PcKeyboardKey.End)),
        PcTypingCompactCommandSpec.Key(PcTypingKeySpec(R.string.pc_key_page_up, PcKeyboardKey.PageUp)),
        PcTypingCompactCommandSpec.Key(PcTypingKeySpec(R.string.pc_key_page_down, PcKeyboardKey.PageDown))
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
