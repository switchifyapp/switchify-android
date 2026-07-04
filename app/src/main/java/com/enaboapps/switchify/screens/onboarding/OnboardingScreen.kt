package com.enaboapps.switchify.screens.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.enaboapps.switchify.R
import com.enaboapps.switchify.components.BaseView
import com.enaboapps.switchify.components.NavBarAction
import com.enaboapps.switchify.nav.NavigationRoute
import com.enaboapps.switchify.screens.onboarding.steps.AccessibilityExplanationStep
import com.enaboapps.switchify.screens.onboarding.steps.HeadControlExplanationStep
import com.enaboapps.switchify.screens.onboarding.steps.PracticeStep
import com.enaboapps.switchify.screens.onboarding.steps.ProBenefitsStep
import com.enaboapps.switchify.screens.onboarding.steps.ScanModeExplanationStep
import com.enaboapps.switchify.screens.onboarding.steps.SwitchSetupStep
import com.enaboapps.switchify.screens.onboarding.steps.TelemetryConsentStep
import com.enaboapps.switchify.screens.onboarding.steps.UserTypeStep
import com.enaboapps.switchify.screens.onboarding.steps.WelcomeStep
import com.enaboapps.switchify.service.utils.ServiceUtils

@Composable
fun OnboardingScreen(navController: NavController) {
    val context = LocalContext.current
    val viewModel = remember { OnboardingViewModel(context) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.init()
    }

    // Determine skip actions based on current step
    val navBarActions = when (uiState.currentStep) {
        OnboardingStep.HEAD_CONTROL_EXPLANATION -> listOf(
            NavBarAction(
                textResId = R.string.onboarding_skip_for_now,
                onClick = { viewModel.nextStep() }
            )
        )

        OnboardingStep.SWITCH_SETUP -> listOf(
            NavBarAction(
                textResId = R.string.onboarding_skip_for_now,
                onClick = { viewModel.nextStep() }
            )
        )

        OnboardingStep.ACCESSIBILITY_SERVICE -> if (!uiState.accessibilityEnabled) {
            listOf(
                NavBarAction(
                    textResId = R.string.onboarding_skip_for_now,
                    onClick = { viewModel.nextStep() }
                )
            )
        } else emptyList()

        OnboardingStep.PRACTICE -> listOf(
            NavBarAction(
                textResId = R.string.onboarding_skip_practice,
                onClick = {
                    viewModel.completeOnboarding()
                    // Safely pop back stack - returns false if navigation fails
                    if (!navController.popBackStack()) {
                        // If popBackStack fails, navigate to Home explicitly
                        navController.navigate(NavigationRoute.Home.name) {
                            popUpTo(navController.graph.startDestinationId) {
                                inclusive = false
                            }
                            launchSingleTop = true
                        }
                    }
                }
            )
        )

        else -> emptyList()
    }

    BaseView(
        titleResId = R.string.onboarding_title,
        navController = navController,
        navBarActions = navBarActions,
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
            AnimatedContent(
                targetState = uiState.currentStep,
                transitionSpec = {
                    val forward = targetState.ordinal >= initialState.ordinal
                    val slideSpec = spring<IntOffset>(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                    (fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) +
                            slideInHorizontally(slideSpec) { if (forward) it / 6 else -it / 6 }) togetherWith
                        (fadeOut(spring(stiffness = Spring.StiffnessMedium)) +
                                slideOutHorizontally(slideSpec) { if (forward) -it / 6 else it / 6 })
                },
                label = "onboardingStep"
            ) { currentStep ->
                when (currentStep) {
                    OnboardingStep.WELCOME -> WelcomeStep(
                        isNewUser = uiState.isNewUser,
                        onNewUserChoice = { isNew ->
                            viewModel.setNewUser(isNew)
                            if (isNew) {
                                viewModel.nextStep()
                            } else {
                                navController.navigate(NavigationRoute.Authentication.name)
                            }
                        }
                    )

                    OnboardingStep.TELEMETRY_CONSENT -> TelemetryConsentStep(
                        onChoice = { accepted ->
                            viewModel.setTelemetryConsent(accepted)
                            viewModel.nextStep()
                        }
                    )

                    OnboardingStep.USER_TYPE -> UserTypeStep(
                        onUserTypeSelected = { userType ->
                            viewModel.setUserType(userType)
                            viewModel.nextStep()
                        }
                    )

                    OnboardingStep.SCAN_MODE_EXPLANATION -> ScanModeExplanationStep(
                        navController = navController,
                        onContinue = { viewModel.nextStep() }
                    )

                    OnboardingStep.HEAD_CONTROL_EXPLANATION -> HeadControlExplanationStep(
                        navController = navController,
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

                    OnboardingStep.PRO_BENEFITS -> ProBenefitsStep(
                        onLearnMore = {
                            navController.navigate(NavigationRoute.Paywall.name)
                        },
                        onContinue = { viewModel.nextStep() }
                    )

                    OnboardingStep.PRACTICE -> PracticeStep(
                        onComplete = {
                            viewModel.completeOnboarding()
                            // Safely pop back stack - returns false if navigation fails
                            if (!navController.popBackStack()) {
                                // If popBackStack fails, navigate to Home explicitly
                                navController.navigate(NavigationRoute.Home.name) {
                                    popUpTo(navController.graph.startDestinationId) {
                                        inclusive = false
                                    }
                                    launchSingleTop = true
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}