package com.enaboapps.switchify.components

import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics

@Composable
fun PreferenceSwitch(
    titleResId: Int,
    summaryResId: Int,
    explanationResId: Int? = null,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    var isChecked by remember(checked) { mutableStateOf(checked) }

    PreferenceComponentBase(
        titleResId = titleResId,
        summaryResId = summaryResId,
        explanationResId = explanationResId,
        onClick = if (enabled) {
            {
                val next = !isChecked
                isChecked = next
                onCheckedChange(next)
            }
        } else {
            null
        },
        trailing = {
            Switch(
                checked = isChecked,
                enabled = enabled,
                onCheckedChange = {
                    isChecked = it
                    onCheckedChange(it)
                },
                modifier = Modifier.semantics { role = Role.Switch }
            )
        }
    )
}
