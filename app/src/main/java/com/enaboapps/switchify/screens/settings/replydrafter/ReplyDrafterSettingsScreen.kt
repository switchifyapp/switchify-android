package com.enaboapps.switchify.screens.settings.replydrafter

import android.os.Build
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.enaboapps.switchify.R
import com.enaboapps.switchify.backend.iap.IAPHandler
import com.enaboapps.switchify.components.ActionButton
import com.enaboapps.switchify.components.ActionButtonType
import com.enaboapps.switchify.components.BaseView
import com.enaboapps.switchify.components.InfoCard
import com.enaboapps.switchify.nav.NavigationRoute
import com.enaboapps.switchify.theme.Dimens

private const val BYTES_PER_MB = 1024L * 1024L

@Composable
fun ReplyDrafterSettingsScreen(navController: NavController) {
    val context = LocalContext.current
    val viewModel: ModelDownloadViewModel = viewModel { ModelDownloadViewModel(context) }
    val uiState by viewModel.uiState.observeAsState(ModelDownloadUiState.NotDownloaded)

    BaseView(
        titleResId = R.string.screen_title_reply_drafter,
        navController = navController
    ) {
        InfoCard(
            titleResId = R.string.settings_title_reply_drafter,
            descriptionResId = R.string.reply_drafter_model_description
        )
        Spacer(modifier = Modifier.height(Dimens.spaceL))

        when {
            Build.VERSION.SDK_INT < Build.VERSION_CODES.R ->
                StatusText(R.string.reply_drafter_unsupported_os)

            !IAPHandler.isPro() -> {
                StatusText(R.string.reply_drafter_pro_required)
                ActionButton(
                    textResId = R.string.reply_drafter_get_pro,
                    onClick = { navController.navigate(NavigationRoute.Paywall.name) }
                )
            }

            else -> ModelStateContent(uiState, viewModel)
        }
    }
}

@Composable
private fun ModelStateContent(state: ModelDownloadUiState, viewModel: ModelDownloadViewModel) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    when (state) {
        is ModelDownloadUiState.Ready -> {
            StatusText(R.string.reply_drafter_ready)
            ActionButton(
                textResId = R.string.reply_drafter_delete_model,
                onClick = { showDeleteDialog = true },
                type = ActionButtonType.DESTRUCTIVE
            )
        }

        is ModelDownloadUiState.Downloading -> {
            StatusText(R.string.reply_drafter_downloading)
            Spacer(modifier = Modifier.height(Dimens.spaceS))
            if (state.totalBytes > 0L) {
                LinearProgressIndicator(
                    progress = {
                        (state.bytesDownloaded.toFloat() / state.totalBytes).coerceIn(0f, 1f)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Dimens.spaceM)
                )
                Spacer(modifier = Modifier.height(Dimens.spaceXs))
                Text(
                    text = stringResource(
                        R.string.reply_drafter_download_progress,
                        state.bytesDownloaded / BYTES_PER_MB,
                        state.totalBytes / BYTES_PER_MB
                    ),
                    style = MaterialTheme.typography.bodySmall
                )
            } else {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Dimens.spaceM)
                )
            }
            ActionButton(
                textResId = R.string.reply_drafter_cancel,
                onClick = { viewModel.cancelDownload() },
                type = ActionButtonType.SECONDARY
            )
        }

        is ModelDownloadUiState.Failed -> {
            StatusText(R.string.reply_drafter_download_failed)
            ActionButton(
                textResId = R.string.reply_drafter_retry,
                onClick = { viewModel.startDownload() }
            )
        }

        is ModelDownloadUiState.NotDownloaded -> when {
            !viewModel.downloadConfigured ->
                StatusText(R.string.reply_drafter_not_configured)

            !viewModel.hasEnoughFreeSpace() ->
                StatusText(R.string.reply_drafter_not_enough_space)

            else -> ActionButton(
                textResId = R.string.reply_drafter_download_button,
                onClick = { viewModel.startDownload() }
            )
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.reply_drafter_delete_model)) },
            text = { Text(stringResource(R.string.reply_drafter_delete_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    viewModel.deleteModel()
                }) {
                    Text(stringResource(R.string.reply_drafter_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.reply_drafter_cancel))
                }
            }
        )
    }
}

@Composable
private fun StatusText(textResId: Int) {
    Text(
        text = stringResource(textResId),
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.padding(horizontal = Dimens.spaceM)
    )
}
