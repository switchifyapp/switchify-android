package com.enaboapps.switchify.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.enaboapps.switchify.backend.iap.IAPHandler

@Composable
fun PreferenceSwitch(
    titleResId: Int,
    summaryResId: Int,
    explanationResId: Int? = null,
    checked: Boolean,
    isRestrictedToPro: Boolean = false,
    onCheckedChange: (Boolean) -> Unit
) {
    var isChecked by remember { mutableStateOf(checked) }
    val isPro = IAPHandler.hasPurchasedPro()

    PreferenceComponentBase(
        titleResId = titleResId,
        summaryResId = summaryResId,
        explanationResId = explanationResId
    ) {
        Row {
            Spacer(modifier = Modifier.width(8.dp))
            if (!isRestrictedToPro || isPro) {
                Switch(
                    checked = isChecked,
                    onCheckedChange = {
                        isChecked = it
                        onCheckedChange(it)
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.primary,
                        checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    )
                )
            } else {
                ProTextLabel()
            }
        }
    }
}