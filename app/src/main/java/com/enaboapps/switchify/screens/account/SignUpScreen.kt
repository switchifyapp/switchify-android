package com.enaboapps.switchify.screens.account

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.enaboapps.switchify.auth.AuthManager
import com.enaboapps.switchify.auth.GoogleAuthHandler
import com.enaboapps.switchify.backend.preferences.PreferenceManager
import com.enaboapps.switchify.components.*
import com.enaboapps.switchify.service.custom.actions.store.ActionStore
import kotlinx.coroutines.launch

@Composable
fun SignUpScreen(navController: NavController) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val authManager = AuthManager.instance
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val googleAuthHandler = remember { GoogleAuthHandler() }

    val onSignUp = {
        // Go to the first screen
        navController.popBackStack(
            navController.graph.startDestinationId,
            false
        )

        // Upload the user's settings to Firestore
        val preferenceManager = PreferenceManager(context)
        preferenceManager.preferenceSync.uploadSettingsToFirestore()

        // Start listening for changes to the user's settings
        preferenceManager.preferenceSync.listenForSettingsChangesOnRemote()

        // Push actions to Firestore
        val actionStore = ActionStore(context)
        actionStore.pushActionsToFirestore()
    }

    BaseView(
        title = "Sign Up",
        navController = navController
    ) {
        if (errorMessage != null) {
            Text(
                text = errorMessage ?: "",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        Text(text = "Create an account to save your settings. This will allow you to access your settings on any device.")
        Spacer(modifier = Modifier.height(16.dp))

        TextArea(
            value = email,
            onValueChange = { email = it },
            label = "Email",
            keyboardType = KeyboardType.Email,
            imeAction = ImeAction.Next,
            isError = email.isBlank(),
            supportingText = "Email is required"
        )

        Spacer(modifier = Modifier.height(8.dp))

        TextArea(
            value = password,
            onValueChange = { password = it },
            label = "Password",
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Next,
            isSecure = true,
            isError = password.isBlank(),
            supportingText = "Password is required"
        )

        Spacer(modifier = Modifier.height(8.dp))

        TextArea(
            value = confirmPassword,
            onValueChange = { confirmPassword = it },
            label = "Confirm Password",
            keyboardType = KeyboardType.Password,
            isSecure = true,
            isError = confirmPassword.isBlank(),
            supportingText = "Confirm password is required"
        )

        Spacer(modifier = Modifier.height(16.dp))

        FullWidthButton(
            text = "Sign Up",
            onClick = {
                errorMessage = when {
                    !authManager.isPasswordStrong(password) -> "Password must be at least 8 characters long, include an uppercase letter, a lowercase letter, and a number."
                    password != confirmPassword -> "Passwords do not match."
                    email.isEmpty() -> "Email cannot be empty."
                    password.isEmpty() -> "Password cannot be empty."
                    else -> null
                }
                if (errorMessage == null) {
                    authManager.createUserWithEmailAndPassword(email, password,
                        onSuccess = {
                            onSignUp()
                        },
                        onFailure = { exception ->
                            errorMessage = exception.localizedMessage
                        }
                    )
                }
            }
        )

        Spacer(modifier = Modifier.height(16.dp))
        Text("or")
        Spacer(modifier = Modifier.height(16.dp))

        FullWidthButton(
            text = "Sign up with Google",
            onClick = {
                scope.launch {
                    googleAuthHandler.googleSignIn(context).collect { result ->
                        result.fold(
                            onSuccess = { authResult ->
                                if (authResult.user != null) {
                                    onSignUp()
                                } else {
                                    errorMessage = "Sign up failed"
                                }
                            },
                            onFailure = { exception ->
                                errorMessage = exception.message
                            }
                        )
                    }
                }
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        val urlLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult()
        ) {
            // Handle the result
        }

        val privacyPolicyUrl = "https://www.switchifyapp.com/privacy"

        FullWidthButton(
            text = "Privacy Policy",
            onClick = {
                urlLauncher.launch(Intent(Intent.ACTION_VIEW, Uri.parse(privacyPolicyUrl)))
            },
            isTextButton = true
        )
    }
}