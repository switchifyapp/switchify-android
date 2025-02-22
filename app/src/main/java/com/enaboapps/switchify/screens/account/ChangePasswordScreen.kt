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
fun ChangePasswordScreen(navController: NavController) {
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    val authManager = AuthManager.instance

    BaseView(
        titleResId = R.string.screen_title_change_password,
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
            value = currentPassword,
            onValueChange = { currentPassword = it },
            labelResId = R.string.label_current_password,
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Next,
            isSecure = true,
            isError = currentPassword.isBlank(),
            supportingTextResId = R.string.error_current_password_required
        )
        Spacer(modifier = Modifier.height(8.dp))
        TextArea(
            value = newPassword,
            onValueChange = { newPassword = it },
            labelResId = R.string.label_new_password,
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Next,
            isSecure = true,
            isError = newPassword.isBlank(),
            supportingTextResId = R.string.error_new_password_required
        )
        Spacer(modifier = Modifier.height(8.dp))
        TextArea(
            value = confirmPassword,
            onValueChange = { confirmPassword = it },
            labelResId = R.string.label_confirm_new_password,
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Done,
            isSecure = true,
            isError = confirmPassword.isBlank(),
            supportingTextResId = R.string.error_confirm_password_required
        )
        Spacer(modifier = Modifier.height(16.dp))
        FullWidthButton(
            textResId = R.string.button_change_password,
            onClick = {
                message = when {
                    newPassword != confirmPassword -> Resources.getString(R.string.error_passwords_do_not_match)
                    !authManager.isPasswordStrong(newPassword) -> Resources.getString(R.string.error_password_not_strong)
                    else -> null
                }
                if (message == null) {
                    authManager.updatePassword(currentPassword, newPassword,
                        onSuccess = {
                            message = Resources.getString(R.string.message_password_changed)
                            navController.popBackStack()
                        },
                        onFailure = { exception ->
                            message = exception.localizedMessage
                                ?: Resources.getString(R.string.error_generic)
                        }
                    )
                }
            }
        )
    }
}