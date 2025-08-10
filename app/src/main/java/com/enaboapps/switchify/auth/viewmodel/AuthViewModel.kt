package com.enaboapps.switchify.auth.viewmodel

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enaboapps.switchify.auth.repository.AuthRepository
import com.enaboapps.switchify.auth.utils.ErrorMessageMapper
import com.enaboapps.switchify.auth.google.GoogleSignInManager
import com.enaboapps.switchify.auth.google.GoogleSignInResult
import com.enaboapps.switchify.backend.preferences.PreferenceManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AuthViewModel(
    private val isSignUp: Boolean = false,
    private val context: Context? = null
) : ViewModel() {
    
    private val authRepository = AuthRepository.instance
    private val googleSignInManager = if (context != null) GoogleSignInManager(context) else null

    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.EmailInput)
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    private val _email = MutableStateFlow("")
    val email: StateFlow<String> = _email.asStateFlow()

    private val _otp = MutableStateFlow("")
    val otp: StateFlow<String> = _otp.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()
    
    val authMode: String get() = if (isSignUp) "Sign Up" else "Sign In"

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
            
            authRepository.sendEmailOtp(_email.value, isSignUp).fold(
                onSuccess = {
                    _uiState.value = AuthUiState.OtpVerification
                },
                onFailure = { exception ->
                    _errorMessage.value = ErrorMessageMapper.mapExceptionToUserFriendlyMessage(
                        exception, 
                        if (isSignUp) "sendSignUpOtp" else "sendSignInOtp"
                    )
                    _uiState.value = AuthUiState.EmailInput
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
                    
                    // Delay settings sync to avoid blocking auth completion
                    if (context != null) {
                        Handler(Looper.getMainLooper()).postDelayed({
                            val preferenceManager = PreferenceManager(context)
                            preferenceManager.enableSync()
                            if (!isSignUp) {
                                // Sign in: pull settings from server
                                preferenceManager.preferenceSync.retrieveSettingsFromSupabase()
                            } else {
                                // Sign up: push current settings to server
                                preferenceManager.preferenceSync.uploadSettingsToSupabase()
                            }
                        }, 1000)
                    }
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
     * Start Google Sign-In flow using Credential Manager
     */
    fun signInWithGoogle() {
        if (googleSignInManager == null) {
            _errorMessage.value = "Google Sign-In not available"
            return
        }

        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            _errorMessage.value = null

            val result = if (isSignUp) {
                googleSignInManager.signInForNewUser()
            } else {
                googleSignInManager.signInForReturningUser()
            }
            
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
                            
                            // Handle settings sync for Google Sign-In users
                            if (context != null) {
                                Handler(Looper.getMainLooper()).postDelayed({
                                    val preferenceManager = PreferenceManager(context)
                                    preferenceManager.enableSync()
                                    // For Google Sign-In, we'll pull settings from server as it's likely an existing user
                                    preferenceManager.preferenceSync.retrieveSettingsFromSupabase()
                                }, 1000)
                            }
                        },
                        onFailure = { exception ->
                            _errorMessage.value = ErrorMessageMapper.mapExceptionToUserFriendlyMessage(
                                exception,
                                "googleSignIn"
                            )
                            _uiState.value = AuthUiState.EmailInput
                        }
                    )
                }
            }
            is GoogleSignInResult.Error -> {
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