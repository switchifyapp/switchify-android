package com.enaboapps.switchify.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.enaboapps.switchify.R

@Composable
fun PreferenceValueSelector(
    value: Int,
    titleResId: Int,
    summaryResId: Int,
    min: Int? = null,
    max: Int? = null,
    values: IntArray? = null,
    buttonLabelFormatter: (Int) -> String = { it.toString() },
    displayFormatter: (Int) -> String = { it.toString() },
    onValueChanged: (Int) -> Unit
) {
    require((min != null && max != null) || values != null) {
        "Either min/max or values array must be provided"
    }

    var currentValue by remember(value) { mutableStateOf(value) }
    var showDialog by remember { mutableStateOf(false) }

    PreferenceComponentBase(
        titleResId = titleResId,
        summaryResId = summaryResId,
        onClick = { showDialog = true },
        trailing = {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = displayFormatter(currentValue),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 128.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    )

    if (showDialog) {
        val valuesToDisplay = if (values != null) {
            values.toList()
        } else {
            (min!!..max!!).toList()
        }

        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(text = stringResource(titleResId)) },
            text = {
                LazyColumn {
                    val groupedValues = valuesToDisplay.chunked(2)
                    items(groupedValues) { rowValues ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            rowValues.forEach { i ->
                                OutlinedButton(
                                    onClick = {
                                        currentValue = i
                                        onValueChanged(i)
                                        showDialog = false
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = 2.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = if (currentValue == i)
                                            MaterialTheme.colorScheme.primaryContainer
                                        else
                                            MaterialTheme.colorScheme.surface,
                                        contentColor = if (currentValue == i)
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        else
                                            MaterialTheme.colorScheme.onSurface
                                    )
                                ) {
                                    Text(
                                        text = buttonLabelFormatter(i),
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                            }
                            repeat(2 - rowValues.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text(stringResource(R.string.button_done))
                }
            }
        )
    }
}
