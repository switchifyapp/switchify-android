package com.enaboapps.switchify.switches

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.enaboapps.switchify.R

/**
 * A banner that is displayed when a switch configuration is invalid or missing
 * Provides informative guidance and visual indicators to help users understand next steps
 */

@Composable
fun SwitchConfigInvalidBanner(
    onClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val validator = remember { SwitchConfigValidator(context) }
    val missingActions = remember { validator.getMissingActions() }

    Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .then(
                    if (onClick != null) {
                        Modifier.clickable { onClick() }
                    } else {
                        Modifier
                    }
                ),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Warning icon
                Icon(
                    imageVector = Icons.Rounded.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(24.dp)
                )
                
                // Content column
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = stringResource(R.string.switch_config_banner_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    
                    Spacer(modifier = Modifier.height(4.dp))

                    val description = if (missingActions.isNotEmpty()) {
                        val actionNames = missingActions.joinToString(", ") { actionId ->
                            SwitchAction(actionId).getActionName()
                        }
                        stringResource(R.string.switch_config_banner_missing_actions, actionNames)
                    } else {
                        stringResource(R.string.switch_config_banner_description)
                    }

                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        lineHeight = MaterialTheme.typography.bodyMedium.lineHeight
                    )
                }
                
                // Settings icon
                Icon(
                    imageVector = Icons.Rounded.Settings,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
}