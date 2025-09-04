package com.enaboapps.switchify.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.enaboapps.switchify.theme.Dimens

@Composable
fun UICard(
    modifier: Modifier = Modifier,
    titleResId: Int? = null,
    runtimeTitle: String? = null,
    descriptionResId: Int? = null,
    extraDescriptionResId: Int? = null,
    runtimeDescription: String? = null,
    runtimeExtraDescription: String? = null,
    rightIcon: ImageVector? = null,
    rightActionButton: @Composable () -> Unit = {},
    onClick: () -> Unit,
    bottomContent: (@Composable () -> Unit)? = null,
    enabled: Boolean? = true
) {
    val title = titleResId?.let { stringResource(it) } ?: runtimeTitle ?: ""
    val description = runtimeDescription ?: descriptionResId?.let { stringResource(it) }
    val extraDescription = runtimeExtraDescription ?: extraDescriptionResId?.let { stringResource(it) }
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clickable(onClick = {
                if (enabled == true) {
                    onClick()
                }
            })
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(20.dp)
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                Text(
                    text = title.uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(Dimens.spaceXs))
                description?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                extraDescription?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                bottomContent?.let {
                    Spacer(modifier = Modifier.height(Dimens.spaceXs))
                    it()
                }
            }

            rightActionButton()

            rightIcon?.let {
                Icon(
                    imageVector = it,
                    contentDescription = null,
                    modifier = Modifier.padding(start = Dimens.spaceM)
                )
            }
        }
    }
}
