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
    onCheckedChange: (Boolean) -> Unit
) {
    var isChecked by remember(checked) { mutableStateOf(checked) }

    PreferenceComponentBase(
        titleResId = titleResId,
        summaryResId = summaryResId,
        explanationResId = explanationResId,
        onClick = {
            val next = !isChecked
            isChecked = next
            onCheckedChange(next)
        },
        trailing = {
            Switch(
                checked = isChecked,
                onCheckedChange = {
                    isChecked = it
                    onCheckedChange(it)
                },
                modifier = Modifier.semantics { role = Role.Switch }
            )
        }
    )
}
