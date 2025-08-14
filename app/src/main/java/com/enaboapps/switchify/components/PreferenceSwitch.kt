package com.enaboapps.switchify.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.enaboapps.switchify.R

@Composable
private fun ButtonToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // OFF Button
        if (!checked) {
            Button(
                onClick = { onCheckedChange(false) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                shape = RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp, topEnd = 0.dp, bottomEnd = 0.dp),
                modifier = Modifier
                    .semantics { role = Role.Switch }
            ) {
                Text(stringResource(R.string.off))
            }
        } else {
            OutlinedButton(
                onClick = { onCheckedChange(false) },
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                shape = RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp, topEnd = 0.dp, bottomEnd = 0.dp)
            ) {
                Text(stringResource(R.string.off))
            }
        }
        
        // ON Button
        if (checked) {
            Button(
                onClick = { onCheckedChange(true) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                shape = RoundedCornerShape(topStart = 0.dp, bottomStart = 0.dp, topEnd = 8.dp, bottomEnd = 8.dp),
                modifier = Modifier
                    .semantics { role = Role.Switch }
            ) {
                Text(stringResource(R.string.on))
            }
        } else {
            OutlinedButton(
                onClick = { onCheckedChange(true) },
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                shape = RoundedCornerShape(topStart = 0.dp, bottomStart = 0.dp, topEnd = 8.dp, bottomEnd = 8.dp)
            ) {
                Text(stringResource(R.string.on))
            }
        }
    }
}

@Composable
fun PreferenceSwitch(
    titleResId: Int,
    summaryResId: Int,
    explanationResId: Int? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    var isChecked by remember { mutableStateOf(checked) }

    PreferenceComponentBase(
        titleResId = titleResId,
        summaryResId = summaryResId,
        explanationResId = explanationResId
    ) {
        Row {
            Spacer(modifier = Modifier.width(8.dp))
            ButtonToggle(
                checked = isChecked,
                onCheckedChange = {
                    isChecked = it
                    onCheckedChange(it)
                }
            )
        }
    }
}