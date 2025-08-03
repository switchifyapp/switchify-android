package com.enaboapps.switchify.auth.utils

import org.junit.Test
import org.junit.Assert.*

class ErrorMessageMapperTest {

    @Test
    fun `maps email not found errors correctly`() {
        val testCases = listOf(
            "Email not found in database",
            "User not found",
            "Invalid email address",
            "Email does not exist",
            "EMAIL NOT FOUND" // Test case insensitivity
        )
        
        testCases.forEach { error ->
            val result = ErrorMessageMapper.mapErrorToUserFriendlyMessage(error)
            assertEquals(
                "Email address not found. Please check your email and try again.",
                result
            )
        }
    }

    @Test
    fun `maps user already exists errors correctly`() {
        val testCases = listOf(
            "User already registered",
            "Email already exists",
            "Already signed up with this email",
            "USER ALREADY REGISTERED" // Test case insensitivity
        )
        
        testCases.forEach { error ->
            val result = ErrorMessageMapper.mapErrorToUserFriendlyMessage(error)
            assertEquals(
                "This email is already registered. Try signing in instead.",
                result
            )
        }
    }

    @Test
    fun `maps OTP code errors correctly`() {
        val testCases = listOf(
            "Incorrect code provided",
            "Invalid OTP code",
            "Wrong verification code",
            "Code has expired",
            "Token expired",
            "OTP expired",
            "Invalid token provided",
            "Token is invalid",
            "INCORRECT CODE" // Test case insensitivity
        )
        
        testCases.forEach { error ->
            val result = ErrorMessageMapper.mapErrorToUserFriendlyMessage(error)
            assertEquals(
                "Incorrect verification code. Please check your code and try again.",
                result
            )
        }
    }

    @Test
    fun `maps rate limiting errors correctly`() {
        val testCases = listOf(
            "Rate limit exceeded",
            "Too many attempts",
            "Try again later",
            "Email rate limit reached",
            "RATE LIMIT" // Test case insensitivity
        )
        
        testCases.forEach { error ->
            val result = ErrorMessageMapper.mapErrorToUserFriendlyMessage(error)
            assertEquals(
                "Too many attempts. Please wait a few minutes before trying again.",
                result
            )
        }
    }

    @Test
    fun `maps network errors correctly`() {
        val testCases = listOf(
            "Network error occurred",
            "Connection failed",
            "Request timeout",
            "Unable to connect to server",
            "No internet connection",
            "NETWORK ERROR" // Test case insensitivity
        )
        
        testCases.forEach { error ->
            val result = ErrorMessageMapper.mapErrorToUserFriendlyMessage(error)
            assertEquals(
                "Connection problem. Please check your internet and try again.",
                result
            )
        }
    }

    @Test
    fun `maps email format errors correctly`() {
        val testCases = listOf(
            "Invalid email format",
            "Malformed email address",
            "Email format is invalid",
            "INVALID EMAIL FORMAT" // Test case insensitivity
        )
        
        testCases.forEach { error ->
            val result = ErrorMessageMapper.mapErrorToUserFriendlyMessage(error)
            assertEquals(
                "Please enter a valid email address.",
                result
            )
        }
    }

    @Test
    fun `maps server errors correctly`() {
        val testCases = listOf(
            "Internal server error",
            "Server error occurred",
            "500 error",
            "502 error",
            "503 error",
            "INTERNAL SERVER ERROR" // Test case insensitivity
        )
        
        testCases.forEach { error ->
            val result = ErrorMessageMapper.mapErrorToUserFriendlyMessage(error)
            assertEquals(
                "Server temporarily unavailable. Please try again in a few minutes.",
                result
            )
        }
    }

    @Test
    fun `maps authentication errors correctly`() {
        val testCases = listOf(
            "Auth error",
            "Authentication failed",
            "Unauthorized access",
            "AUTH ERROR" // Test case insensitivity
        )
        
        testCases.forEach { error ->
            val result = ErrorMessageMapper.mapErrorToUserFriendlyMessage(error)
            assertEquals(
                "Authentication failed. Please try again.",
                result
            )
        }
    }

    @Test
    fun `returns fallback message for unknown errors`() {
        val testCases = listOf(
            "Some random error",
            "Unexpected database constraint violation",
            "Foreign key constraint failed",
            "",
            "   " // Whitespace only
        )
        
        testCases.forEach { error ->
            val result = ErrorMessageMapper.mapErrorToUserFriendlyMessage(error)
            assertEquals(
                "Something went wrong. Please try again or contact support if the problem continues.",
                result
            )
        }
    }

    @Test
    fun `mapExceptionToUserFriendlyMessage works correctly`() {
        val exception = RuntimeException("Invalid OTP code provided")
        val result = ErrorMessageMapper.mapExceptionToUserFriendlyMessage(exception, "verifyOtp")
        
        assertEquals(
            "Incorrect verification code. Please check your code and try again.",
            result
        )
    }

    @Test
    fun `mapExceptionToUserFriendlyMessage handles null message`() {
        val exception = RuntimeException(null as String?)
        val result = ErrorMessageMapper.mapExceptionToUserFriendlyMessage(exception, "test")
        
        assertEquals(
            "Something went wrong. Please try again or contact support if the problem continues.",
            result
        )
    }

    @Test
    fun `findMatchingPattern works correctly for testing`() {
        val error = "Email not found in database"
        val result = ErrorMessageMapper.findMatchingPattern(error)
        
        assertEquals(
            "Email address not found. Please check your email and try again.",
            result
        )
    }

    @Test
    fun `findMatchingPattern returns null for unmatched errors`() {
        val error = "Some random unmatched error"
        val result = ErrorMessageMapper.findMatchingPattern(error)
        
        assertNull(result)
    }
}