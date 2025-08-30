package com.enaboapps.switchify.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.enaboapps.switchify.utils.StringUtils

@Composable
fun PreferenceTimeStepper(
    value: Long,
    titleResId: Int,
    summaryResId: Int,
    explanationResId: Int? = null,
    min: Long,
    max: Long,
    step: Long = 100, // Default step is 100
    onValueChanged: (Long) -> Unit
) {
    var time by remember { mutableLongStateOf(value) }

    PreferenceComponentBase(
        titleResId = titleResId,
        summaryResId = summaryResId,
        explanationResId = explanationResId
    ) {
        Column(
            modifier = Modifier.wrapContentWidth(Alignment.End),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Value display with improved typography
            Text(
                text = StringUtils.getSecondsString(time),
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Normal
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Icon buttons for increment/decrement
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        if (time > min) {
                            time -= step
                            onValueChanged(time)
                        }
                    },
                    enabled = time > min,
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                    ),
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Remove,
                        contentDescription = "Decrease",
                        modifier = Modifier.size(24.dp)
                    )
                }

                IconButton(
                    onClick = {
                        if (time < max) {
                            time += step
                            onValueChanged(time)
                        }
                    },
                    enabled = time < max,
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                    ),
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Increase",
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}