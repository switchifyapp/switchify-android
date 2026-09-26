package com.enaboapps.switchify.auth.google

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.enaboapps.switchify.BuildConfig
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import java.security.MessageDigest
import java.util.UUID

/**
 * Modern Google Sign-In implementation using Credential Manager API
 * This replaces the deprecated GoogleSignInHelper
 */
class GoogleSignInManager(private val context: Context) {

    private val credentialManager = CredentialManager.create(context)

    /**
     * Sign in with Google using Credential Manager
     * @param filterByAuthorizedAccounts If true, only shows accounts that have previously authorized the app
     * @return GoogleSignInResult containing the authentication result
     */
    suspend fun signIn(filterByAuthorizedAccounts: Boolean = false): GoogleSignInResult {
        return try {
            // Google receives the hashed nonce; Supabase receives the raw nonce and
            // verifies that its hash matches the one embedded in the ID token.
            val rawNonce = UUID.randomUUID().toString()
            val hashedNonce = sha256Hex(rawNonce)

            // Create Google ID option
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(filterByAuthorizedAccounts)
                .setServerClientId(BuildConfig.GOOGLE_WEB_CLIENT_ID)
                .setAutoSelectEnabled(true)
                .setNonce(hashedNonce)
                .build()

            // Create credential request
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            // Get credentials
            val result = credentialManager.getCredential(
                context = context,
                request = request
            )

            handleCredentialResponse(result, rawNonce)
        } catch (e: NoCredentialException) {
            // No credentials available - user needs to sign in manually
            GoogleSignInResult.Error("No Google credentials available. Please sign in manually.")
        } catch (e: GetCredentialException) {
            GoogleSignInResult.Error("Google Sign-In failed: ${e.message}")
        } catch (e: Exception) {
            GoogleSignInResult.Error("Unexpected error: ${e.message}")
        }
    }

    /**
     * Sign in with Google for new users (shows all Google accounts)
     */
    suspend fun signInForNewUser(): GoogleSignInResult {
        return signIn(filterByAuthorizedAccounts = false)
    }

    /**
     * Sign in with Google for returning users (shows only authorized accounts)
     */
    suspend fun signInForReturningUser(): GoogleSignInResult {
        return signIn(filterByAuthorizedAccounts = true)
    }

    private fun handleCredentialResponse(
        result: GetCredentialResponse,
        rawNonce: String
    ): GoogleSignInResult {
        val credential = result.credential

        return when (credential.type) {
            GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL -> {
                try {
                    val googleIdCredential = GoogleIdTokenCredential.createFrom(credential.data)
                    GoogleSignInResult.Success(
                        idToken = googleIdCredential.idToken,
                        accessToken = null, // Access token not available in Credential Manager flow
                        email = googleIdCredential.id,
                        displayName = googleIdCredential.displayName,
                        rawNonce = rawNonce
                    )
                } catch (e: Exception) {
                    GoogleSignInResult.Error("Failed to parse Google credential: ${e.message}")
                }
            }

            else -> {
                GoogleSignInResult.Error("Unexpected credential type: ${credential.type} (${credential::class.simpleName})")
            }
        }
    }

    private fun sha256Hex(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}

/**
 * Sealed class representing Google Sign-In results
 * Updated to match the new Credential Manager API
 */
sealed class GoogleSignInResult {
    data class Success(
        val idToken: String,
        val accessToken: String?, // Note: May be null with Credential Manager
        val email: String?,
        val displayName: String?,
        val rawNonce: String
    ) : GoogleSignInResult()

    data class Error(val message: String) : GoogleSignInResult()
}