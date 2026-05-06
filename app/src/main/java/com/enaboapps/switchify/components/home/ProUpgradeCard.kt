package com.enaboapps.switchify.components.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.enaboapps.switchify.R
import com.enaboapps.switchify.components.Panel
import com.enaboapps.switchify.theme.Dimens

@Composable
fun ProUpgradeCard(
    isReminder: Boolean,
    onLearnMore: () -> Unit,
    onDismiss: () -> Unit,
    onRemindLater: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.medium
) {
    val scheme = MaterialTheme.colorScheme
    Panel(
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        containerColor = scheme.secondaryContainer
    ) {
        Column(modifier = Modifier.padding(Dimens.spaceM)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.spaceM)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(scheme.secondary.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Star,
                        contentDescription = null,
                        tint = scheme.onSecondaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.pro_reminder_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = scheme.onSecondaryContainer
                    )
                    Text(
                        text = stringResource(R.string.home_pro_trial_pill),
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSecondaryContainer.copy(alpha = 0.75f)
                    )
                }
                TextButton(onClick = onLearnMore) {
                    Text(
                        text = stringResource(R.string.pro_reminder_learn_more),
                        color = scheme.onSecondaryContainer
                    )
                }
            }

            if (isReminder) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(
                            text = stringResource(R.string.pro_reminder_dismiss),
                            color = scheme.onSecondaryContainer.copy(alpha = 0.7f)
                        )
                    }
                    TextButton(onClick = onRemindLater) {
                        Text(
                            text = stringResource(R.string.pro_reminder_later),
                            color = scheme.onSecondaryContainer.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
    }
}
