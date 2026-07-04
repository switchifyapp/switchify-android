package com.enaboapps.switchify.screens.pc

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
        viewModel.onPcUiResumed()
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
        viewModel.onPcUiPaused()
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
    val surfaceEnabled = uiState.connectedDisplayName != null && !uiState.isBusy

    BackHandler(onBack = onClose)

    Scaffold(
        topBar = {
            NavBar(
                title = "",
                titleContent = {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(end = 8.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        PcControlSurfaceSwitcher(
                            selectedSurface = uiState.activeSurface,
                            onSurfaceSelected = viewModel::selectControlSurface,
                            enabled = !uiState.isBusy,
                            onClose = onClose,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                showBackButton = false
            )
        },
        bottomBar = {
            PcControlPcSwitchStrip(
                connectedDisplayName = uiState.switcherConnectedDisplayName ?: uiState.connectedDisplayName,
                enabled = !uiState.isBusy,
                isDiscovering = uiState.isDiscoveringSwitchPcs,
                switching = uiState.switchingDesktopId != null,
                onSwitchClick = viewModel::openSwitchPcChooser
            )
        }
    ) { paddingValues ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            color = MaterialTheme.colorScheme.background
        ) {
            AnimatedContent(
                targetState = uiState.activeSurface,
                transitionSpec = {
                    (fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) +
                            slideInVertically(
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioLowBouncy,
                                    stiffness = Spring.StiffnessMediumLow
                                ),
                                initialOffsetY = { it / 24 }
                            )) togetherWith
                        fadeOut(spring(stiffness = Spring.StiffnessMedium))
                },
                label = "pcControlSurface"
            ) { activeSurface ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    PcTransientMessage(message = uiState.message)
                    when (activeSurface) {
                        PcControlSurface.Mouse -> Column(modifier = Modifier.fillMaxWidth()) {
                            PcControlCommandGrid(
                                enabled = surfaceEnabled,
                                movementStep = uiState.movementStep,
                                onCommandSelected = viewModel::sendMouseCommand
                            )
                            PcMovementSizeSection(
                                selectedSize = uiState.selectedMovementSize,
                                onSizeSelected = viewModel::selectMovementSize,
                                enabled = !uiState.isBusy
                            )
                        }
                        PcControlSurface.Typing -> PcTypingControlScreen(
                            typingText = uiState.typingText,
                            typingMessage = uiState.typingMessage,
                            enabled = surfaceEnabled,
                            onTextChanged = viewModel::updateTypingText,
                            onSend = viewModel::sendTypedText,
                            onSendAndEnter = viewModel::sendTypedTextThenEnter,
                            onClear = viewModel::clearTypingText,
                            onKeySelected = viewModel::sendKey
                        )
                        PcControlSurface.Window -> PcWindowControlScreen(
                            enabled = surfaceEnabled,
                            activeModifiers = uiState.activeModifiers,
                            onModifierSelected = viewModel::toggleModifier,
                            onShortcutLetterSelected = viewModel::sendShortcutLetter,
                            onCommandSelected = viewModel::send
                        )
                    }
                }
            }
        }
    }

    if (uiState.switchPcChooserVisible) {
        PcSwitchPcDialog(
            rows = uiState.switchPcRows,
            isDiscovering = uiState.isDiscoveringSwitchPcs,
            switchingDesktopId = uiState.switchingDesktopId,
            onDismiss = viewModel::dismissSwitchPcChooser,
            onRefresh = viewModel::refreshSwitchPcChoices,
            onPcSelected = viewModel::switchToPc
        )
    }

    uiState.switchPcApprovalCode?.let { approvalCode ->
        PcSwitchPcApprovalDialog(
            approvalCode = approvalCode,
            onCancel = viewModel::cancelSwitchPcPairing
        )
    }
}
