package com.enaboapps.switchify.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.enaboapps.switchify.R
import com.enaboapps.switchify.service.camera.CameraPermissionManager
import com.enaboapps.switchify.service.core.ServiceBridge
import com.enaboapps.switchify.service.techniques.headcontrol.HeadControlSettings
import com.enaboapps.switchify.service.utils.ServiceUtils
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

    // Listen to ServiceBridge events for actual state changes
    LaunchedEffect(Unit) {
        ServiceBridge.serviceEvents.collect { event ->
            if (event is ServiceBridge.ServiceEvent.ConfigurationUpdated) {
                // Query actual state from settings instead of optimistic update
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
        // Don't update state optimistically - wait for ServiceBridge event
        ServiceBridge.sendCommand(ServiceBridge.ServiceCommand.SetHeadControlEnabled(desired))
        coolingDown = true
        scope.launch {
            delay(1000L)
            coolingDown = false
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clickable(enabled = !coolingDown, onClick = { onClick.let { it() } }),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .padding(bottom = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_head_control_pointer),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(scaleX = iconScale, scaleY = iconScale)
                )
            }
            Text(
                text = stringResource(R.string.home_head_control_title),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = stringResource(
                    if (headEnabled) R.string.home_head_control_on_summary else R.string.home_head_control_off_summary
                ),
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}


