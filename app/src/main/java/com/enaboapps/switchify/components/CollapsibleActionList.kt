package com.enaboapps.switchify.components

import android.Manifest
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.enaboapps.switchify.R
import com.enaboapps.switchify.nav.NavigationRoute
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState

data class ActionItem(
    val titleResId: Int,
    val summaryResId: Int,
    val icon: ImageVector,
    val route: String,
    val isVisible: Boolean = true
)

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CollapsibleActionList(
    isExpanded: Boolean,
    onToggleExpanded: () -> Unit,
    navController: NavController,
    hasUsageStatsPermission: Boolean,
    showDebug: Boolean,
    modifier: Modifier = Modifier
) {
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)
    val hasCameraPermission = cameraPermissionState.status.isGranted

    val actionItems = remember(hasUsageStatsPermission, showDebug, hasCameraPermission) {
        listOfNotNull(
            if (!hasUsageStatsPermission) {
                ActionItem(
                    titleResId = R.string.menu_title_quick_apps,
                    summaryResId = R.string.screen_summary_quick_apps_permission,
                    icon = Icons.Rounded.Apps,
                    route = NavigationRoute.UsageStatsPermission.name
                )
            } else null,
            if (!hasCameraPermission) {
                ActionItem(
                    titleResId = R.string.home_camera_permission_title,
                    summaryResId = R.string.home_camera_permission_summary,
                    icon = Icons.Rounded.CameraAlt,
                    route = NavigationRoute.CameraSettings.name
                )
            } else null,
            if (showDebug) {
                ActionItem(
                    titleResId = R.string.screen_title_debug,
                    summaryResId = R.string.screen_summary_debug,
                    icon = Icons.Rounded.BugReport,
                    route = NavigationRoute.Debug.name
                )
            } else null
        )
    }

    // Don't show anything if no actions are available
    if (actionItems.isEmpty()) {
        return
    }

    Column(
        modifier = modifier
    ) {
        // Header Surface (full-width, no rounded corners)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggleExpanded() }
                .animateContentSize(
                    animationSpec = tween(300)
                ),
            color = MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = 2.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.home_actions_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = stringResource(R.string.home_actions_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                Icon(
                    imageVector = if (isExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Animated Action List
        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically(
                animationSpec = tween(400)
            ) + fadeIn(
                animationSpec = tween(400)
            ),
            exit = shrinkVertically(
                animationSpec = tween(400)
            ) + fadeOut(
                animationSpec = tween(400)
            )
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
            ) {
                actionItems.forEach { action ->
                    ActionListItem(
                        action = action,
                        onClick = {
                            navController.navigate(action.route)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionListItem(
    action: ActionItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = action.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(action.titleResId),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(action.summaryResId),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}