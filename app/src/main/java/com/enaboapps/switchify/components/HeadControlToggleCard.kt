package com.enaboapps.switchify.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.enaboapps.switchify.R
import com.enaboapps.switchify.service.camera.CameraPermissionManager
import com.enaboapps.switchify.service.core.ServiceBridge
import com.enaboapps.switchify.service.techniques.headcontrol.HeadControlSettings
import com.enaboapps.switchify.service.utils.ServiceUtils
import com.enaboapps.switchify.theme.Dimens
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun HeadControlToggleCard(
    modifier: Modifier = Modifier,
    serviceUtils: ServiceUtils = ServiceUtils()
) {
    val context = LocalContext.current

    val isServiceActive = remember { serviceUtils.isAccessibilityServiceEnabled(context) }
    val hasCameraPermission =
        remember { CameraPermissionManager.getInstance(context).hasPermission() }

    if (!isServiceActive || !hasCameraPermission) return

    val settings = remember { HeadControlSettings(context) }
    var headEnabled by remember { mutableStateOf(settings.isHeadControlEnabled()) }
    var coolingDown by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        ServiceBridge.serviceEvents.collect { event ->
            if (event is ServiceBridge.ServiceEvent.ConfigurationUpdated) {
                headEnabled = settings.isHeadControlEnabled()
            }
        }
    }

    val iconScale by animateFloatAsState(
        targetValue = if (headEnabled) 1.1f else 1.0f,
        label = "iconScale"
    )

    val onClick = onClick@{
        if (coolingDown) return@onClick
        val desired = !headEnabled
        ServiceBridge.sendCommand(ServiceBridge.ServiceCommand.SetHeadControlEnabled(desired))
        coolingDown = true
        scope.launch {
            delay(1000L)
            coolingDown = false
        }
    }

    val scheme = MaterialTheme.colorScheme

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(scheme.surfaceColorAtElevation(1.dp))
            .clickable(enabled = !coolingDown, onClick = { onClick.let { it() } })
            .padding(Dimens.spaceM),
        horizontalArrangement = Arrangement.spacedBy(Dimens.spaceM),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(scheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_head_control_pointer),
                contentDescription = null,
                tint = scheme.primary,
                modifier = Modifier
                    .size(22.dp)
                    .graphicsLayer(scaleX = iconScale, scaleY = iconScale)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.home_head_control_title),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = stringResource(
                    if (headEnabled) R.string.home_head_control_on_summary else R.string.home_head_control_off_summary
                ),
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant
            )
        }
        Text(
            text = if (headEnabled) "ON" else "OFF",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = if (headEnabled) scheme.primary else scheme.onSurfaceVariant.copy(alpha = 0.6f)
        )
    }
}
