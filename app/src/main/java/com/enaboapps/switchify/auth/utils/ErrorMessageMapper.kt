package com.enaboapps.switchify.auth.utils

import android.util.Log

/**
 * Utility class for mapping raw API error messages to user-friendly messages.
 * Uses regex patterns to identify common error scenarios and provide clear,
 * actionable error messages to users while preserving original errors for debugging.
 */
object ErrorMessageMapper {
    
    private const val TAG = "ErrorMessageMapper"
    
    /**
     * Map of regex patterns to user-friendly error messages.
     * Patterns are case-insensitive for better matching.
     * Order matters: more specific patterns should come first.
     */
    private val errorPatterns = mapOf(
        // Email format issues (must come before general email errors)
        "invalid.*email.*format|malformed.*email|email.*format.*invalid".toRegex(RegexOption.IGNORE_CASE) to
            "Please enter a valid email address.",
        
        // OTP/Token specific errors (must come before general invalid errors)
        "incorrect.*code|invalid.*code|wrong.*code|code.*expired|token.*expired|otp.*expired|invalid.*token|token.*invalid".toRegex(RegexOption.IGNORE_CASE) to 
            "Incorrect verification code. Please check your code and try again.",
        
        // Sign up specific - user already exists
        "user.*already.*registered|email.*already.*exists|already.*signed.*up".toRegex(RegexOption.IGNORE_CASE) to
            "This email is already registered. Try signing in instead.",
        
        // Email-related errors (more general, comes after specific email format errors)
        "email.*not.*found|user.*not.*found|invalid.*email|email.*does.*not.*exist".toRegex(RegexOption.IGNORE_CASE) to 
            "Email address not found. Please check your email and try again.",
        
        // Rate limiting
        "rate.*limit|too.*many.*attempts|try.*again.*later|email.*rate.*limit".toRegex(RegexOption.IGNORE_CASE) to 
            "Too many attempts. Please wait a few minutes before trying again.",
        
        // Network/Connection issues
        "network.*error|connection.*failed|timeout|unable.*to.*connect|no.*internet".toRegex(RegexOption.IGNORE_CASE) to 
            "Connection problem. Please check your internet and try again.",
        
        // Server errors
        "internal.*server.*error|server.*error|5\\d\\d.*error".toRegex(RegexOption.IGNORE_CASE) to
            "Server temporarily unavailable. Please try again in a few minutes.",
        
        // Authentication service errors
        "auth.*error|authentication.*failed|unauthorized".toRegex(RegexOption.IGNORE_CASE) to
            "Authentication failed. Please try again."
    )
    
    /**
     * Maps a raw API error message to a user-friendly message.
     * 
     * @param apiError The raw error message from the API
     * @param context Optional context for logging (e.g., "sendOtp", "verifyOtp")
     * @return User-friendly error message
     */
    fun mapErrorToUserFriendlyMessage(apiError: String, context: String = ""): String {
        // Log the original error for debugging (safe for unit tests)
        try {
            Log.w(TAG, "API Error${if (context.isNotEmpty()) " ($context)" else ""}: $apiError")
        } catch (e: RuntimeException) {
            // Android Log not available in unit tests, ignore
        }
        
        // Check each pattern for a match
        errorPatterns.forEach { (pattern, userMessage) ->
            if (pattern.containsMatchIn(apiError)) {
                try {
                    Log.d(TAG, "Mapped error to user message: $userMessage")
                } catch (e: RuntimeException) {
                    // Android Log not available in unit tests, ignore
                }
                return userMessage
            }
        }
        
        // Fallback message for unmatched errors
        val fallbackMessage = "Something went wrong. Please try again or contact support if the problem continues."
        try {
            Log.d(TAG, "No pattern matched, using fallback message: $fallbackMessage")
        } catch (e: RuntimeException) {
            // Android Log not available in unit tests, ignore
        }
        return fallbackMessage
    }
    
    /**
     * Convenience method for mapping exception messages.
     */
    fun mapExceptionToUserFriendlyMessage(exception: Throwable, context: String = ""): String {
        val errorMessage = exception.message ?: "Unknown error occurred"
        return mapErrorToUserFriendlyMessage(errorMessage, context)
    }
    
    /**
     * For testing purposes - allows checking if a pattern would match without logging
     */
    internal fun findMatchingPattern(apiError: String): String? {
        return errorPatterns.entries.find { (pattern, _) ->
            pattern.containsMatchIn(apiError)
        }?.value
    }
}