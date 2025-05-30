package com.enaboapps.switchify.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PreferenceValueSelector(
    value: Int,
    titleResId: Int,
    summaryResId: Int,
    explanationResId: Int? = null,
    min: Int,
    max: Int,
    buttonLabelFormatter: (Int) -> String = { it.toString() },
    displayFormatter: (Int) -> String = { it.toString() },
    onValueChanged: (Int) -> Unit
) {
    var currentValue by remember { mutableIntStateOf(value) }

    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        // Title and description
        PreferenceComponentBase(
            titleResId = titleResId,
            summaryResId = summaryResId,
            explanationResId = explanationResId
        ) {
            // Current value display on the right
            Text(
                text = displayFormatter(currentValue),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        
        // Value buttons below
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            for (i in min..max) {
                Button(
                    onClick = {
                        currentValue = i
                        onValueChanged(i)
                    },
                    modifier = Modifier,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (currentValue == i) 
                            MaterialTheme.colorScheme.primary 
                        else 
                            MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (currentValue == i)
                            MaterialTheme.colorScheme.onPrimary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    contentPadding = ButtonDefaults.TextButtonContentPadding
                ) {
                    Text(
                        text = buttonLabelFormatter(i),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
}