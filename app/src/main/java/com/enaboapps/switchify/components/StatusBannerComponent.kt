package com.enaboapps.switchify.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessibilityNew
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.enaboapps.switchify.R

@Composable
fun StatusBannerComponent(
    isAccessibilityServiceEnabled: Boolean,
    isPro: Boolean,
    onAccessibilityClick: () -> Unit,
    onProUpgradeClick: () -> Unit
) {
    Column {
        Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Max),
        horizontalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        // Accessibility Service Status
        Surface(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .then(
                    if (!isAccessibilityServiceEnabled) {
                        Modifier.clickable { onAccessibilityClick() }
                    } else {
                        Modifier
                    }
                ),
            color = if (isAccessibilityServiceEnabled) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.error
            },
            tonalElevation = 4.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = if (isAccessibilityServiceEnabled) {
                            stringResource(R.string.accessibility_service_banner_title_enabled)
                        } else {
                            stringResource(R.string.accessibility_service_banner_title_disabled)
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (isAccessibilityServiceEnabled) {
                            stringResource(R.string.accessibility_service_enabled)
                        } else {
                            stringResource(R.string.accessibility_service_disabled)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
                if (!isAccessibilityServiceEnabled) {
                    Icon(
                        imageVector = Icons.Rounded.AccessibilityNew,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        // Pro Upgrade Banner (only show when not Pro)
        if (!isPro) {
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable { onProUpgradeClick() },
                color = MaterialTheme.colorScheme.secondaryContainer,
                tonalElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = stringResource(R.string.screen_title_upgrade_pro),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = stringResource(R.string.screen_summary_upgrade_pro),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                    Icon(
                        imageVector = Icons.Rounded.Star,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
        }

        // Trial info banner for non-Pro users
        if (!isPro) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onProUpgradeClick() },
                color = MaterialTheme.colorScheme.tertiaryContainer,
                tonalElevation = 2.dp
            ) {
                Text(
                    text = stringResource(R.string.trial_info_banner),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}