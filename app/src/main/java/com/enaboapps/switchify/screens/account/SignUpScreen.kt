package com.enaboapps.switchify.screens.account

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.enaboapps.switchify.R
import com.enaboapps.switchify.auth.AuthManager
import com.enaboapps.switchify.auth.GoogleAuthHandler
import com.enaboapps.switchify.backend.preferences.PreferenceManager
import com.enaboapps.switchify.components.BaseView
import com.enaboapps.switchify.components.FullWidthButton
import com.enaboapps.switchify.components.TextArea
import com.enaboapps.switchify.service.actions.custom.store.ActionStore
import com.enaboapps.switchify.utils.Resources
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
        titleResId = R.string.screen_title_sign_up,
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

        Text(text = stringResource(R.string.create_an_account_to_save_settings))
        Spacer(modifier = Modifier.height(16.dp))

        TextArea(
            value = email,
            onValueChange = { email = it },
            labelResId = R.string.label_email_address,
            keyboardType = KeyboardType.Email,
            imeAction = ImeAction.Next,
            isError = email.isBlank(),
            supportingTextResId = R.string.error_email_required
        )

        Spacer(modifier = Modifier.height(8.dp))

        TextArea(
            value = password,
            onValueChange = { password = it },
            labelResId = R.string.label_password,
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Next,
            isSecure = true,
            isError = password.isBlank(),
            supportingTextResId = R.string.error_password_required
        )

        Spacer(modifier = Modifier.height(8.dp))

        TextArea(
            value = confirmPassword,
            onValueChange = { confirmPassword = it },
            labelResId = R.string.label_confirm_password,
            keyboardType = KeyboardType.Password,
            isSecure = true,
            isError = confirmPassword.isBlank(),
            supportingTextResId = R.string.error_password_required
        )

        Spacer(modifier = Modifier.height(16.dp))

        FullWidthButton(
            textResId = R.string.button_sign_up,
            onClick = {
                errorMessage = when {
                    !authManager.isPasswordStrong(password) -> Resources.getString(R.string.error_password_not_strong)
                    password != confirmPassword -> Resources.getString(R.string.error_passwords_do_not_match)
                    email.isEmpty() -> Resources.getString(R.string.error_email_required)
                    password.isEmpty() -> Resources.getString(R.string.error_password_required)
                    else -> null
                }
                if (errorMessage == null) {
                    authManager.createUserWithEmailAndPassword(
                        email, password,
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
        Text(stringResource(R.string.or))
        Spacer(modifier = Modifier.height(16.dp))

        FullWidthButton(
            textResId = R.string.button_sign_up_with_google,
            onClick = {
                scope.launch {
                    googleAuthHandler.googleSignIn(context).collect { result ->
                        result.fold(
                            onSuccess = { authResult ->
                                if (authResult.user != null) {
                                    onSignUp()
                                } else {
                                    errorMessage = Resources.getString(R.string.error_signing_up)
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
            textResId = R.string.button_privacy_policy,
            onClick = {
                urlLauncher.launch(Intent(Intent.ACTION_VIEW, Uri.parse(privacyPolicyUrl)))
            },
            isTextButton = true
        )
    }
}