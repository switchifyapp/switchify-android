package com.enaboapps.switchify.components

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.enaboapps.switchify.R
import com.enaboapps.switchify.utils.StringUtils

@Composable
fun PreferenceTimeStepper(
    value: Long,
    titleResId: Int,
    summaryResId: Int,
    explanationResId: Int? = null,
    min: Long,
    max: Long,
    step: Long = 100,
    onValueChanged: (Long) -> Unit
) {
    var time by remember(value) { mutableLongStateOf(value) }
    var showDialog by remember { mutableStateOf(false) }

    fun decrease() {
        if (time > min) {
            time = (time - step).coerceAtLeast(min)
            onValueChanged(time)
        }
    }

    fun increase() {
        if (time < max) {
            time = (time + step).coerceAtMost(max)
            onValueChanged(time)
        }
    }

    BoxWithConstraints {
        val configuration = LocalConfiguration.current
        val isTablet = maxWidth >= 600.dp && configuration.smallestScreenWidthDp.dp >= 600.dp

        PreferenceComponentBase(
            titleResId = titleResId,
            summaryResId = summaryResId,
            explanationResId = explanationResId,
            onClick = if (isTablet) null else ({ showDialog = true }),
            trailing = {
                if (isTablet) {
                    TimeStepperControls(
                        value = time,
                        min = min,
                        max = max,
                        onDecrease = ::decrease,
                        onIncrease = ::increase
                    )
                } else {
                    TimeStepperCompactValue(value = time)
                }
            }
        )

        if (!isTablet && showDialog) {
            AlertDialog(
                onDismissRequest = { showDialog = false },
                title = { Text(text = stringResource(titleResId)) },
                text = {
                    TimeStepperControls(
                        value = time,
                        min = min,
                        max = max,
                        onDecrease = ::decrease,
                        onIncrease = ::increase,
                        modifier = Modifier.wrapContentWidth(Alignment.CenterHorizontally)
                    )
                },
                confirmButton = {
                    TextButton(onClick = { showDialog = false }) {
                        Text(stringResource(R.string.button_done))
                    }
                }
            )
        }
    }
}

@Composable
private fun TimeStepperCompactValue(value: Long) {
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = StringUtils.getSecondsString(value),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 96.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun TimeStepperControls(
    value: Long,
    min: Long,
    max: Long,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onDecrease,
            enabled = value > min,
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
            ),
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Remove,
                contentDescription = "Decrease",
                modifier = Modifier.size(20.dp)
            )
        }

        Text(
            text = StringUtils.getSecondsString(value),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.widthIn(min = 56.dp).wrapContentWidth(Alignment.CenterHorizontally)
        )

        IconButton(
            onClick = onIncrease,
            enabled = value < max,
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
            ),
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Increase",
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
