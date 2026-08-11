package com.enaboapps.switchify.screens.pc

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
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
        viewModel.onPcUiPaused(
            endTypingSession = !isChangingConfigurations &&
                !viewModel.uiState.value.liveTypingPaused
        )
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

private sealed interface PcTypingExitIntent {
    data object Close : PcTypingExitIntent
    data object DismissQuickInput : PcTypingExitIntent
    data object OpenPcSwitcher : PcTypingExitIntent
    data class SelectSurface(val surface: PcControlSurface) : PcTypingExitIntent
    data class SelectMode(val mode: PcTypingMode) : PcTypingExitIntent
}

@Composable
private fun PcMouseControlScreen(
    viewModel: PcMouseControlViewModel,
    onClose: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val pcSwitcherState by viewModel.pcSwitcherState.collectAsState()
    val surfaceEnabled = uiState.connectedDisplayName != null && !uiState.isBusy
    var closeConfirmationVisible by rememberSaveable { mutableStateOf(false) }
    var quickInputVisible by rememberSaveable { mutableStateOf(false) }
    var pendingTypingExit by remember { mutableStateOf<PcTypingExitIntent?>(null) }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val liveTypingUiVisible =
        uiState.typingMode == PcTypingMode.Live &&
            uiState.supportsTextStreamInput &&
            (
                uiState.activeSurface == PcControlSurface.Typing ||
                    (quickInputVisible && shouldShowPcQuickInputButton(uiState.activeSurface))
                )
    val latestLiveTypingText by rememberUpdatedState(uiState.liveTypingText)
    DisposableEffect(liveTypingUiVisible) {
        if (liveTypingUiVisible) viewModel.startLiveTyping()
        onDispose {
            if (liveTypingUiVisible) {
                viewModel.retainAndStopLiveTyping(latestLiveTypingText)
            }
        }
    }
    val performTypingExit: (PcTypingExitIntent) -> Unit = { intent ->
        when (intent) {
            PcTypingExitIntent.Close -> closeConfirmationVisible = true
            PcTypingExitIntent.DismissQuickInput -> {
                viewModel.endLiveTypingSession()
                quickInputVisible = false
            }
            PcTypingExitIntent.OpenPcSwitcher -> viewModel.openSwitchPcChooser()
            is PcTypingExitIntent.SelectSurface -> {
                if (shouldDismissPcTypingKeyboard(uiState.activeSurface, intent.surface)) {
                    focusManager.clearFocus(force = true)
                    keyboardController?.hide()
                }
                viewModel.selectControlSurface(intent.surface)
            }
            is PcTypingExitIntent.SelectMode -> viewModel.selectTypingMode(intent.mode)
        }
    }
    val requestTypingExit: (PcTypingExitIntent) -> Unit = { intent ->
        if (shouldConfirmPausedLiveTypingExit(uiState.liveTypingPaused, uiState.typingMode)) {
            pendingTypingExit = intent
        } else {
            performTypingExit(intent)
        }
    }
    val requestClose = { requestTypingExit(PcTypingExitIntent.Close) }

    BackHandler(onBack = requestClose)

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
                            onSurfaceSelected = { surface ->
                                if (surface == uiState.activeSurface) {
                                    viewModel.selectControlSurface(surface)
                                } else {
                                    requestTypingExit(PcTypingExitIntent.SelectSurface(surface))
                                }
                            },
                            enabled = !uiState.isBusy,
                            onClose = requestClose,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                showBackButton = false
            )
        },
        bottomBar = {
            Column {
                AnimatedVisibility(
                    visible = shouldShowPcQuickInputButton(uiState.activeSurface)
                ) {
                    PcQuickInputButton(
                        enabled = surfaceEnabled,
                        onClick = { quickInputVisible = true }
                    )
                }
                PcSwitcherStrip(
                    connectedDisplayName = pcSwitcherState.connectedDisplayName
                        ?: uiState.connectedDisplayName,
                    enabled = !uiState.isBusy,
                    isDiscovering = pcSwitcherState.isDiscovering,
                    switching = pcSwitcherState.isPreparing ||
                        pcSwitcherState.switchingDesktopId != null,
                    onSwitchClick = {
                        requestTypingExit(PcTypingExitIntent.OpenPcSwitcher)
                    }
                )
            }
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
                                pointerSpeedScalePercent = uiState.pointerSpeedScalePercent,
                                pointerSpeedMinScalePercent = uiState.pointerSpeedMinScalePercent,
                                pointerSpeedMaxScalePercent = uiState.pointerSpeedMaxScalePercent,
                                pointerSpeedStepPercent = uiState.pointerSpeedStepPercent,
                                pointerSpeedSupported = uiState.pointerSpeedSupported && uiState.pointerSpeedSetSupported,
                                pointerSpeedControlEnabled = uiState.pointerSpeedSupported && uiState.pointerSpeedSetSupported && !uiState.isBusy,
                                pointerSpeedLabel = uiState.pointerSpeedPercentLabel,
                                onPointerSpeedSelected = viewModel::setPointerSpeed,
                                onCommandSelected = viewModel::sendMouseCommand
                            )
                        }
                        PcControlSurface.Typing -> PcTypingControlScreen(
                            typingText = uiState.typingText,
                            typingMessage = uiState.typingMessage,
                            typingDraftReviewWarning = uiState.typingDraftReviewWarning,
                            typingMode = uiState.typingMode,
                            draftPressEnterAfterSending = uiState.draftPressEnterAfterSending,
                            liveTypingAvailable = uiState.supportsTextStreamInput,
                            liveTypingPaused = uiState.liveTypingPaused,
                            liveTypingMessage = uiState.liveTypingMessage,
                            liveTypingAnnouncement = uiState.liveTypingAnnouncement,
                            liveTypingNotice = uiState.liveTypingNotice,
                            liveTypingText = uiState.liveTypingText,
                            enabled = surfaceEnabled,
                            onTypingModeSelected = { mode ->
                                if (mode == PcTypingMode.Draft) {
                                    requestTypingExit(PcTypingExitIntent.SelectMode(mode))
                                } else {
                                    viewModel.selectTypingMode(mode)
                                }
                            },
                            onDraftEnterChanged = viewModel::setDraftPressEnterAfterSending,
                            onTextChanged = viewModel::updateTypingText,
                            onSendDraft = viewModel::sendDraftText,
                            onClear = viewModel::clearTypingText,
                            onClearLiveField = viewModel::clearLiveTypingField,
                            onLiveTypingStarted = viewModel::startLiveTyping,
                            onLiveTypingStopped = viewModel::retainAndStopLiveTyping,
                            onLiveSessionTextChanged = viewModel::updateLiveTypingSessionText,
                            onLiveTextCommitted = viewModel::commitLiveTypingText,
                            onLiveEnterCommitted = viewModel::commitLiveTypingEnter,
                            onLiveKeyCommitted = viewModel::sendLiveTypingKey,
                            onLiveTypingRetry = viewModel::retryLiveTyping,
                            onMoveLiveToDraft = viewModel::moveLiveTypingToDraft,
                            onLiveTypingAnnouncementShown = viewModel::clearLiveTypingAnnouncement,
                            onLiveTypingNoticeShown = viewModel::clearLiveTypingNotice,
                            onKeySelected = viewModel::sendTypingKey,
                            manageLiveSessionLifecycle = false
                        )
                        PcControlSurface.Window -> PcWindowControlScreen(
                            enabled = surfaceEnabled,
                            platform = uiState.connectedPlatform,
                            monitorNavigationVisible = shouldShowPcDisplayNavigation(
                                uiState.displayNavigationSupported,
                                uiState.displayCount
                            ),
                            monitorNavigationEnabled = pcDisplayNavigationControlsEnabled(surfaceEnabled, uiState.isDragging),
                            activeModifiers = uiState.activeModifiers,
                            onModifierSelected = viewModel::toggleModifier,
                            onShortcutKeySelected = viewModel::sendShortcutKey,
                            onCommandSelected = viewModel::send
                        )
                    }
                }
            }
        }
    }

    if (pcSwitcherState.visible) {
        PcSwitcherDialog(
            rows = pcSwitcherState.rows,
            isDiscovering = pcSwitcherState.isDiscovering,
            switchingDesktopId = pcSwitcherState.switchingDesktopId,
            message = pcSwitcherState.message,
            onDismiss = viewModel::dismissSwitchPcChooser,
            onRefresh = viewModel::refreshSwitchPcChoices,
            onPcSelected = viewModel::switchToPc
        )
    }

    pcSwitcherState.approvalCode?.let { approvalCode ->
        PcSwitcherApprovalDialog(
            approvalCode = approvalCode,
            onCancel = viewModel::cancelSwitchPcPairing
        )
    }

    if (closeConfirmationVisible) {
        AlertDialog(
            onDismissRequest = { closeConfirmationVisible = false },
            title = { Text(text = stringResource(R.string.pc_control_close_confirm_title)) },
            text = { Text(text = stringResource(R.string.pc_control_close_confirm_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        closeConfirmationVisible = false
                        onClose()
                    }
                ) {
                    Text(text = stringResource(R.string.pc_control_close_confirm_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { closeConfirmationVisible = false }) {
                    Text(text = stringResource(R.string.pc_control_close_confirm_cancel))
                }
            }
        )
    }

    if (quickInputVisible && shouldShowPcQuickInputButton(uiState.activeSurface)) {
        PcQuickInputSheet(
            typingText = uiState.typingText,
            typingMessage = uiState.typingMessage,
            typingDraftReviewWarning = uiState.typingDraftReviewWarning,
            typingMode = uiState.typingMode,
            draftPressEnterAfterSending = uiState.draftPressEnterAfterSending,
            liveTypingAvailable = uiState.supportsTextStreamInput,
            liveTypingPaused = uiState.liveTypingPaused,
            liveTypingMessage = uiState.liveTypingMessage,
            liveTypingAnnouncement = uiState.liveTypingAnnouncement,
            liveTypingNotice = uiState.liveTypingNotice,
            liveTypingText = uiState.liveTypingText,
            connected = uiState.connectedDisplayName != null,
            enabled = surfaceEnabled,
            onTypingModeSelected = { mode ->
                if (mode == PcTypingMode.Draft) {
                    requestTypingExit(PcTypingExitIntent.SelectMode(mode))
                } else {
                    viewModel.selectTypingMode(mode)
                }
            },
            onDraftEnterChanged = viewModel::setDraftPressEnterAfterSending,
            onTextChanged = viewModel::updateTypingText,
            onSend = viewModel::sendDraftText,
            onClear = viewModel::clearTypingText,
            onClearLiveField = viewModel::clearLiveTypingField,
            onLiveTypingStarted = viewModel::startLiveTyping,
            onLiveTypingStopped = viewModel::retainAndStopLiveTyping,
            onLiveSessionTextChanged = viewModel::updateLiveTypingSessionText,
            onLiveTextCommitted = viewModel::commitLiveTypingText,
            onLiveEnterCommitted = viewModel::commitLiveTypingEnter,
            onLiveKeyCommitted = viewModel::sendLiveTypingKey,
            onLiveTypingRetry = viewModel::retryLiveTyping,
            onMoveLiveToDraft = viewModel::moveLiveTypingToDraft,
            onLiveTypingAnnouncementShown = viewModel::clearLiveTypingAnnouncement,
            onLiveTypingNoticeShown = viewModel::clearLiveTypingNotice,
            onKeySelected = viewModel::sendTypingKey,
            onOpenFullTypingControls = {
                quickInputVisible = false
                viewModel.selectControlSurface(PcControlSurface.Typing)
            },
            onDismissRequest = {
                requestTypingExit(PcTypingExitIntent.DismissQuickInput)
            },
            manageLiveSessionLifecycle = false
        )
    }

    pendingTypingExit?.let { exitIntent ->
        AlertDialog(
            onDismissRequest = { pendingTypingExit = null },
            title = { Text(stringResource(R.string.pc_typing_exit_title)) },
            text = { Text(stringResource(R.string.pc_typing_text_safe)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.moveLiveTypingToDraft()
                        pendingTypingExit = null
                        performTypingExit(exitIntent)
                    }
                ) {
                    Text(stringResource(R.string.pc_typing_save_as_draft))
                }
            },
            dismissButton = {
                Column {
                    TextButton(
                        onClick = {
                            pendingTypingExit = null
                        }
                    ) {
                        Text(stringResource(R.string.pc_typing_keep_typing))
                    }
                    TextButton(
                        onClick = {
                            viewModel.endLiveTypingSession()
                            pendingTypingExit = null
                            performTypingExit(exitIntent)
                        }
                    ) {
                        Text(stringResource(R.string.pc_typing_exit_discard))
                    }
                }
            }
        )
    }
}

internal fun shouldShowPcDisplayNavigation(supported: Boolean, displayCount: Int): Boolean {
    return supported && displayCount > 1
}

internal fun pcDisplayNavigationControlsEnabled(surfaceEnabled: Boolean, isDragging: Boolean): Boolean {
    return surfaceEnabled && !isDragging
}

internal fun shouldDismissPcTypingKeyboard(
    currentSurface: PcControlSurface,
    targetSurface: PcControlSurface
): Boolean {
    return currentSurface == PcControlSurface.Typing &&
        targetSurface != PcControlSurface.Typing
}

internal fun shouldConfirmPausedLiveTypingExit(
    liveTypingPaused: Boolean,
    typingMode: PcTypingMode
): Boolean {
    return liveTypingPaused && typingMode == PcTypingMode.Live
}
