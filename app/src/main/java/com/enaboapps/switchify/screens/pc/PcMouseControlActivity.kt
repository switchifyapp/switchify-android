package com.enaboapps.switchify.screens.pc

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.enaboapps.switchify.R
import com.enaboapps.switchify.activities.ui.theme.SwitchifyTheme
import com.enaboapps.switchify.components.NavBar
import com.enaboapps.switchify.pc.isSafePcTypedText
import com.enaboapps.switchify.service.core.ServiceCore
import com.enaboapps.switchify.service.scanning.TemporaryScanModeSession
import com.enaboapps.switchify.service.techniques.AccessTechnique

class PcMouseControlActivity : ComponentActivity() {
    private val viewModel: PcMouseControlViewModel by viewModels {
        viewModelFactory {
            initializer { PcMouseControlViewModel(applicationContext) }
        }
    }
    private var scanModeSession: TemporaryScanModeSession? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SwitchifyTheme {
                PcMouseControlScreen(
                    viewModel = viewModel,
                    onClose = { finish() }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        ServiceCore.getScanningManager()?.let { manager ->
            scanModeSession = TemporaryScanModeSession(
                scanningManager = manager,
                targetTechnique = AccessTechnique.Technique.ITEM_SCAN
            ).also { it.start() }
        }
    }

    override fun onPause() {
        scanModeSession?.close()
        scanModeSession = null
        super.onPause()
    }

    companion object {
        fun createIntent(context: Context): Intent {
            return Intent(context, PcMouseControlActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
    }
}

@Composable
private fun PcMouseControlScreen(
    viewModel: PcMouseControlViewModel,
    onClose: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            NavBar(
                title = stringResource(R.string.menu_title_control_pc),
                showBackButton = true,
                onBackPressed = onClose
            )
        }
    ) { paddingValues ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                PcControlStatusStrip(
                    connectedDisplayName = uiState.connectedDisplayName,
                    message = uiState.message
                )
                PcMovementSizeSection(
                    selectedSize = uiState.selectedMovementSize,
                    onSizeSelected = viewModel::selectMovementSize
                )
                PcMouseCommandSections(
                    connected = uiState.connectedDisplayName != null,
                    movementStep = uiState.movementStep,
                    onCommandSelected = viewModel::send
                )
                PcTypingCommandSection(
                    connected = uiState.connectedDisplayName != null,
                    onOpenTyping = viewModel::openTypingDialog
                )
            }
            if (uiState.typingDialogVisible) {
                PcTypingDialog(
                    text = uiState.typingText,
                    message = uiState.typingMessage,
                    sendEnabled = uiState.connectedDisplayName != null &&
                            uiState.typingText.isNotEmpty() &&
                            isSafePcTypedText(uiState.typingText) &&
                            !uiState.isBusy,
                    onTextChanged = viewModel::updateTypingText,
                    onSend = viewModel::sendTypedText,
                    onClear = viewModel::clearTypingText,
                    onClose = viewModel::closeTypingDialog
                )
            }
        }
    }
}

@Composable
private fun PcTypingDialog(
    text: String,
    message: String?,
    sendEnabled: Boolean,
    onTextChanged: (String) -> Unit,
    onSend: () -> Unit,
    onClear: () -> Unit,
    onClose: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onClose,
        title = {
            Text(text = stringResource(R.string.pc_typing_dialog_title))
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = onTextChanged,
                    label = { Text(stringResource(R.string.pc_typing_text_label)) },
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth()
                )
                message?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onSend,
                enabled = sendEnabled
            ) {
                Text(text = stringResource(R.string.pc_typing_send))
            }
        },
        dismissButton = {
            TextButton(onClick = onClear) {
                Text(text = stringResource(R.string.pc_typing_clear))
            }
            TextButton(onClick = onClose) {
                Text(text = stringResource(R.string.pc_typing_close))
            }
        }
    )
}
