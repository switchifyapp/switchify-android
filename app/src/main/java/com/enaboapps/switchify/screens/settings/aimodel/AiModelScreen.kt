package com.enaboapps.switchify.screens.settings.aimodel

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
fun AiModelScreen(navController: NavController) {
    val context = LocalContext.current
    val viewModel: ModelDownloadViewModel = viewModel { ModelDownloadViewModel(context) }
    val uiState by viewModel.uiState.observeAsState(ModelDownloadUiState.NotDownloaded)
    val isBuiltInAi = uiState is ModelDownloadUiState.BuiltInReady ||
        uiState is ModelDownloadUiState.BuiltInSetup ||
        uiState is ModelDownloadUiState.BuiltInPreparing

    BaseView(
        titleResId = R.string.screen_title_ai_model,
        navController = navController
    ) {
        InfoCard(
            titleResId = R.string.settings_title_ai_model,
            descriptionResId = if (isBuiltInAi) {
                R.string.ai_model_builtin_description
            } else {
                R.string.ai_model_description
            }
        )
        Spacer(modifier = Modifier.height(Dimens.spaceL))

        when {
            Build.VERSION.SDK_INT < Build.VERSION_CODES.R ->
                StatusText(R.string.ai_model_unsupported_os)

            !IAPHandler.isPro() -> {
                StatusText(R.string.ai_model_pro_required)
                ActionButton(
                    textResId = R.string.ai_model_get_pro,
                    onClick = { navController.navigate(NavigationRoute.Paywall.name) }
                )
            }

            else -> ModelStateContent(uiState, viewModel, navController)
        }

        if (!isBuiltInAi) {
            Spacer(modifier = Modifier.height(Dimens.spaceL))
            Text(
                text = stringResource(R.string.gemma_built_with),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = Dimens.spaceM)
            )
            ActionButton(
                textResId = R.string.gemma_terms_link,
                onClick = { navController.navigate(NavigationRoute.GemmaTerms.name) },
                type = ActionButtonType.SECONDARY
            )
        }
    }
}

@Composable
private fun ModelStateContent(state: ModelDownloadUiState, viewModel: ModelDownloadViewModel, navController: NavController) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    when (state) {
        is ModelDownloadUiState.Ready -> {
            StatusText(R.string.ai_model_ready)
            ActionButton(
                textResId = R.string.ai_model_delete_model,
                onClick = { showDeleteDialog = true },
                type = ActionButtonType.DESTRUCTIVE
            )
        }

        is ModelDownloadUiState.Downloading -> {
            StatusText(R.string.ai_model_downloading)
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
                        R.string.ai_model_download_progress,
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
            Spacer(modifier = Modifier.height(Dimens.spaceS))
            Text(
                text = stringResource(R.string.ai_model_keep_screen_open),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = Dimens.spaceM)
            )
            ActionButton(
                textResId = R.string.ai_model_cancel,
                onClick = { viewModel.cancelDownload() },
                type = ActionButtonType.SECONDARY
            )
        }

        is ModelDownloadUiState.Failed -> {
            StatusText(R.string.ai_model_download_failed)
            ActionButton(
                textResId = R.string.ai_model_retry,
                onClick = { viewModel.startDownload() }
            )
        }

        is ModelDownloadUiState.NotDownloaded -> when {
            !viewModel.hasEnoughFreeSpace() ->
                StatusText(R.string.ai_model_not_enough_space)

            !viewModel.isTermsAccepted() -> ActionButton(
                textResId = R.string.gemma_terms_review,
                onClick = { navController.navigate(NavigationRoute.GemmaTerms.name) }
            )

            else -> ActionButton(
                textResId = R.string.ai_model_download_button,
                onClick = { viewModel.startDownload() }
            )
        }

        is ModelDownloadUiState.BuiltInReady ->
            StatusText(R.string.ai_model_builtin_ready)

        is ModelDownloadUiState.BuiltInSetup -> {
            StatusText(R.string.ai_model_builtin_setup)
            ActionButton(
                textResId = R.string.ai_model_builtin_setup_button,
                onClick = { viewModel.prepareBuiltIn() }
            )
        }

        is ModelDownloadUiState.BuiltInPreparing -> {
            StatusText(R.string.ai_model_builtin_preparing)
            Spacer(modifier = Modifier.height(Dimens.spaceS))
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimens.spaceM)
            )
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.ai_model_delete_model)) },
            text = { Text(stringResource(R.string.ai_model_delete_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    viewModel.deleteModel()
                }) {
                    Text(stringResource(R.string.ai_model_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.ai_model_cancel))
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
