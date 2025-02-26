package com.enaboapps.switchify.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.enaboapps.switchify.R

@Composable
fun <T> Picker(
    titleResId: Int,
    titleResIdArgs: Array<Any>? = null,
    selectedItem: T?,
    items: List<T>,
    modifier: Modifier = Modifier,
    onItemSelected: (T) -> Unit,
    onDelete: (() -> Unit)? = null,
    itemToString: (T) -> String,
    itemDescription: (T) -> String
) {
    var expanded by remember { mutableStateOf(false) }

    val title = if (titleResIdArgs != null) {
        stringResource(titleResId, *titleResIdArgs)
    } else {
        stringResource(titleResId)
    }

    PickerItem(
        title = title,
        description = if (selectedItem != null) itemToString(selectedItem) else "",
        extraDescription = if (selectedItem != null) itemDescription(selectedItem) else "",
        onDelete = onDelete,
        onClick = {
            expanded = true
        }
    )

    if (expanded) {
        DropdownMenu(
            modifier = modifier,
            expanded = true,
            onDismissRequest = { expanded = false }
        ) {
            items.forEach { item ->
                DropdownMenuItem(
                    text = { Text(text = itemToString(item)) },
                    onClick = {
                        onItemSelected(item)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun PickerItem(
    title: String,
    description: String,
    extraDescription: String,
    onDelete: (() -> Unit)?,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clickable(onClick = {
                onClick()
            })
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(20.dp)
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                Text(
                    text = title.uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = extraDescription,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            if (onDelete != null) {
                TextButton(
                    onClick = onDelete
                ) {
                    Text(text = stringResource(R.string.button_delete))
                }
            }

            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier.padding(start = 16.dp)
            )
        }
    }
}
