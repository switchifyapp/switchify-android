package com.enaboapps.switchify.components

import android.app.Activity
import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.enaboapps.switchify.R
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability

@Composable
fun InAppUpdateBar(
    modifier: Modifier = Modifier,
    onError: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val appUpdateManager = remember { AppUpdateManagerFactory.create(context) }
    var showRestart by remember { mutableStateOf(false) }
    var isDownloading by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }

    val listener = remember {
        InstallStateUpdatedListener { state ->
            when (state.installStatus()) {
                InstallStatus.DOWNLOADING -> {
                    isDownloading = true
                    val total = state.totalBytesToDownload().toFloat()
                    if (total > 0) progress = state.bytesDownloaded().toFloat() / total
                }
                InstallStatus.DOWNLOADED -> {
                    isDownloading = false
                    showRestart = true
                }
                InstallStatus.FAILED -> {
                    isDownloading = false
                    onError("Update failed: ${state.installErrorCode()}")
                }
                InstallStatus.INSTALLED -> {
                    isDownloading = false
                    showRestart = false
                }
                else -> {}
            }
        }
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        when (result.resultCode) {
            Activity.RESULT_OK -> {}
            Activity.RESULT_CANCELED -> onError("Update cancelled")
            else -> onError("Update failed to start")
        }
    }

    LaunchedEffect(Unit) {
        tryResumeOrCheck(context, appUpdateManager, launcher) { onError(it) }
        appUpdateManager.appUpdateInfo.addOnSuccessListener { info ->
            if (info.installStatus() == InstallStatus.DOWNLOADED) showRestart = true
        }
    }

    DisposableEffect(Unit) {
        appUpdateManager.registerListener(listener)
        onDispose { appUpdateManager.unregisterListener(listener) }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                appUpdateManager.appUpdateInfo.addOnSuccessListener { info ->
                    if (info.installStatus() == InstallStatus.DOWNLOADED) {
                        isDownloading = false
                        showRestart = true
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (isDownloading) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.dialog_title_downloading_update),
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "${(progress * 100).toInt()}%",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
            )
        }
    }

    if (showRestart) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stringResource(R.string.dialog_message_update),
                style = MaterialTheme.typography.bodyMedium
            )
            Row {
                TextButton(onClick = { showRestart = false }) {
                    Text(stringResource(R.string.dialog_button_later))
                }
                TextButton(onClick = { appUpdateManager.completeUpdate() }) {
                    Text(stringResource(R.string.dialog_button_restart))
                }
            }
        }
    }
}

private fun tryResumeOrCheck(
    context: Context,
    appUpdateManager: AppUpdateManager,
    launcher: androidx.activity.result.ActivityResultLauncher<IntentSenderRequest>,
    onError: (String) -> Unit
) {
    try {
        appUpdateManager.appUpdateInfo
            .addOnSuccessListener { info ->
                if (info.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS) {
                    startUpdate(context, appUpdateManager, info, launcher, AppUpdateType.FLEXIBLE, onError)
                    return@addOnSuccessListener
                }

                if (info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE) {
                    if (info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)) {
                        startUpdate(context, appUpdateManager, info, launcher, AppUpdateType.FLEXIBLE, onError)
                    }
                }
            }
            .addOnFailureListener { e -> onError("Failed to check updates: ${e.localizedMessage}") }
    } catch (e: Exception) {
        onError("Exception checking updates: ${e.localizedMessage}")
    }
}


private fun startUpdate(
    context: Context,
    appUpdateManager: AppUpdateManager,
    info: com.google.android.play.core.appupdate.AppUpdateInfo,
    launcher: androidx.activity.result.ActivityResultLauncher<IntentSenderRequest>,
    type: Int,
    onError: (String) -> Unit
) {
    try {
        val options = AppUpdateOptions.newBuilder(type).build()
        appUpdateManager.startUpdateFlowForResult(info, launcher, options)
    } catch (e: Exception) {
        Log.e("InAppUpdateBar", "Error starting update flow", e)
        Toast.makeText(context, "Failed to start update: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        onError("Failed to start update: ${e.localizedMessage}")
    }
}
