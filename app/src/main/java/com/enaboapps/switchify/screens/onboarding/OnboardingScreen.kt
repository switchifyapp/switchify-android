package com.enaboapps.switchify.screens.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.enaboapps.switchify.components.BaseView
import com.enaboapps.switchify.nav.NavigationRoute
import com.enaboapps.switchify.screens.onboarding.steps.*
import com.enaboapps.switchify.service.utils.ServiceUtils
import com.enaboapps.switchify.R

@Composable
fun OnboardingScreen(navController: NavController) {
    val context = LocalContext.current
    val viewModel = remember { OnboardingViewModel(context) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.init()
    }

    BaseView(
        titleResId = R.string.onboarding_title,
        navController = navController,
        enableScroll = false,
        showBackButton = uiState.currentStep != OnboardingStep.WELCOME,
        onBackPressed = { viewModel.previousStep() }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Progress indicator at the top
            if (uiState.currentStep != OnboardingStep.WELCOME) {
                LinearProgressIndicator(
                    progress = { uiState.progress },
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(horizontal = 32.dp, vertical = 16.dp),
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            // Animated content based on current step
            AnimatedVisibility(
                visible = true,
                enter = slideInHorizontally { it } + fadeIn(),
                exit = slideOutHorizontally { -it } + fadeOut()
            ) {
                when (uiState.currentStep) {
                    OnboardingStep.WELCOME -> WelcomeStep(
                        isNewUser = uiState.isNewUser,
                        onNewUserChoice = { isNew ->
                            viewModel.setNewUser(isNew)
                            if (isNew) {
                                viewModel.nextStep()
                            } else {
                                navController.navigate(NavigationRoute.SignIn.name)
                            }
                        }
                    )

                    OnboardingStep.USER_TYPE -> UserTypeStep(
                        onUserTypeSelected = { userType ->
                            viewModel.setUserType(userType)
                            viewModel.nextStep()
                        }
                    )

                    OnboardingStep.SCAN_MODE_EXPLANATION -> ScanModeExplanationStep(
                        onContinue = { viewModel.nextStep() }
                    )

                    OnboardingStep.SWITCH_SETUP -> SwitchSetupStep(
                        navController = navController,
                        switchesValid = uiState.switchesValid,
                        onSwitchesConfigured = {
                            viewModel.checkSwitches()
                        },
                        onContinue = { viewModel.nextStep() }
                    )

                    OnboardingStep.ACCESSIBILITY_SERVICE -> AccessibilityExplanationStep(
                        isEnabled = uiState.accessibilityEnabled,
                        onEnableService = {
                            ServiceUtils().openAccessibilitySettings(context)
                        },
                        onContinue = { viewModel.nextStep() },
                        onRefreshStatus = { viewModel.checkAccessibilityService() }
                    )

                    OnboardingStep.PRACTICE -> PracticeStep(
                        onComplete = {
                            viewModel.completeOnboarding()
                            navController.popBackStack()
                        }
                    )
                }
            }
        }
    }
}