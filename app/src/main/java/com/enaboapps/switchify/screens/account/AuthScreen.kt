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
import com.enaboapps.switchify.components.ActionButton
import com.enaboapps.switchify.components.OfficialGoogleSignInButton
import com.enaboapps.switchify.components.TextArea
import com.enaboapps.switchify.nav.NavigationRoute
import com.enaboapps.switchify.backend.preferences.PreferenceManager
import kotlinx.coroutines.delay

@Composable
fun AuthScreen(
    navController: NavController
) {
    val context = LocalContext.current
    val preferenceManager = remember { PreferenceManager(context) }
    
    val viewModel: AuthViewModel = viewModel { 
        AuthViewModel() // No context stored
    }
    
    // Initialize Google Sign-In with Context
    LaunchedEffect(Unit) {
        viewModel.initializeGoogleSignIn(context)
    }
    
    val uiState by viewModel.uiState.collectAsState()
    val email by viewModel.email.collectAsState()
    val otp by viewModel.otp.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    BaseView(
        titleResId = R.string.screen_title_authentication,
        navController = navController,
        padding = 16.dp
    ) {
        // Content based on current state
        when (uiState) {
            AuthUiState.EmailInput -> {
                EmailInputSection(
                    email = email,
                    onEmailChange = viewModel::updateEmail,
                    onSendOtp = viewModel::sendOtp,
                    errorMessage = errorMessage,
                    onClearError = viewModel::clearError,
                    onGoogleSignInClick = {
                        viewModel.signInWithGoogle()
                    }
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
                    // Handle settings sync after successful authentication
                    viewModel.handleSettingsSync(context)
                    
                    // Set setup complete when user signs in
                    preferenceManager.setSetupComplete()
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
    onGoogleSignInClick: (() -> Unit)? = null
) {
    Text(
        text = stringResource(R.string.welcome_to_switchify),
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center
    )
    
    Spacer(modifier = Modifier.height(8.dp))
    
    Text(
        text = stringResource(R.string.enter_email_to_continue),
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

    ActionButton(
        textResId = R.string.button_continue_with_email,
        onClick = onSendOtp,
        enabled = email.isNotBlank()
    )

    // Add divider and Google Sign-In option
    if (onGoogleSignInClick != null) {
        Spacer(modifier = Modifier.height(24.dp))
        
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            HorizontalDivider(
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
            )
            Text(
                text = stringResource(R.string.or),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            HorizontalDivider(
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        OfficialGoogleSignInButton(
            onClick = onGoogleSignInClick
        )
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

    ActionButton(
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