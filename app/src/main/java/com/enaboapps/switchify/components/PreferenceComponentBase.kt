package com.enaboapps.switchify.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.enaboapps.switchify.R
import com.enaboapps.switchify.theme.Dimens

@Composable
fun PreferenceComponentBase(
    titleResId: Int,
    summaryResId: Int,
    explanationResId: Int? = null,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.spaceL)
            .padding(bottom = Dimens.spaceXs)
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimens.spaceM)
        ) {
            val isNarrow = maxWidth < 400.dp

            if (isNarrow) {
                // Narrow screen: Stack vertically
                Column(modifier = Modifier.fillMaxWidth()) {
                    TitleAndSummaryContent(
                        titleResId = titleResId,
                        summaryResId = summaryResId,
                        explanationResId = explanationResId
                    )
                    Spacer(modifier = Modifier.height(Dimens.spaceM))
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.CenterEnd
                    ) {
                        content()
                    }
                }
            } else {
                // Wide screen: Horizontal layout
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TitleAndSummaryContent(
                        titleResId = titleResId,
                        summaryResId = summaryResId,
                        explanationResId = explanationResId,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(Dimens.spaceM))
                    content()
                }
            }
        }
    }
}

@Composable
private fun TitleAndSummaryContent(
    titleResId: Int,
    summaryResId: Int,
    explanationResId: Int?,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = stringResource(titleResId).uppercase(),
                style = MaterialTheme.typography.titleMedium.copy(
                    lineHeight = 24.sp
                ),
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                softWrap = true
            )

            if (explanationResId != null) {
                var showExplanationDialog by remember { mutableStateOf(false) }

                IconButton(onClick = { showExplanationDialog = true }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Help,
                        contentDescription = stringResource(R.string.help),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                if (showExplanationDialog) {
                    AlertDialog(
                        onDismissRequest = { showExplanationDialog = false },
                        title = { Text(stringResource(titleResId)) },
                        text = { Text(stringResource(explanationResId)) },
                        confirmButton = {
                            TextButton(onClick = { showExplanationDialog = false }) {
                                Text(stringResource(R.string.ok))
                            }
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(Dimens.spaceXs))

        Text(
            text = stringResource(summaryResId),
            style = MaterialTheme.typography.bodySmall.copy(
                lineHeight = 24.sp
            ),
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            softWrap = true
        )
    }
}
