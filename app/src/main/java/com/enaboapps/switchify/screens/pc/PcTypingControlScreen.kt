package com.enaboapps.switchify.screens.pc

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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

data class PcTypingKeySpec(
    @param:StringRes val labelResId: Int,
    val key: PcKeyboardKey
)

@Composable
fun PcTypingControlScreen(
    typingText: String,
    typingMessage: String?,
    sendEnabled: Boolean,
    keysEnabled: Boolean,
    onTextChanged: (String) -> Unit,
    onSend: () -> Unit,
    onClear: () -> Unit,
    onKeySelected: (PcKeyboardKey) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        PcTypingTextSection(
            text = typingText,
            message = typingMessage,
            onTextChanged = onTextChanged
        )
        PcTypingActionSection(
            sendEnabled = sendEnabled,
            clearEnabled = typingText.isNotEmpty(),
            onSend = onSend,
            onClear = onClear
        )
        PcTypingKeySection(
            keysEnabled = keysEnabled,
            onKeySelected = onKeySelected
        )
    }
}

@Composable
private fun PcTypingTextSection(
    text: String,
    message: String?,
    onTextChanged: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PcTypingSectionTitle(R.string.pc_typing_section_text)
        OutlinedTextField(
            value = text,
            onValueChange = onTextChanged,
            label = { Text(stringResource(R.string.pc_typing_text_label)) },
            minLines = 4,
            maxLines = 8,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.None),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 132.dp)
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
private fun PcTypingActionSection(
    sendEnabled: Boolean,
    clearEnabled: Boolean,
    onSend: () -> Unit,
    onClear: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PcTypingSectionTitle(R.string.pc_typing_section_actions)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            PcScannedCommandTile(
                labelResId = R.string.pc_typing_send,
                enabled = sendEnabled,
                onClick = onSend,
                modifier = Modifier.weight(1f),
                minHeightDp = 72
            )
            PcScannedCommandTile(
                labelResId = R.string.pc_typing_clear,
                enabled = clearEnabled,
                onClick = onClear,
                modifier = Modifier.weight(1f),
                minHeightDp = 72
            )
        }
    }
}

@Composable
private fun PcTypingKeySection(
    keysEnabled: Boolean,
    onKeySelected: (PcKeyboardKey) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PcTypingSectionTitle(R.string.pc_typing_section_keys)
        PcTypingKeyRow(
            specs = pcEditingKeySpecs(),
            keysEnabled = keysEnabled,
            onKeySelected = onKeySelected
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            PcTypingKeyTile(
                spec = PcTypingKeySpec(R.string.pc_key_space, PcKeyboardKey.Space),
                keysEnabled = keysEnabled,
                onKeySelected = onKeySelected,
                modifier = Modifier.weight(2f)
            )
            PcTypingKeyTile(
                spec = PcTypingKeySpec(R.string.pc_key_tab, PcKeyboardKey.Tab),
                keysEnabled = keysEnabled,
                onKeySelected = onKeySelected,
                modifier = Modifier.weight(1f)
            )
            PcTypingKeyTile(
                spec = PcTypingKeySpec(R.string.pc_key_escape, PcKeyboardKey.Escape),
                keysEnabled = keysEnabled,
                onKeySelected = onKeySelected,
                modifier = Modifier.weight(1f)
            )
        }
        PcTypingKeyRow(
            specs = pcCursorKeySpecs(),
            keysEnabled = keysEnabled,
            onKeySelected = onKeySelected
        )
        PcTypingKeyRow(
            specs = pcDocumentKeySpecs(),
            keysEnabled = keysEnabled,
            onKeySelected = onKeySelected
        )
    }
}

@Composable
private fun PcTypingKeyRow(
    specs: List<PcTypingKeySpec>,
    keysEnabled: Boolean,
    onKeySelected: (PcKeyboardKey) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        specs.forEach { spec ->
            PcTypingKeyTile(
                spec = spec,
                keysEnabled = keysEnabled,
                onKeySelected = onKeySelected,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun PcTypingKeyTile(
    spec: PcTypingKeySpec,
    keysEnabled: Boolean,
    onKeySelected: (PcKeyboardKey) -> Unit,
    modifier: Modifier = Modifier
) {
    PcScannedCommandTile(
        labelResId = spec.labelResId,
        enabled = keysEnabled,
        onClick = { onKeySelected(spec.key) },
        modifier = modifier,
        minHeightDp = 64
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
