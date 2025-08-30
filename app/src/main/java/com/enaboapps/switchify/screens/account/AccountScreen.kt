package com.enaboapps.switchify.screens.account

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.enaboapps.switchify.R
import com.enaboapps.switchify.auth.repository.AuthRepository
import com.enaboapps.switchify.backend.iap.IAPHandler
import com.enaboapps.switchify.backend.preferences.PreferenceManager
import com.enaboapps.switchify.components.ActionButton
import com.enaboapps.switchify.components.ActionButtonType
import com.enaboapps.switchify.components.AdaptiveStack
import com.enaboapps.switchify.components.BaseView
import kotlinx.coroutines.launch

@Composable
fun AccountScreen(navController: NavController) {
    val authRepository = AuthRepository.instance
    val currentUser = authRepository.getCurrentUser()
    val userEmail = currentUser?.email ?: "Not Logged In"
    val scope = rememberCoroutineScope()
    val showDeleteAccountDialog = remember { mutableStateOf(false) }
    val showSignOutDialog = remember { mutableStateOf(false) }
    val showDeletePrefsDialog = remember { mutableStateOf(false) }
    val context = LocalContext.current
    val preferenceManager = remember { PreferenceManager(context) }

    val proStatus = remember { mutableStateOf("") }
    val isLoading = remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        IAPHandler.initIfNeeded(context) {
            IAPHandler.getProStatus { status ->
                proStatus.value = status
                isLoading.value = false
            }
        }
    }

    val deleteAccount = {
        scope.launch {
            val result = authRepository.deleteUser()
            result.fold(
                onSuccess = {
                    Toast.makeText(context, "Account deleted", Toast.LENGTH_SHORT).show()
                    navController.popBackStack(navController.graph.startDestinationId, false)
                },
                onFailure = {
                    Toast.makeText(context, "Error deleting account", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }

    BaseView(
        titleResId = R.string.screen_title_account,
        navController = navController
    ) {
        // User Information Section
        AccountInfoSection(email = userEmail)

        Spacer(modifier = Modifier.height(24.dp))

        // Pro Status Section
        ProStatusSection(
            proStatus = proStatus.value,
            isLoading = isLoading.value
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Account Actions Section
        AccountActionsSection(
            onSignOut = {
                showSignOutDialog.value = true
            },
            onDeleteAccount = {
                showDeleteAccountDialog.value = true
            }
        )

        if (showDeleteAccountDialog.value) {
            AlertDialog(
                onDismissRequest = { showDeleteAccountDialog.value = false },
                title = { Text(stringResource(R.string.dialog_title_delete_account)) },
                text = { Text(stringResource(R.string.dialog_message_delete_account)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showDeleteAccountDialog.value = false
                            showDeletePrefsDialog.value = true
                        }
                    ) {
                        Text(stringResource(R.string.button_delete))
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showDeleteAccountDialog.value = false }
                    ) {
                        Text(stringResource(R.string.button_cancel))
                    }
                }
            )
        }

        // Sign Out Confirmation Dialog
        if (showSignOutDialog.value) {
            AlertDialog(
                onDismissRequest = { showSignOutDialog.value = false },
                title = { Text(stringResource(R.string.dialog_title_sign_out)) },
                text = { Text(stringResource(R.string.dialog_message_clear_preferences_sign_out)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showSignOutDialog.value = false
                            scope.launch {
                                preferenceManager.clearWhitelistedPreferences()
                                authRepository.signOut()
                                navController.popBackStack(
                                    navController.graph.startDestinationId,
                                    false
                                )
                            }
                        }
                    ) {
                        Text(stringResource(R.string.button_clear_and_sign_out))
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showSignOutDialog.value = false
                            scope.launch {
                                authRepository.signOut()
                                navController.popBackStack(
                                    navController.graph.startDestinationId,
                                    false
                                )
                            }
                        }
                    ) {
                        Text(stringResource(R.string.button_keep_and_sign_out))
                    }
                }
            )
        }

        // Delete Account Preferences Dialog  
        if (showDeletePrefsDialog.value) {
            AlertDialog(
                onDismissRequest = { showDeletePrefsDialog.value = false },
                title = { Text(stringResource(R.string.dialog_title_delete_account)) },
                text = { Text(stringResource(R.string.dialog_message_clear_preferences_delete)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showDeletePrefsDialog.value = false
                            scope.launch {
                                preferenceManager.clearWhitelistedPreferences()
                                deleteAccount()
                            }
                        }
                    ) {
                        Text(stringResource(R.string.button_clear_and_delete))
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showDeletePrefsDialog.value = false
                            deleteAccount()
                        }
                    ) {
                        Text(stringResource(R.string.button_keep_and_delete))
                    }
                }
            )
        }
    }
}

/**
 * Account information section with user email
 */
@Composable
private fun AccountInfoSection(email: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Text(
                text = stringResource(R.string.account_information),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Email,
                    contentDescription = "Email",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Column {
                    Text(
                        text = stringResource(R.string.label_email_address),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = email,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

/**
 * Pro status section with loading and status display
 */
@Composable
private fun ProStatusSection(
    proStatus: String,
    isLoading: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (proStatus.contains("Pro", ignoreCase = true)) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "Pro Status",
                        tint = if (proStatus.contains("Pro", ignoreCase = true)) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column {
                    Text(
                        text = stringResource(R.string.subscription_status),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (isLoading) {
                            stringResource(R.string.loading_status)
                        } else {
                            proStatus.ifEmpty { stringResource(R.string.subscription_free) }
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

/**
 * Account actions section with sign out and delete account buttons
 * Uses AdaptiveStack for responsive layout on different screen sizes
 */
@Composable
private fun AccountActionsSection(
    onSignOut: () -> Unit,
    onDeleteAccount: () -> Unit
) {
    AdaptiveStack(
        modifier = Modifier.fillMaxWidth(),
        spacing = 16.dp
    ) {
        ActionButton(
            textResId = R.string.button_sign_out,
            onClick = onSignOut,
            modifier = Modifier.adaptiveFill(),
            applyPadding = false
        )

        ActionButton(
            textResId = R.string.button_delete_account,
            onClick = onDeleteAccount,
            type = ActionButtonType.DESTRUCTIVE,
            modifier = Modifier.adaptiveFill(),
            applyPadding = false
        )
    }
}