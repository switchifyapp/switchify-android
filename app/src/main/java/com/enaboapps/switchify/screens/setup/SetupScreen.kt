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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.enaboapps.switchify.R
import com.enaboapps.switchify.auth.AuthManager
import com.enaboapps.switchify.backend.preferences.PreferenceManager
import com.enaboapps.switchify.components.BaseView
import com.enaboapps.switchify.components.FullWidthButton
import com.enaboapps.switchify.nav.NavigationRoute
import com.enaboapps.switchify.screens.settings.scanning.ScanModeSelectionSection
import com.enaboapps.switchify.service.utils.ServiceUtils
import com.enaboapps.switchify.switches.SwitchConfigInvalidBanner
import com.enaboapps.switchify.switches.SwitchEventStore


@Composable
fun SetupScreen(
    navController: NavController
) {
    val context = LocalContext.current
    val switchEventStore = remember {
        SwitchEventStore.getInstance()
    }
    val serviceUtils = ServiceUtils()
    val preferenceManager = PreferenceManager(context)

    val viewModel = remember {
        SetupScreenModel(
            switchEventStore,
            serviceUtils,
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
    onSkipSetup: () -> Unit,
    onSignIn: () -> Unit,
    onScanModeChange: (String) -> Unit,
    onFinish: () -> Unit,
    navController: NavController
) {
    BaseView(
        titleResId = R.string.screen_title_setup,
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
                text = stringResource(R.string.setup_welcome),
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
        textResId = R.string.setup_edit_switches,
        onClick = onEditSwitches
    )
    FullWidthButton(
        textResId = R.string.setup_skip_setup,
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
        text = stringResource(R.string.setup_sign_in_prompt),
        modifier = Modifier.padding(bottom = 4.dp)
    )
    FullWidthButton(
        textResId = R.string.button_sign_in,
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
        text = stringResource(R.string.setup_accessibility_prompt),
        modifier = Modifier.padding(bottom = 20.dp)
    )
    Text(
        text = context.getString(R.string.accessibility_service_disclosure),
        modifier = Modifier.padding(bottom = 20.dp)
    )
    FullWidthButton(
        textResId = R.string.setup_lets_go,
        onClick = onEnable
    )
    FullWidthButton(
        textResId = R.string.setup_skip_setup,
        onClick = onSkip
    )
}

@Composable
private fun SetupCompleteContent(onFinish: () -> Unit) {
    Text(
        text = stringResource(R.string.setup_setup_complete),
        modifier = Modifier.padding(bottom = 20.dp)
    )
    FullWidthButton(
        textResId = R.string.setup_finish,
        onClick = onFinish
    )
}