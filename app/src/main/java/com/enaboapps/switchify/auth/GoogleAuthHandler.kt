package com.enaboapps.switchify.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.enaboapps.switchify.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.security.MessageDigest
import java.util.UUID

class GoogleAuthHandler(
    private val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    fun googleSignIn(context: Context): Flow<Result<AuthResult>> = callbackFlow {
        try {
            val credentialManager = CredentialManager.create(context)

            // Generate and hash nonce
            val nonce = generateHashedNonce()

            // Create Google ID option
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(context.getString(R.string.default_web_client_id))
                .setNonce(nonce)
                .build()

            // Build credential request
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            // Get credentials
            val result = credentialManager.getCredential(context, request)

            when (val credential = result.credential) {
                is CustomCredential -> {
                    if (credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                        handleGoogleCredential(credential)?.let { authResult ->
                            trySend(Result.success(authResult))
                        }
                            ?: trySend(Result.failure(Exception("Failed to process Google credential")))
                    } else {
                        trySend(Result.failure(Exception("Invalid credential type received")))
                    }
                }

                else -> trySend(Result.failure(Exception("Unsupported credential type")))
            }

        } catch (e: GetCredentialCancellationException) {
            trySend(Result.failure(Exception("Sign-in was canceled")))
        } catch (e: NoCredentialException) {
            trySend(Result.failure(Exception("No credentials available. Please sign in first or add a Google account to your device.")))
        } catch (e: Exception) {
            trySend(Result.failure(e))
        }

        awaitClose { }
    }

    private suspend fun handleGoogleCredential(credential: CustomCredential): AuthResult? {
        return try {
            val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
            val authCredential =
                GoogleAuthProvider.getCredential(googleIdTokenCredential.idToken, null)
            firebaseAuth.signInWithCredential(authCredential).await()
        } catch (e: Exception) {
            null
        }
    }

    private fun generateHashedNonce(): String {
        val nonce = UUID.randomUUID().toString()
        val bytes = nonce.toByteArray()
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(bytes)
        return digest.fold("") { str, it -> str + "%02x".format(it) }
    }
}