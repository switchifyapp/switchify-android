package com.enaboapps.switchify.auth.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enaboapps.switchify.auth.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AuthViewModel(private val isSignUp: Boolean = false) : ViewModel() {
    
    private val authRepository = AuthRepository.instance

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
                    _errorMessage.value = exception.message ?: if (isSignUp) "Failed to send sign-up OTP" else "Failed to send sign-in OTP"
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
                },
                onFailure = { exception ->
                    _errorMessage.value = exception.message ?: "Invalid OTP"
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