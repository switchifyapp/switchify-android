package com.enaboapps.switchify.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.enaboapps.switchify.utils.StringUtils

@Composable
fun PreferenceTimeStepper(
    value: Long,
    titleResId: Int,
    summaryResId: Int,
    min: Long,
    max: Long,
    step: Long = 100, // Default step is 100
    onValueChanged: (Long) -> Unit
) {
    var time by remember { mutableLongStateOf(value) }

    val title = stringResource(titleResId)
    val summary = stringResource(summaryResId)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title.uppercase(),
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = summary, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(modifier = Modifier.weight(1f))
            Column(
                modifier = Modifier.wrapContentWidth(Alignment.End),
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = StringUtils.getSecondsString(time),
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        if (time > min) {
                            time -= step
                            onValueChanged(time)
                        }
                    }) {
                        Text(text = "-", style = MaterialTheme.typography.bodyMedium)
                    }
                    Button(onClick = {
                        if (time < max) {
                            time += step
                            onValueChanged(time)
                        }
                    }) {
                        Text(text = "+", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}
