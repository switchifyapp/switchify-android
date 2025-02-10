package com.enaboapps.switchify.screens.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.enaboapps.switchify.R
import com.enaboapps.switchify.auth.AuthManager
import com.enaboapps.switchify.backend.preferences.PreferenceManager
import com.enaboapps.switchify.components.BaseView
import com.enaboapps.switchify.components.FullWidthButton
import com.enaboapps.switchify.keyboard.utils.KeyboardUtils
import com.enaboapps.switchify.nav.NavigationRoute
import com.enaboapps.switchify.screens.settings.scanning.ScanModeSelectionSection
import com.enaboapps.switchify.service.utils.ServiceUtils
import com.enaboapps.switchify.switches.SwitchConfigInvalidBanner
import com.enaboapps.switchify.switches.SwitchEventStore

private object SetupStrings {
    const val SETUP = "Setup"
    const val WELCOME = "Welcome to Switchify!"
    const val EDIT_SWITCHES = "Edit Switches"
    const val SKIP_SETUP = "I'll Skip The Setup"
    const val SIGN_IN = "Sign In"
    const val SIGN_IN_PROMPT = "Used Switchify? Sign in to access your settings."
    const val ACCESSIBILITY_PROMPT =
        "To use Switchify effectively, please enable the Accessibility Service."
    const val KEYBOARD_PROMPT =
        "To use Switchify effectively, please enable the Switchify Keyboard in your device settings."
    const val LETS_GO = "Let's Go"
    const val SETUP_COMPLETE = "You're all set up!"
    const val FINISH = "Finish"
}

@Composable
fun SetupScreen(
    navController: NavController
) {
    val context = LocalContext.current
    val switchEventStore = remember {
        SwitchEventStore.getInstance()
    }
    val serviceUtils = ServiceUtils()
    val keyboardUtils = KeyboardUtils
    val preferenceManager = PreferenceManager(context)

    val viewModel = remember {
        SetupScreenModel(
            switchEventStore,
            serviceUtils,
            keyboardUtils,
            preferenceManager
        )
    }

    LaunchedEffect(Unit) {
        viewModel.init(context)
    }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    SetupScreenContent(
        uiState = uiState,
        onEditSwitches = { navController.navigate(NavigationRoute.Switches.name) },
        onEnableAccessibilityService = { navController.navigate(NavigationRoute.EnableAccessibilityService.name) },
        onEnableSwitchifyKeyboard = { navController.navigate(NavigationRoute.EnableSwitchifyKeyboard.name) },
        onSkipSetup = {
            viewModel.setSetupComplete()
            navController.popBackStack()
        },
        onSignIn = {
            viewModel.setSetupComplete()
            navController.navigate(NavigationRoute.SignIn.name)
        },
        onScanModeChange = { viewModel.checkSwitches(context) },
        onFinish = {
            viewModel.setSetupComplete()
            navController.popBackStack()
        },
        navController = navController
    )
}

@Composable
private fun SetupScreenContent(
    uiState: SetupScreenUiState,
    onEditSwitches: () -> Unit,
    onEnableAccessibilityService: () -> Unit,
    onEnableSwitchifyKeyboard: () -> Unit,
    onSkipSetup: () -> Unit,
    onSignIn: () -> Unit,
    onScanModeChange: (String) -> Unit,
    onFinish: () -> Unit,
    navController: NavController
) {
    BaseView(
        title = SetupStrings.SETUP,
        navController = navController
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = SetupStrings.WELCOME,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 20.dp)
            )

            when {
                uiState.switchesInvalidReason != null -> SwitchesInvalidContent(
                    switchesInvalidReason = uiState.switchesInvalidReason,
                    isUserSignedIn = AuthManager.instance.isUserSignedIn(),
                    onEditSwitches = onEditSwitches,
                    onSkipSetup = onSkipSetup,
                    onSignIn = onSignIn,
                    onScanModeChange = onScanModeChange
                )

                !uiState.isAccessibilityServiceEnabled -> AccessibilityServiceContent(
                    onEnable = onEnableAccessibilityService,
                    onSkip = onSkipSetup
                )

                !uiState.isSwitchifyKeyboardEnabled -> KeyboardEnableContent(
                    onEnable = onEnableSwitchifyKeyboard,
                    onSkip = onSkipSetup
                )

                else -> SetupCompleteContent(onFinish = onFinish)
            }
        }
    }
}

@Composable
private fun SwitchesInvalidContent(
    switchesInvalidReason: String,
    isUserSignedIn: Boolean,
    onEditSwitches: () -> Unit,
    onSkipSetup: () -> Unit,
    onSignIn: () -> Unit,
    onScanModeChange: (String) -> Unit
) {
    ScanModeSelectionSection(onChange = onScanModeChange)
    SwitchConfigInvalidBanner(switchesInvalidReason)
    FullWidthButton(
        text = SetupStrings.EDIT_SWITCHES,
        onClick = onEditSwitches
    )
    FullWidthButton(
        text = SetupStrings.SKIP_SETUP,
        onClick = onSkipSetup
    )

    if (!isUserSignedIn) {
        SignInSection(onSignIn = onSignIn)
    }
}

@Composable
private fun SignInSection(onSignIn: () -> Unit) {
    Spacer(modifier = Modifier.height(20.dp))
    Text(
        text = SetupStrings.SIGN_IN_PROMPT,
        modifier = Modifier.padding(bottom = 4.dp)
    )
    FullWidthButton(
        text = SetupStrings.SIGN_IN,
        onClick = onSignIn
    )
}

@Composable
private fun AccessibilityServiceContent(
    onEnable: () -> Unit,
    onSkip: () -> Unit
) {
    val context = LocalContext.current
    Text(
        text = SetupStrings.ACCESSIBILITY_PROMPT,
        modifier = Modifier.padding(bottom = 20.dp)
    )
    Text(
        text = context.getString(R.string.accessibility_service_disclosure),
        modifier = Modifier.padding(bottom = 20.dp)
    )
    FullWidthButton(
        text = SetupStrings.LETS_GO,
        onClick = onEnable
    )
    FullWidthButton(
        text = SetupStrings.SKIP_SETUP,
        onClick = onSkip
    )
}

@Composable
private fun KeyboardEnableContent(
    onEnable: () -> Unit,
    onSkip: () -> Unit
) {
    Text(
        text = SetupStrings.KEYBOARD_PROMPT,
        modifier = Modifier.padding(bottom = 20.dp)
    )
    FullWidthButton(
        text = SetupStrings.LETS_GO,
        onClick = onEnable
    )
    FullWidthButton(
        text = SetupStrings.SKIP_SETUP,
        onClick = onSkip
    )
}

@Composable
private fun SetupCompleteContent(onFinish: () -> Unit) {
    Text(
        text = SetupStrings.SETUP_COMPLETE,
        modifier = Modifier.padding(bottom = 20.dp)
    )
    FullWidthButton(
        text = SetupStrings.FINISH,
        onClick = onFinish
    )
}