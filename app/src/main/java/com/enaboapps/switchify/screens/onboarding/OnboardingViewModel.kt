package com.enaboapps.switchify.screens.onboarding

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enaboapps.switchify.backend.preferences.PreferenceManager
import com.enaboapps.switchify.service.utils.ServiceUtils
import com.enaboapps.switchify.switches.SwitchEventStore
import com.enaboapps.switchify.utils.LogEvent
import com.enaboapps.switchify.utils.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class OnboardingUiState(
    val currentStep: OnboardingStep = OnboardingStep.WELCOME,
    val isNewUser: Boolean? = null,
    val userType: UserType? = null,
    val switchesValid: Boolean = false,
    val accessibilityEnabled: Boolean = false,
    val progress: Float = 0f
)

enum class OnboardingStep {
    WELCOME,
    USER_TYPE,
    SCAN_MODE_EXPLANATION,
    HEAD_CONTROL_EXPLANATION,
    SWITCH_SETUP,
    ACCESSIBILITY_SERVICE,
    PRO_BENEFITS,
    PRACTICE
}

enum class UserType {
    USER,
    SPECIALIST,
    CARER_FAMILY,
    OTHER
}

class OnboardingViewModel(context: Context) : ViewModel() {
    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState

    private val switchEventStore = SwitchEventStore.getInstance()
    private val serviceUtils = ServiceUtils()
    private val preferenceManager = PreferenceManager(context.applicationContext)
    private val applicationContext = context.applicationContext

    private val stepOrder = listOf(
        OnboardingStep.WELCOME,
        OnboardingStep.USER_TYPE,
        OnboardingStep.SCAN_MODE_EXPLANATION,
        OnboardingStep.HEAD_CONTROL_EXPLANATION,
        OnboardingStep.SWITCH_SETUP,
        OnboardingStep.ACCESSIBILITY_SERVICE,
        OnboardingStep.PRO_BENEFITS,
        OnboardingStep.PRACTICE
    )

    fun init() {
        // Restore saved onboarding step
        val savedStep = preferenceManager.getStringValue(
            PreferenceManager.PREFERENCE_KEY_ONBOARDING_CURRENT_STEP,
            OnboardingStep.WELCOME.name
        )
        val savedUserType = preferenceManager.getStringValue(
            PreferenceManager.PREFERENCE_KEY_ONBOARDING_USER_TYPE,
            ""
        )
        val savedIsNewUser = preferenceManager.getBooleanValue(
            PreferenceManager.PREFERENCE_KEY_ONBOARDING_IS_NEW_USER,
            false
        )

        try {
            val step = OnboardingStep.valueOf(savedStep)
            val userType = if (savedUserType.isNotEmpty()) UserType.valueOf(savedUserType) else null

            _uiState.update {
                it.copy(
                    currentStep = step,
                    userType = userType,
                    isNewUser = if (savedIsNewUser) true else null,
                    progress = calculateProgress(step)
                )
            }
        } catch (e: IllegalArgumentException) {
            // If saved step is invalid, start from beginning
            _uiState.update { it.copy(currentStep = OnboardingStep.WELCOME) }
        }

        checkSwitches()
        checkAccessibilityService()
    }

    private fun calculateProgress(step: OnboardingStep): Float {
        val currentIndex = stepOrder.indexOf(step)
        return if (currentIndex >= 0) (currentIndex + 1).toFloat() / stepOrder.size else 0f
    }

    fun setNewUser(isNew: Boolean) {
        _uiState.update { it.copy(isNewUser = isNew) }
        preferenceManager.setBooleanValue(
            PreferenceManager.PREFERENCE_KEY_ONBOARDING_IS_NEW_USER,
            isNew
        )
    }

    fun setUserType(userType: UserType) {
        _uiState.update { it.copy(userType = userType) }
        preferenceManager.setStringValue(
            PreferenceManager.PREFERENCE_KEY_ONBOARDING_USER_TYPE,
            userType.name
        )

        // Log analytics event
        val event = when (userType) {
            UserType.USER -> LogEvent.OnboardingUserTypeEndUser
            UserType.SPECIALIST -> LogEvent.OnboardingUserTypeSpecialist
            UserType.CARER_FAMILY -> LogEvent.OnboardingUserTypeCarerFamily
            UserType.OTHER -> LogEvent.OnboardingUserTypeOther
        }
        Logger.log(event)
    }

    fun nextStep() {
        viewModelScope.launch {
            val currentIndex = stepOrder.indexOf(_uiState.value.currentStep)
            if (currentIndex < stepOrder.size - 1) {
                val nextStep = stepOrder[currentIndex + 1]
                val progress = (currentIndex + 2).toFloat() / stepOrder.size

                _uiState.update {
                    it.copy(
                        currentStep = nextStep,
                        progress = progress
                    )
                }

                // Save the current step
                preferenceManager.setStringValue(
                    PreferenceManager.PREFERENCE_KEY_ONBOARDING_CURRENT_STEP,
                    nextStep.name
                )

                // Check status when moving to relevant steps
                when (nextStep) {
                    OnboardingStep.SWITCH_SETUP -> checkSwitches()
                    OnboardingStep.ACCESSIBILITY_SERVICE -> checkAccessibilityService()
                    else -> {}
                }
            }
        }
    }

    fun previousStep() {
        viewModelScope.launch {
            val currentIndex = stepOrder.indexOf(_uiState.value.currentStep)
            if (currentIndex > 0) {
                val previousStep = stepOrder[currentIndex - 1]
                val progress = currentIndex.toFloat() / stepOrder.size

                _uiState.update {
                    it.copy(
                        currentStep = previousStep,
                        progress = progress
                    )
                }

                // Save the current step
                preferenceManager.setStringValue(
                    PreferenceManager.PREFERENCE_KEY_ONBOARDING_CURRENT_STEP,
                    previousStep.name
                )

                // Check status when moving to relevant steps
                when (previousStep) {
                    OnboardingStep.SWITCH_SETUP -> checkSwitches()
                    OnboardingStep.ACCESSIBILITY_SERVICE -> checkAccessibilityService()
                    else -> {}
                }
            }
        }
    }

    fun checkSwitches() {
        val invalidReason = switchEventStore.isConfigInvalid(applicationContext)
        _uiState.update { it.copy(switchesValid = invalidReason == null) }
    }

    fun checkAccessibilityService() {
        val isEnabled = serviceUtils.isAccessibilityServiceEnabled(applicationContext)
        _uiState.update { it.copy(accessibilityEnabled = isEnabled) }
    }

    fun completeOnboarding() {
        viewModelScope.launch {
            preferenceManager.setSetupComplete()
            // Clear onboarding state since it's completed
            preferenceManager.setStringValue(
                PreferenceManager.PREFERENCE_KEY_ONBOARDING_CURRENT_STEP,
                ""
            )
            preferenceManager.setStringValue(
                PreferenceManager.PREFERENCE_KEY_ONBOARDING_USER_TYPE,
                ""
            )
            preferenceManager.setBooleanValue(
                PreferenceManager.PREFERENCE_KEY_ONBOARDING_IS_NEW_USER,
                false
            )
            Logger.log(LogEvent.OnboardingCompleted)
        }
    }
}