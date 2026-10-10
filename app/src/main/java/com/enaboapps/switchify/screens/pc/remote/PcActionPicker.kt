package com.enaboapps.switchify.screens.pc.remote

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.enaboapps.switchify.R
import com.enaboapps.switchify.components.ActionButton
import com.enaboapps.switchify.components.ActionButtonType
import com.enaboapps.switchify.pc.remote.layouts.PcActionOption
import com.enaboapps.switchify.pc.remote.layouts.PcActionPickerModel
import com.enaboapps.switchify.theme.Dimens

@Composable
fun PcActionPicker(
    row: Int,
    column: Int,
    options: List<PcActionOption>,
    onSelect: (String) -> Unit,
    onClose: () -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    var choosing by remember { mutableStateOf(false) }
    val filtered = PcActionPickerModel.search(options, query)
    val groups = PcActionPickerModel.group(filtered)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(Dimens.spaceM),
        verticalArrangement = Arrangement.spacedBy(Dimens.spaceXs)
    ) {
        Text(
            text = stringResource(R.string.pc_picker_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { heading() }
        )
        Text(
            text = stringResource(R.string.pc_layout_cell_heading, row, column),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text(stringResource(R.string.pc_picker_search)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Search),
            modifier = Modifier.fillMaxWidth()
        )
        groups.forEach { group ->
            Text(
                text = group.category,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(top = Dimens.spaceXs)
                    .semantics { heading() }
            )
            group.options.forEach { option ->
                OptionRow(option) {
                    if (!choosing) {
                        choosing = true
                        onSelect(option.id)
                    }
                }
            }
        }
        if (filtered.isEmpty()) {
            Text(
                text = stringResource(if (options.isEmpty()) R.string.pc_picker_all_placed else R.string.pc_picker_no_match),
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
            )
        }
        ActionButton(
            textResId = R.string.pc_picker_close,
            onClick = onClose,
            type = ActionButtonType.SECONDARY,
            leadingIcon = Icons.Rounded.Close,
            applyPadding = false,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun OptionRow(option: PcActionOption, onClick: () -> Unit) {
    val hint = option.explanation?.let { stringResource(R.string.pc_picker_unavailable_hint, it) }
        ?: stringResource(R.string.pc_picker_assign_hint)
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClickLabel = hint, role = Role.Button, onClick = onClick)
                .semantics(mergeDescendants = true) {}
                .padding(Dimens.spaceM),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(text = option.name, style = MaterialTheme.typography.labelLarge)
            option.explanation?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
