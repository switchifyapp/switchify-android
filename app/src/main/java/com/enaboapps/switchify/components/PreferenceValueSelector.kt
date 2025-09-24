package com.enaboapps.switchify.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.enaboapps.switchify.theme.Dimens

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

    var currentValue by remember { mutableStateOf(value) }
    var showDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = Dimens.spaceXs)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimens.spaceM)
        ) {
            // Main content row with click handler
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showDialog = true }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = stringResource(titleResId).uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = stringResource(summaryResId),
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                // Current value display with dropdown indicator
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = displayFormatter(currentValue),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.width(4.dp))

                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Select value",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    // Value selection dialog
    if (showDialog) {
        val valuesToDisplay = if (values != null) {
            values.toList()
        } else {
            (min!!..max!!).toList()
        }

        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = {
                Text(text = stringResource(titleResId))
            },
            text = {
                LazyColumn {
                    // Group values into rows of 3
                    val groupedValues = valuesToDisplay.chunked(3)
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
                                    modifier = Modifier.weight(1f).padding(horizontal = 2.dp),
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
                            // Fill remaining slots if row has fewer than 3 items
                            repeat(3 - rowValues.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { showDialog = false }
                ) {
                    Text("Done")
                }
            }
        )
    }
}
