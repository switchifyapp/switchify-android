package com.enaboapps.switchify.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.enaboapps.switchify.theme.Dimens

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SwitchListItem(
    title: String,
    subtitle: String? = null,
    chips: List<String> = emptyList(),
    chipsTitle: String? = null,
    onClick: () -> Unit
) {
    UICard(
        runtimeTitle = title,
        runtimeDescription = subtitle,
        rightIcon = Icons.AutoMirrored.Filled.ArrowForward,
        onClick = onClick,
        bottomContent = {
            if (chips.isNotEmpty()) {
                Spacer(modifier = Modifier.height(Dimens.spaceS))
                Row {
                    Text(chipsTitle ?: "")
                    Spacer(modifier = Modifier.width(Dimens.spaceS))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(Dimens.spaceS)
                    ) {
                        chips.take(3).forEach { label ->
                            AssistChip(
                                onClick = { /* no-op in list */ },
                                label = { Text(label) },
                                colors = AssistChipDefaults.assistChipColors()
                            )
                            Spacer(modifier = Modifier.height(Dimens.spaceS))
                        }
                        val overflow = chips.size - 3
                        if (overflow > 0) {
                            AssistChip(
                                onClick = { /* no-op */ },
                                label = { Text("+$overflow") },
                                colors = AssistChipDefaults.assistChipColors()
                            )
                        }
                    }
                }
            }
        }
    )
}

