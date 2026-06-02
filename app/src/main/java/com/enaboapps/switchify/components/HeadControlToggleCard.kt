package com.enaboapps.switchify.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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

    PreferenceRowScaffold(
        title = stringResource(R.string.home_head_control_title),
        summary = stringResource(
            if (headEnabled) R.string.home_head_control_on_summary else R.string.home_head_control_off_summary
        ),
        modifier = modifier,
        enabled = !coolingDown,
        onClick = { onClick.let { it() } },
        leadingContent = {
            PreferenceRowLeadingIcon(
                painter = painterResource(id = R.drawable.ic_head_control_pointer),
                contentDescription = null,
                iconModifier = Modifier.graphicsLayer(scaleX = iconScale, scaleY = iconScale)
            )
        }
    ) {
        Text(
            text = if (headEnabled) "ON" else "OFF",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = if (headEnabled) scheme.primary else scheme.onSurfaceVariant.copy(alpha = 0.6f)
        )
    }
}
