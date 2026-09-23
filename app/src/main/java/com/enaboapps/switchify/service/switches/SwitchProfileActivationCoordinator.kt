package com.enaboapps.switchify.service.switches

import android.content.Context
import com.enaboapps.switchify.backend.preferences.PreferenceManager
import com.enaboapps.switchify.R
import com.enaboapps.switchify.service.camera.CameraPermissionManager
import com.enaboapps.switchify.service.core.ServiceBridge
import com.enaboapps.switchify.service.core.ServiceCore
import com.enaboapps.switchify.service.window.MessageSeverity
import com.enaboapps.switchify.service.window.ServiceMessageHUD
import com.enaboapps.switchify.switches.RequiredActionsPolicy
import com.enaboapps.switchify.switches.SWITCH_EVENT_TYPE_CAMERA
import com.enaboapps.switchify.switches.SupportedActionsPolicy
import com.enaboapps.switchify.switches.SwitchAction
import com.enaboapps.switchify.switches.SwitchEventStore
import com.enaboapps.switchify.switches.profiles.SwitchProfile
import com.enaboapps.switchify.switches.profiles.SwitchProfileActivationState
import com.enaboapps.switchify.switches.profiles.SwitchProfileConfirmationMode
import com.enaboapps.switchify.switches.profiles.SwitchProfileRepository
import com.enaboapps.switchify.switches.profiles.SwitchProfileValidator
import com.enaboapps.switchify.switches.SwitchHoldPolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class SwitchProfileActivationCoordinator(
    private val context: Context,
    private val switchEventProvider: SwitchEventProvider,
    private val scope: CoroutineScope,
    private val repository: SwitchProfileRepository = SwitchProfileRepository.getInstance(context),
    private val menuActions: SwitchProfileMenuActions = SwitchProfileMenuActions(),
    private val now: () -> Long = System::currentTimeMillis
) {
    private val _state = MutableStateFlow<SwitchProfileActivationState>(SwitchProfileActivationState.Idle)
    val state: StateFlow<SwitchProfileActivationState> = _state.asStateFlow()
    private val stateLock = Any()
    private var timeoutJob: Job? = null
    private var confirmationStarted = false
    private var activationGeneration = 0L

    fun begin(
        profileId: String,
        confirmationMode: SwitchProfileConfirmationMode =
            SwitchProfileConfirmationMode.INPUT_ACTION
    ) {
        val generation = synchronized(stateLock) {
            if (confirmationStarted) return
            activationGeneration += 1
            timeoutJob?.cancel()
            timeoutJob = null
            confirmationStarted = false
            if (_state.value is SwitchProfileActivationState.Verifying) {
                _state.value = SwitchProfileActivationState.Idle
            }
            switchEventProvider.clearStage()
            activationGeneration
        }
        scope.launch {
            repository.initialize()
            if (!isCurrent(generation)) return@launch
            val profile = repository.profile(profileId)
            if (profile == null) {
                fail(generation, profileId, "not_found")
                return@launch
            }
            if (repository.document.value.activeProfileId == profileId) {
                fail(generation, profileId, "already_active")
                return@launch
            }
            val validation = validate(profile)
            if (!validation.isValid) {
                fail(
                    generation,
                    profileId,
                    "invalid",
                    validation.missingActionIds,
                    validation.unsupportedActionIds
                )
                return@launch
            }
            if (profile.switches.any { it.type == SWITCH_EVENT_TYPE_CAMERA } &&
                !CameraPermissionManager.getInstance(context).hasPermission()
            ) {
                fail(generation, profileId, "camera_permission")
                return@launch
            }
            val expiresAt = now() + VERIFICATION_TIMEOUT_MS
            val started = synchronized(stateLock) {
                if (generation != activationGeneration || confirmationStarted) {
                    false
                } else {
                    switchEventProvider.stage(profile.switches)
                    _state.value = SwitchProfileActivationState.Verifying(
                        profile,
                        expiresAt,
                        confirmationMode
                    )
                    ServiceBridge.emitEvent(
                        ServiceBridge.ServiceEvent.SwitchProfileVerificationStarted(
                            profile.id,
                            profile.name,
                            expiresAt,
                            confirmationMode == SwitchProfileConfirmationMode.MENU
                        )
                    )
                    true
                }
            }
            if (!started) return@launch
            ServiceCore.getCameraManager()?.evaluateAndUpdateCameraState()
            if (confirmationMode == SwitchProfileConfirmationMode.MENU) {
                menuActions.open(profile.name) {
                    isCurrentVerification(generation, profile.id)
                }
            }
            val job = scope.launch {
                while (true) {
                    val remaining = (expiresAt - now()).coerceAtLeast(0L)
                    if (remaining == 0L) break
                    showVerificationPrompt(
                        SwitchProfileActivationState.Verifying(
                            profile,
                            expiresAt,
                            confirmationMode
                        )
                    )
                    delay(1000L)
                }
                cancel(generation, "timeout")
            }
            synchronized(stateLock) {
                if (generation == activationGeneration && !confirmationStarted) {
                    timeoutJob = job
                } else {
                    job.cancel()
                }
            }
        }
    }

    fun intercept(action: SwitchAction): Boolean {
        var confirmation: Pair<SwitchProfileActivationState.Verifying, Long>? = null
        val consumed = synchronized(stateLock) {
            val currentState = _state.value
            if (confirmationStarted && currentState is SwitchProfileActivationState.Verifying) {
                true
            } else when (SwitchProfileVerificationInputPolicy.decide(currentState, action)) {
                SwitchProfileVerificationInputDecision.PASS_THROUGH -> false
                SwitchProfileVerificationInputDecision.CONSUME -> true
                SwitchProfileVerificationInputDecision.CONFIRM -> {
                    if (currentState is SwitchProfileActivationState.Verifying &&
                        !confirmationStarted
                    ) {
                        confirmationStarted = true
                        timeoutJob?.cancel()
                        timeoutJob = null
                        confirmation = currentState to activationGeneration
                    }
                    true
                }
            }
        }
        confirmation?.let { (verifying, generation) ->
            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                confirm(verifying, generation)
            }
        }
        return consumed
    }

    fun confirmFromMenu() {
        val confirmation = synchronized(stateLock) {
            val currentState = _state.value
            if (currentState !is SwitchProfileActivationState.Verifying ||
                currentState.confirmationMode != SwitchProfileConfirmationMode.MENU ||
                confirmationStarted
            ) {
                null
            } else {
                confirmationStarted = true
                timeoutJob?.cancel()
                timeoutJob = null
                currentState to activationGeneration
            }
        } ?: return
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            confirm(confirmation.first, confirmation.second)
        }
    }

    fun repeatMenuPrompt() {
        val verifying = synchronized(stateLock) {
            (_state.value as? SwitchProfileActivationState.Verifying)
                ?.takeIf { it.confirmationMode == SwitchProfileConfirmationMode.MENU }
        } ?: return
        showVerificationPrompt(verifying)
    }

    fun cancel(
        reason: String = "cancelled",
        showMessage: Boolean = true,
        dismissConfirmationMenu: Boolean = true
    ) {
        var confirmationMode = SwitchProfileConfirmationMode.INPUT_ACTION
        val wasVerifying = synchronized(stateLock) {
            if (confirmationStarted) return
            activationGeneration += 1
            val currentState = _state.value
            val verifying = currentState is SwitchProfileActivationState.Verifying
            if (currentState is SwitchProfileActivationState.Verifying) {
                confirmationMode = currentState.confirmationMode
            }
            timeoutJob?.cancel()
            timeoutJob = null
            confirmationStarted = false
            if (verifying) _state.value = SwitchProfileActivationState.Idle
            if (verifying) switchEventProvider.clearStage()
            verifying
        }
        if (!wasVerifying) return
        ServiceCore.getCameraManager()?.evaluateAndUpdateCameraState()
        ServiceMessageHUD.instance.clearMessage()
        if (dismissConfirmationMenu && confirmationMode == SwitchProfileConfirmationMode.MENU) {
            scope.launch {
                menuActions.dismiss()
            }
        }
        ServiceBridge.emitEvent(ServiceBridge.ServiceEvent.SwitchProfileActivationCancelled)
        if (showMessage) {
            ServiceMessageHUD.instance.showMessage(
                if (reason == "timeout") R.string.switch_profile_verification_timed_out
                else R.string.switch_profile_verification_cancelled,
                ServiceMessageHUD.MessageType.DISAPPEARING,
                severity = MessageSeverity.Warning
            )
        }
    }

    private fun cancel(generation: Long, reason: String) {
        val shouldCancel = synchronized(stateLock) {
            generation == activationGeneration && !confirmationStarted &&
                _state.value is SwitchProfileActivationState.Verifying
        }
        if (shouldCancel) cancel(reason)
    }

    private suspend fun confirm(
        verifying: SwitchProfileActivationState.Verifying,
        generation: Long
    ) = withContext(NonCancellable) {
        var failureReason = "storage"
        var missingActionIds = emptySet<Int>()
        var unsupportedActionIds = emptySet<Int>()
        val store = SwitchEventStore.getInstance()
        val activated = repository.commitActiveProfile(
            profileId = verifying.profile.id,
            prepare = { target, previous ->
                if (!isCurrentConfirmation(generation)) return@commitActiveProfile false
                val validation = validate(target)
                if (!validation.isValid) {
                    failureReason = "invalid"
                    missingActionIds = validation.missingActionIds
                    unsupportedActionIds = validation.unsupportedActionIds
                    return@commitActiveProfile false
                }
                if (target.switches.any { it.type == SWITCH_EVENT_TYPE_CAMERA } &&
                    !CameraPermissionManager.getInstance(context).hasPermission()
                ) {
                    failureReason = "camera_permission"
                    return@commitActiveProfile false
                }
                val promoted = runCatching {
                    switchEventProvider.promoteStage(target.switches)
                    store.replaceActiveProfileCache(target.switches)
                }.isSuccess
                if (!promoted) {
                    failureReason = "runtime_reload"
                    runCatching { switchEventProvider.promoteStage(previous.switches) }
                    store.replaceActiveProfileCache(previous.switches)
                }
                promoted
            },
            rollback = { previous ->
                runCatching { switchEventProvider.promoteStage(previous.switches) }
                store.replaceActiveProfileCache(previous.switches)
            }
        )
        if (activated == null) {
            fail(
                generation,
                verifying.profile.id,
                failureReason,
                missingActionIds,
                unsupportedActionIds,
                verifying.confirmationMode
            )
            return@withContext
        }
        ServiceCore.getCameraManager()?.evaluateAndUpdateCameraState()
        synchronized(stateLock) {
            _state.value = SwitchProfileActivationState.Activated(activated)
        }
        ServiceBridge.emitEvent(
            ServiceBridge.ServiceEvent.SwitchProfileActivated(
                activated.id,
                activated.name
            )
        )
        ServiceBridge.emitEvent(ServiceBridge.ServiceEvent.SwitchProfilesUpdated)
        ServiceMessageHUD.instance.clearMessage()
        if (verifying.confirmationMode == SwitchProfileConfirmationMode.MENU) {
            menuActions.closeAll()
        }
        ServiceMessageHUD.instance.showMessage(
            R.string.switch_profile_activated,
            arrayOf(activated.name),
            ServiceMessageHUD.MessageType.DISAPPEARING,
            severity = MessageSeverity.Success
        )
        synchronized(stateLock) {
            if (generation == activationGeneration) {
                _state.value = SwitchProfileActivationState.Idle
                confirmationStarted = false
            }
        }
    }

    private suspend fun fail(
        generation: Long,
        profileId: String,
        reason: String,
        missingActionIds: Set<Int> = emptySet(),
        unsupportedActionIds: Set<Int> = emptySet(),
        confirmationMode: SwitchProfileConfirmationMode =
            SwitchProfileConfirmationMode.INPUT_ACTION
    ) {
        synchronized(stateLock) {
            if (generation != activationGeneration) return
            timeoutJob?.cancel()
            timeoutJob = null
            switchEventProvider.clearStage()
            confirmationStarted = true
            _state.value = SwitchProfileActivationState.Failed(profileId, reason)
        }
        ServiceCore.getCameraManager()?.evaluateAndUpdateCameraState()
        ServiceMessageHUD.instance.clearMessage()
        if (confirmationMode == SwitchProfileConfirmationMode.MENU) {
            menuActions.dismiss()
        }
        ServiceBridge.emitEvent(
            ServiceBridge.ServiceEvent.SwitchProfileActivationFailed(
                profileId,
                reason,
                missingActionIds,
                unsupportedActionIds
            )
        )
        val message = when (reason) {
            "camera_permission" -> context.getString(R.string.switch_profile_camera_permission_required)
            "invalid" -> when {
                missingActionIds.isNotEmpty() -> context.getString(
                    R.string.switch_profile_missing_actions,
                    missingActionIds.joinToString { SwitchAction(it).getActionName() }
                )
                else -> context.getString(
                    R.string.switch_profile_unsupported_actions,
                    unsupportedActionIds.joinToString { SwitchAction(it).getActionName() }
                )
            }
            else -> context.getString(R.string.switch_profile_activation_failed)
        }
        ServiceMessageHUD.instance.showMessageText(
            message,
            ServiceMessageHUD.MessageType.DISAPPEARING,
            severity = MessageSeverity.Error
        )
        synchronized(stateLock) {
            if (generation == activationGeneration) {
                _state.value = SwitchProfileActivationState.Idle
                confirmationStarted = false
            }
        }
    }

    private fun validate(profile: SwitchProfile) =
        SwitchProfileValidator.validate(
            profile.switches,
            RequiredActionsPolicy.requiredActionIds(context),
            SupportedActionsPolicy.supportedActionIds(context) + SwitchAction.ACTION_NONE,
            SwitchHoldPolicy.isEnabled(PreferenceManager(context))
        )

    private fun showVerificationPrompt(verifying: SwitchProfileActivationState.Verifying) {
        val remainingSeconds = ((verifying.expiresAtMillis - now()).coerceAtLeast(0L) + 999L) / 1000L
        val message = if (verifying.confirmationMode == SwitchProfileConfirmationMode.MENU) {
            context.resources.getQuantityString(
                R.plurals.switch_profile_menu_verification_prompt,
                remainingSeconds.toInt(),
                verifying.profile.name,
                remainingSeconds
            )
        } else {
            context.resources.getQuantityString(
                R.plurals.switch_profile_verification_prompt,
                remainingSeconds.toInt(),
                verifying.profile.name,
                remainingSeconds
            )
        }
        ServiceMessageHUD.instance.showMessageText(
            message,
            ServiceMessageHUD.MessageType.PERMANENT,
            severity = MessageSeverity.Info
        )
    }

    private fun isCurrent(generation: Long): Boolean = synchronized(stateLock) {
        generation == activationGeneration && !confirmationStarted
    }

    private fun isCurrentConfirmation(generation: Long): Boolean = synchronized(stateLock) {
        generation == activationGeneration && confirmationStarted
    }

    private fun isCurrentVerification(generation: Long, profileId: String): Boolean =
        synchronized(stateLock) {
            val currentState = _state.value
            generation == activationGeneration &&
                !confirmationStarted &&
                currentState is SwitchProfileActivationState.Verifying &&
                currentState.profile.id == profileId &&
                currentState.confirmationMode == SwitchProfileConfirmationMode.MENU
        }

    private companion object {
        const val VERIFICATION_TIMEOUT_MS = 60_000L
    }
}
