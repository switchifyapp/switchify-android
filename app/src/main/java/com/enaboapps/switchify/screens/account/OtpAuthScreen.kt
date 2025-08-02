package com.enaboapps.switchify.screens.account

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.enaboapps.switchify.R
import com.enaboapps.switchify.auth.viewmodel.AuthUiState
import com.enaboapps.switchify.auth.viewmodel.AuthViewModel
import com.enaboapps.switchify.components.BaseView
import com.enaboapps.switchify.components.FullWidthButton
import com.enaboapps.switchify.components.TextArea
import com.enaboapps.switchify.nav.NavigationRoute
import kotlinx.coroutines.delay

@Composable
fun OtpAuthScreen(
    navController: NavController,
    isSignUp: Boolean = false
) {
    var selectedTabIndex by remember { mutableIntStateOf(if (isSignUp) 1 else 0) }
    val currentIsSignUp = selectedTabIndex == 1
    
    val viewModel: AuthViewModel = viewModel(key = "auth_$currentIsSignUp") { 
        AuthViewModel(currentIsSignUp) 
    }
    
    val uiState by viewModel.uiState.collectAsState()
    val email by viewModel.email.collectAsState()
    val otp by viewModel.otp.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    BaseView(
        titleResId = R.string.screen_title_authentication,
        navController = navController,
        padding = 0.dp
    ) {
        // Tab Row
        TabRow(
            selectedTabIndex = selectedTabIndex,
            modifier = Modifier.fillMaxWidth()
        ) {
            Tab(
                selected = selectedTabIndex == 0,
                onClick = { selectedTabIndex = 0 },
                text = { Text("Sign In") }
            )
            Tab(
                selected = selectedTabIndex == 1,
                onClick = { selectedTabIndex = 1 },
                text = { Text("Sign Up") }
            )
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Content based on current state
        when (uiState) {
            AuthUiState.EmailInput -> {
                EmailInputSection(
                    email = email,
                    onEmailChange = viewModel::updateEmail,
                    onSendOtp = viewModel::sendOtp,
                    errorMessage = errorMessage,
                    onClearError = viewModel::clearError,
                    isSignUp = currentIsSignUp
                )
            }
            AuthUiState.Loading -> {
                LoadingSection()
            }
            AuthUiState.OtpVerification -> {
                OtpVerificationSection(
                    email = email,
                    otp = otp,
                    onOtpChange = viewModel::updateOtp,
                    onVerifyOtp = viewModel::verifyOtp,
                    onResendOtp = viewModel::resendOtp,
                    onBackToEmail = viewModel::goBackToEmailInput,
                    errorMessage = errorMessage,
                    onClearError = viewModel::clearError
                )
            }
            AuthUiState.Success -> {
                LaunchedEffect(Unit) {
                    navController.navigate(NavigationRoute.Home.name) {
                        popUpTo(navController.graph.startDestinationId) {
                            inclusive = true
                        }
                        launchSingleTop = true
                    }
                }
            }
        }
    }
}

@Composable
private fun EmailInputSection(
    email: String,
    onEmailChange: (String) -> Unit,
    onSendOtp: () -> Unit,
    errorMessage: String?,
    onClearError: () -> Unit,
    isSignUp: Boolean = false
) {
    Text(
        text = stringResource(if (isSignUp) R.string.create_account else R.string.welcome_back),
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center
    )
    
    Spacer(modifier = Modifier.height(8.dp))
    
    Text(
        text = stringResource(if (isSignUp) R.string.enter_email_to_create_account else R.string.enter_email_for_otp),
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    
    Spacer(modifier = Modifier.height(32.dp))

    TextArea(
        value = email,
        onValueChange = { 
            onEmailChange(it)
            if (errorMessage != null) onClearError()
        },
        labelResId = R.string.label_email,
        keyboardType = KeyboardType.Email,
        imeAction = ImeAction.Done,
        isError = errorMessage != null
    )

    Spacer(modifier = Modifier.height(24.dp))

    FullWidthButton(
        textResId = if (isSignUp) R.string.button_create_account else R.string.button_send_otp,
        onClick = onSendOtp,
        enabled = email.isNotBlank()
    )

    if (errorMessage != null) {
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = errorMessage,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun LoadingSection() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator()
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.please_wait),
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

@Composable
private fun OtpVerificationSection(
    email: String,
    otp: String,
    onOtpChange: (String) -> Unit,
    onVerifyOtp: () -> Unit,
    onResendOtp: () -> Unit,
    onBackToEmail: () -> Unit,
    errorMessage: String?,
    onClearError: () -> Unit
) {
    var timeLeft by remember { mutableStateOf(60) }
    val isResendEnabled by remember { derivedStateOf { timeLeft == 0 } }

    LaunchedEffect(key1 = timeLeft) {
        if (timeLeft > 0) {
            delay(1000L)
            timeLeft--
        }
    }

    Text(
        text = stringResource(R.string.verification_code_sent),
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center
    )
    
    Spacer(modifier = Modifier.height(8.dp))
    
    Text(
        text = stringResource(R.string.enter_otp_sent_to, email),
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    
    Spacer(modifier = Modifier.height(32.dp))

    TextArea(
        value = otp,
        onValueChange = { 
            onOtpChange(it)
            if (errorMessage != null) onClearError()
        },
        labelResId = R.string.label_verification_code,
        keyboardType = KeyboardType.Number,
        imeAction = ImeAction.Done,
        isError = errorMessage != null
    )

    Spacer(modifier = Modifier.height(24.dp))

    FullWidthButton(
        textResId = R.string.button_verify,
        onClick = onVerifyOtp,
        enabled = otp.length == 6
    )

    Spacer(modifier = Modifier.height(16.dp))

    TextButton(
        onClick = {
            if (isResendEnabled) {
                onResendOtp()
                timeLeft = 60
            }
        },
        enabled = isResendEnabled
    ) {
        Text(
            text = if (isResendEnabled) {
                stringResource(R.string.button_resend_otp)
            } else {
                stringResource(R.string.resend_in_seconds, timeLeft)
            }
        )
    }

    Spacer(modifier = Modifier.height(8.dp))

    TextButton(onClick = onBackToEmail) {
        Text(text = stringResource(R.string.button_change_email))
    }

    if (errorMessage != null) {
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = errorMessage,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
    }
}