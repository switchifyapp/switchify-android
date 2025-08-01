package com.enaboapps.switchify.auth.repository

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.OTP
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.user.UserInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.enaboapps.switchify.backend.supabase.SupabaseClient as SupabaseClientProvider

class AuthRepository private constructor() {
    
    companion object {
        val instance: AuthRepository by lazy { AuthRepository() }
    }
    
    private val supabaseClient = SupabaseClientProvider.client

    suspend fun sendEmailOtp(email: String, isSignUp: Boolean = false): Result<Unit> {
        return try {
            if (isSignUp) {
                // Sign up with OTP
                supabaseClient.auth.signUpWith(OTP) {
                    this.email = email
                }
            } else {
                // Sign in with OTP
                supabaseClient.auth.signInWith(OTP) {
                    this.email = email
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun verifyEmailOtp(email: String, token: String): Result<Unit> {
        return try {
            supabaseClient.auth.verifyEmailOtp(
                type = OtpType.Email.EMAIL,
                email = email,
                token = token
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun signOut(): Result<Unit> {
        return try {
            supabaseClient.auth.signOut()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun isUserSignedIn(): Boolean {
        return supabaseClient.auth.currentUserOrNull() != null
    }

    /**
     * Get the currently signed-in user, if any.
     */
    fun getCurrentUser(): UserInfo? {
        return supabaseClient.auth.currentUserOrNull()
    }

    /**
     * Get the user's ID, if any.
     */
    fun getUserId(): String? {
        return supabaseClient.auth.currentUserOrNull()?.id
    }

    /**
     * Delete the current user.
     * Note: User deletion must be implemented server-side in Supabase.
     * This method only signs out the user.
     */
    suspend fun deleteUser(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            supabaseClient.auth.signOut()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}