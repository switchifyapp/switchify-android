package com.enaboapps.switchify.screens.account

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.enaboapps.switchify.R
import com.enaboapps.switchify.auth.AuthManager
import com.enaboapps.switchify.components.BaseView
import com.enaboapps.switchify.components.FullWidthButton
import com.enaboapps.switchify.components.TextArea
import com.enaboapps.switchify.utils.Resources

@Composable
fun ForgotPasswordScreen(navController: NavController) {
    var email by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    val authManager = AuthManager.instance

    BaseView(
        titleResId = R.string.screen_title_reset_password,
        navController = navController
    ) {
        if (message != null) {
            Text(
                text = message ?: "",
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
        TextArea(
            value = email,
            onValueChange = { email = it },
            labelResId = R.string.label_email,
            keyboardType = KeyboardType.Email,
            imeAction = ImeAction.Done,
            isError = email.isBlank(),
            supportingTextResId = R.string.error_email_required
        )
        Spacer(modifier = Modifier.height(16.dp))
        FullWidthButton(
            textResId = R.string.button_send_reset_link,
            enabled = email.isNotBlank(),
            onClick = {
                if (email.isNotBlank()) {
                    authManager.sendPasswordResetEmail(email,
                        onSuccess = {
                            message = Resources.getString(R.string.message_reset_link_sent)
                        },
                        onFailure = { exception ->
                            message = exception.localizedMessage
                                ?: Resources.getString(R.string.error_generic)
                        }
                    )
                } else {
                    message = "Please enter your email address."
                }
            }
        )
    }
}