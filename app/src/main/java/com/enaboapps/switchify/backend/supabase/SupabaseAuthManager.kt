package com.enaboapps.switchify.backend.supabase

import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.user.UserInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SupabaseAuthManager {
    companion object {
        val instance: SupabaseAuthManager by lazy {
            SupabaseAuthManager()
        }
    }

    private val auth: Auth = SupabaseClient.client.auth

    /**
     * Create user with email and password.
     */
    suspend fun createUserWithEmailAndPassword(
        email: String,
        password: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            auth.signUpWith(Email) {
                this.email = email
                this.password = password
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Sign in with email and password.
     */
    suspend fun signInWithEmailAndPassword(
        email: String,
        password: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            auth.signInWith(Email) {
                this.email = email
                this.password = password
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Delete the current user.
     * Note: User deletion must be implemented server-side in Supabase.
     * This method only signs out the user.
     */
    suspend fun deleteUser(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            auth.signOut()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Sign out the current user.
     */
    suspend fun signOut(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            auth.signOut()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Check if a user is currently signed in.
     */
    fun isUserSignedIn(): Boolean {
        return auth.currentUserOrNull() != null
    }

    /**
     * Get the currently signed-in user, if any.
     */
    fun getCurrentUser(): UserInfo? {
        return auth.currentUserOrNull()
    }

    /**
     * Get the user's ID, if any.
     */
    fun getUserId(): String? {
        return auth.currentUserOrNull()?.id
    }

    /**
     * Update the current user's password.
     */
    suspend fun updatePassword(
        newPassword: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            auth.updateUser {
                password = newPassword
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Send a password reset email.
     */
    suspend fun sendPasswordResetEmail(
        email: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            auth.resetPasswordForEmail(email)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Check a password for strength.
     */
    fun isPasswordStrong(password: String): Boolean {
        // eight characters, one uppercase, one lowercase, one number
        val passwordRegex = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{8,}\$".toRegex()
        return passwordRegex.matches(password)
    }
}