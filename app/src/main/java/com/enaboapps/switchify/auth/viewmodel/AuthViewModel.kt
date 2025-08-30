package com.enaboapps.switchify.auth.viewmodel

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enaboapps.switchify.auth.google.GoogleSignInManager
import com.enaboapps.switchify.auth.google.GoogleSignInResult
import com.enaboapps.switchify.auth.repository.AuthRepository
import com.enaboapps.switchify.auth.utils.ErrorMessageMapper
import com.enaboapps.switchify.backend.preferences.PreferenceManager
import io.sentry.Sentry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AuthViewModel : ViewModel() {

    private val authRepository = AuthRepository.instance
    private var googleSignInManager: GoogleSignInManager? = null

    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.EmailInput)
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    private val _email = MutableStateFlow("")
    val email: StateFlow<String> = _email.asStateFlow()

    private val _otp = MutableStateFlow("")
    val otp: StateFlow<String> = _otp.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    /**
     * Initialize Google Sign-In manager when Context is available
     */
    fun initializeGoogleSignIn(context: Context) {
        if (googleSignInManager == null) {
            googleSignInManager = GoogleSignInManager(context)
        }
    }

    fun updateEmail(newEmail: String) {
        _email.value = newEmail
        _errorMessage.value = null
    }

    fun updateOtp(newOtp: String) {
        if (newOtp.length <= 6 && newOtp.all { it.isDigit() }) {
            _otp.value = newOtp
            _errorMessage.value = null
        }
    }

    fun sendOtp() {
        if (_email.value.isBlank() || !isValidEmail(_email.value)) {
            _errorMessage.value = "Please enter a valid email address"
            return
        }

        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            _errorMessage.value = null

            // Try sign-in first, then fall back to sign-up if user doesn't exist
            authRepository.sendEmailOtp(_email.value, false).fold(
                onSuccess = {
                    _uiState.value = AuthUiState.OtpVerification
                },
                onFailure = { exception ->
                    val errorMessage = exception.message?.lowercase() ?: ""

                    // If user not found, try sign-up
                    if (errorMessage.contains("user not found") ||
                        errorMessage.contains("email not found") ||
                        errorMessage.contains("invalid_credentials")
                    ) {

                        // Attempt sign-up
                        authRepository.sendEmailOtp(_email.value, true).fold(
                            onSuccess = {
                                _uiState.value = AuthUiState.OtpVerification
                            },
                            onFailure = { signUpException ->
                                _errorMessage.value =
                                    ErrorMessageMapper.mapExceptionToUserFriendlyMessage(
                                        signUpException,
                                        "sendUnifiedOtp"
                                    )
                                _uiState.value = AuthUiState.EmailInput
                            }
                        )
                    } else {
                        // Other error, show original message
                        _errorMessage.value = ErrorMessageMapper.mapExceptionToUserFriendlyMessage(
                            exception,
                            "sendUnifiedOtp"
                        )
                        _uiState.value = AuthUiState.EmailInput
                    }
                }
            )
        }
    }

    fun verifyOtp() {
        if (_otp.value.length != 6) {
            _errorMessage.value = "Please enter a 6-digit OTP"
            return
        }

        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            _errorMessage.value = null

            authRepository.verifyEmailOtp(_email.value, _otp.value).fold(
                onSuccess = {
                    _uiState.value = AuthUiState.Success
                },
                onFailure = { exception ->
                    _errorMessage.value = ErrorMessageMapper.mapExceptionToUserFriendlyMessage(
                        exception,
                        "verifyOtp"
                    )
                    _uiState.value = AuthUiState.OtpVerification
                }
            )
        }
    }

    fun resendOtp() {
        sendOtp()
    }

    fun goBackToEmailInput() {
        _uiState.value = AuthUiState.EmailInput
        _otp.value = ""
        _errorMessage.value = null
    }

    fun clearError() {
        _errorMessage.value = null
    }

    /**
     * Get Google Sign-In manager instance
     */
    fun getGoogleSignInManager(): GoogleSignInManager? = googleSignInManager

    /**
     * Handle settings sync after successful authentication
     */
    fun handleSettingsSync(context: Context) {
        Handler(Looper.getMainLooper()).postDelayed({
            val preferenceManager = PreferenceManager(context)
            preferenceManager.enableSync()
        }, 1000)
    }

    /**
     * Start Google Sign-In flow using Credential Manager
     */
    fun signInWithGoogle() {
        val manager = googleSignInManager
        if (manager == null) {
            _errorMessage.value = "Google Sign-In not available"
            return
        }

        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            _errorMessage.value = null

            // For unified flow, always try for new users (shows all Google accounts)
            val result = manager.signInForNewUser()

            handleGoogleSignInResult(result)
        }
    }

    /**
     * Handle Google Sign-In result and authenticate with Supabase
     */
    fun handleGoogleSignInResult(result: GoogleSignInResult) {
        when (result) {
            is GoogleSignInResult.Success -> {
                viewModelScope.launch {
                    _uiState.value = AuthUiState.Loading
                    _errorMessage.value = null

                    authRepository.signInWithGoogle(result.idToken, result.accessToken).fold(
                        onSuccess = {
                            _uiState.value = AuthUiState.Success
                        },
                        onFailure = { exception ->
                            // Log Google Sign-In Supabase authentication errors to Sentry
                            Sentry.captureException(exception) { scope ->
                                scope.setTag("component", "AuthViewModel")
                                scope.setTag("error_type", "GoogleSignInSupabaseError")
                                scope.setExtra("authFlow", "unified")
                                scope.setExtra("idTokenLength", result.idToken.length.toString())
                                scope.setExtra("hasEmail", (result.email != null).toString())
                                scope.setExtra(
                                    "hasDisplayName",
                                    (result.displayName != null).toString()
                                )
                            }
                            _errorMessage.value =
                                ErrorMessageMapper.mapExceptionToUserFriendlyMessage(
                                    exception,
                                    "googleSignIn"
                                )
                            _uiState.value = AuthUiState.EmailInput
                        }
                    )
                }
            }

            is GoogleSignInResult.Error -> {
                // Log Google Sign-In client errors to Sentry
                Sentry.captureMessage("Google Sign-In client error: ${result.message}") { scope ->
                    scope.setTag("component", "AuthViewModel")
                    scope.setTag("error_type", "GoogleSignInClientError")
                    scope.setExtra("authFlow", "unified")
                    scope.setExtra("errorMessage", result.message)
                }
                _errorMessage.value = "Google Sign-In failed: ${result.message}"
                _uiState.value = AuthUiState.EmailInput
            }
        }
    }

    private fun isValidEmail(email: String): Boolean {
        return android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }
}

sealed class AuthUiState {
    object EmailInput : AuthUiState()
    object Loading : AuthUiState()
    object OtpVerification : AuthUiState()
    object Success : AuthUiState()
}