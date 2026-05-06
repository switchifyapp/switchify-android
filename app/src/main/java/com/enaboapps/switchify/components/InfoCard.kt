package com.enaboapps.switchify.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.enaboapps.switchify.theme.Dimens

@Composable
fun InfoCard(
    titleResId: Int,
    descriptionResId: Int
) {
    Panel(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(Dimens.spaceL)) {
            Text(
                text = stringResource(titleResId),
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(Dimens.spaceXs))
            Text(
                text = stringResource(descriptionResId),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
