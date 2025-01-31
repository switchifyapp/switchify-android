package com.enaboapps.switchify.screens.account

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.enaboapps.switchify.auth.AuthManager
import com.enaboapps.switchify.backend.iap.IAPHandler
import com.enaboapps.switchify.components.BaseView
import com.enaboapps.switchify.components.FullWidthButton
import com.enaboapps.switchify.nav.NavigationRoute
import com.google.firebase.auth.FirebaseAuth

@Composable
fun AccountScreen(navController: NavController) {
    val authManager = AuthManager.instance
    val currentUser = FirebaseAuth.getInstance().currentUser
    val userEmail = currentUser?.email ?: "Not Logged In"
    val showDeleteAccountDialog = remember { mutableStateOf(false) }
    val context = LocalContext.current

    val proStatus = remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        IAPHandler.getProStatus { status ->
            proStatus.value = status
        }
    }

    val deleteAccount = {
        authManager.deleteUser(onSuccess = {
            Toast.makeText(context, "Account deleted", Toast.LENGTH_SHORT).show()
            navController.popBackStack(navController.graph.startDestinationId, false)
        }, onFailure = {
            Toast.makeText(context, "Error deleting account", Toast.LENGTH_SHORT).show()
        })
    }

    BaseView(
        title = "Account",
        navController = navController
    ) {
        EmailAddressView(email = userEmail)

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "Pro Status: ${proStatus.value}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(20.dp))

        FullWidthButton(
            text = "Change Password",
            onClick = {
                navController.navigate(NavigationRoute.ChangePassword.name)
            }
        )

        FullWidthButton(
            text = "Sign Out",
            onClick = {
                authManager.signOut()
                navController.popBackStack(navController.graph.startDestinationId, false)
            }
        )

        Spacer(modifier = Modifier.height(20.dp))

        FullWidthButton(
            text = "Delete Account",
            onClick = {
                showDeleteAccountDialog.value = true
            }
        )

        if (showDeleteAccountDialog.value) {
            AlertDialog(
                onDismissRequest = { showDeleteAccountDialog.value = false },
                title = { Text("Delete Account") },
                text = { Text("Are you sure you want to delete your account? This action cannot be undone.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            deleteAccount()
                        }
                    ) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showDeleteAccountDialog.value = false }
                    ) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

/**
 * This composable represents the email address view
 * @param email The email address
 */
@Composable
private fun EmailAddressView(email: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(8.dp)
                .fillMaxWidth()
        ) {
            Text(
                text = "Email address",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(8.dp)
            )
            Row {
                Icon(
                    imageVector = Icons.Default.Email,
                    contentDescription = "Email Icon",
                    modifier = Modifier.padding(8.dp)
                )
                Text(text = email, modifier = Modifier.padding(8.dp))
            }
        }
    }
}