package com.enaboapps.switchify.screens.pc

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

    BackHandler(onBack = onClose)

    Scaffold(
        topBar = {
            NavBar(
                title = "",
                titleContent = {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        PcConnectionStatusDot(
                            connectedDisplayName = uiState.connectedDisplayName
                        )
                        PcControlSurfaceSwitcher(
                            selectedSurface = uiState.activeSurface,
                            onSurfaceSelected = viewModel::selectControlSurface,
                            modifier = Modifier.weight(1f)
                        )
                    }
                },
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
            when (uiState.activeSurface) {
                PcControlSurface.Mouse -> PcMouseControlSurface(
                    uiState = uiState,
                    viewModel = viewModel,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                )
                PcControlSurface.Typing -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    PcTransientMessage(message = uiState.message)
                    PcTypingControlScreen(
                        typingText = uiState.typingText,
                        typingMessage = uiState.typingMessage,
                        sendEnabled = uiState.connectedDisplayName != null &&
                                uiState.typingText.isNotEmpty() &&
                                isSafePcTypedText(uiState.typingText) &&
                                !uiState.isBusy,
                        keysEnabled = uiState.connectedDisplayName != null && !uiState.isBusy,
                        onTextChanged = viewModel::updateTypingText,
                        onSend = viewModel::sendTypedText,
                        onClear = viewModel::clearTypingText,
                        onKeySelected = viewModel::sendKey
                    )
                }
                PcControlSurface.Window -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    PcTransientMessage(message = uiState.message)
                    PcWindowControlScreen(
                        connected = uiState.connectedDisplayName != null,
                        onCommandSelected = viewModel::send
                    )
                }
            }
        }
    }
}

@Composable
private fun PcMouseControlSurface(
    uiState: PcMouseControlUiState,
    viewModel: PcMouseControlViewModel,
    modifier: Modifier = Modifier
) {
    val connected = uiState.connectedDisplayName != null
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        PcTransientMessage(message = uiState.message)
        PcMouseControlPad(
            connected = connected,
            movementStep = uiState.movementStep,
            onCommandSelected = viewModel::send,
            modifier = Modifier.weight(1f)
        )
        PcCommandButtonRow(
            specs = pcSecondaryClickControlSpecs(),
            connected = connected,
            onCommandSelected = viewModel::send
        )
        PcCommandButtonRow(
            specs = pcScrollControlSpecs(),
            connected = connected,
            onCommandSelected = viewModel::send
        )
        PcMovementSizeSection(
            selectedSize = uiState.selectedMovementSize,
            onSizeSelected = viewModel::selectMovementSize
        )
    }
}
