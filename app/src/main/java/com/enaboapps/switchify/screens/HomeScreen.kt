package com.enaboapps.switchify.screens

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.enaboapps.switchify.auth.AuthManager
import com.enaboapps.switchify.backend.iap.IAPHandler
import com.enaboapps.switchify.backend.preferences.PreferenceManager
import com.enaboapps.switchify.components.BaseView
import com.enaboapps.switchify.components.NavBarAction
import com.enaboapps.switchify.keyboard.utils.KeyboardUtils
import com.enaboapps.switchify.nav.NavigationRoute
import com.enaboapps.switchify.service.utils.ServiceUtils
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import com.google.android.play.core.review.ReviewManager
import com.google.android.play.core.review.ReviewManagerFactory

@Composable
fun HomeScreen(navController: NavController, serviceUtils: ServiceUtils = ServiceUtils()) {
    val context = LocalContext.current
    val isAccessibilityServiceEnabled = serviceUtils.isAccessibilityServiceEnabled(context)
    val isSwitchifyKeyboardEnabled = KeyboardUtils.isSwitchifyKeyboardEnabled(context)
    val isSetupComplete = PreferenceManager(context).isSetupComplete()
    val isPro = remember { mutableStateOf(true) }
    val signedIn = AuthManager.instance.isUserSignedIn()
    var showUpdateDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (!isSetupComplete && !signedIn) {
            navController.navigate(NavigationRoute.Setup.name)
        } else if (signedIn) {
            PreferenceManager(context).setSetupComplete()
        }
        IAPHandler.refreshPurchaseStatus { proPurchased ->
            isPro.value = proPurchased
        }
    }

    val appUpdateManager = remember { AppUpdateManagerFactory.create(context) }
    val installStateUpdatedListener = remember {
        InstallStateUpdatedListener { state ->
            when (state.installStatus()) {
                InstallStatus.DOWNLOADED -> {
                    Log.d("HomeScreen", "Update downloaded")
                    showUpdateDialog = true
                }

                InstallStatus.FAILED -> {
                    Log.e("HomeScreen", "Update failed! State: ${state.installErrorCode()}")
                }

                InstallStatus.INSTALLED -> {
                    Log.d("HomeScreen", "Update installed successfully")
                }

                else -> {
                    Log.d("HomeScreen", "Install Status: ${state.installStatus()}")
                }
            }
        }
    }

    val updateResultLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        when (result.resultCode) {
            Activity.RESULT_OK -> {
                Log.d("HomeScreen", "Update flow started successfully")
                Toast.makeText(context, "Downloading update...", Toast.LENGTH_SHORT).show()
            }

            Activity.RESULT_CANCELED -> {
                Log.d("HomeScreen", "Update cancelled by user")
                Toast.makeText(context, "Update cancelled", Toast.LENGTH_SHORT).show()
            }

            else -> {
                Log.e("HomeScreen", "Update flow failed! Result code: ${result.resultCode}")
                Toast.makeText(context, "Update failed to start", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val reviewManager = remember { ReviewManagerFactory.create(context) }

    LaunchedEffect(Unit) {
        appUpdateManager.registerListener(installStateUpdatedListener)
        checkForUpdates(context, appUpdateManager, updateResultLauncher)
        checkForDownloadedUpdate(appUpdateManager) { showUpdateDialog = it }
        requestReview(context, reviewManager)
    }

    DisposableEffect(Unit) {
        onDispose {
            appUpdateManager.unregisterListener(installStateUpdatedListener)
        }
    }

    BaseView(
        title = "Switchify",
        navController = navController,
        enableScroll = false,
        navBarActions = listOf(NavBarAction(text = "Feedback", onClick = {
            val url = "https://switchify.featurebase.app/"
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }))
    ) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 300.dp),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Settings Card
            item {
                GridCard(
                    title = "Settings",
                    summary = "Tap here to adjust your settings.",
                    onClick = { navController.navigate(NavigationRoute.Settings.name) },
                    icon = {
                        Icon(
                            imageVector = Icons.Rounded.Settings,
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                )
            }

            // Accessibility Service Card
            if (!isAccessibilityServiceEnabled) {
                item {
                    GridCard(
                        title = "Accessibility Service",
                        summary = "Tap here to enable the accessibility service.",
                        onClick = { navController.navigate(NavigationRoute.EnableAccessibilityService.name) },
                        icon = {
                            Icon(
                                imageVector = Icons.Rounded.AccessibilityNew,
                                contentDescription = "Accessibility",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    )
                }
            }

            // Keyboard Card
            if (!isSwitchifyKeyboardEnabled) {
                item {
                    GridCard(
                        title = "Switchify Keyboard",
                        summary = "Tap here to enable the Switchify keyboard.",
                        onClick = { navController.navigate(NavigationRoute.EnableSwitchifyKeyboard.name) },
                        icon = {
                            Icon(
                                imageVector = Icons.Rounded.Keyboard,
                                contentDescription = "Keyboard",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    )
                }
            }

            // Pro Upgrade Card
            if (!isPro.value) {
                item {
                    GridCard(
                        title = "Upgrade to Pro",
                        summary = "Upgrade to Pro to unlock new features and support Switchify.",
                        onClick = { navController.navigate(NavigationRoute.Paywall.name) },
                        icon = {
                            Icon(
                                imageVector = Icons.Rounded.Star,
                                contentDescription = "Pro",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    )
                }
            }

            // Account Card
            item {
                AccountGridCard(navController)
            }
        }

        if (showUpdateDialog) {
            AlertDialog(
                onDismissRequest = { showUpdateDialog = false },
                title = { Text("Update Available") },
                text = { Text("A new version has been downloaded. Restart now to complete the installation?") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showUpdateDialog = false
                            appUpdateManager.completeUpdate()
                        }
                    ) {
                        Text("Restart")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showUpdateDialog = false }
                    ) {
                        Text("Later")
                    }
                }
            )
        }
    }
}

@Composable
private fun GridCard(
    title: String,
    summary: String,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            icon()
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun AccountGridCard(navController: NavController) {
    val authManager = AuthManager.instance
    val isUserSignedIn = authManager.isUserSignedIn()
    val currentUser = authManager.getCurrentUser()

    GridCard(
        title = if (isUserSignedIn) "Account" else "Sign In",
        summary = if (isUserSignedIn) {
            currentUser?.email ?: ""
        } else {
            "Sign in to access your settings."
        },
        onClick = {
            navController.navigate(
                if (isUserSignedIn) NavigationRoute.Account.name
                else NavigationRoute.SignIn.name
            )
        },
        icon = {
            Icon(
                imageVector = if (isUserSignedIn) Icons.Rounded.AccountCircle else Icons.AutoMirrored.Default.Login,
                contentDescription = if (isUserSignedIn) "Account" else "Sign In",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
        }
    )
}

private fun checkForUpdates(
    context: Context,
    appUpdateManager: AppUpdateManager,
    updateResultLauncher: androidx.activity.result.ActivityResultLauncher<IntentSenderRequest>
) {
    appUpdateManager.appUpdateInfo
        .addOnSuccessListener { appUpdateInfo ->
            if (appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE &&
                appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)
            ) {
                try {
                    val updateOptions = AppUpdateOptions.newBuilder(AppUpdateType.FLEXIBLE).build()
                    appUpdateManager.startUpdateFlowForResult(
                        appUpdateInfo,
                        updateResultLauncher,
                        updateOptions
                    )
                    Log.d("HomeScreen", "Update available. Requesting update.")
                } catch (e: Exception) {
                    Log.e("HomeScreen", "Error starting update flow", e)
                    Toast.makeText(
                        context,
                        "Failed to start update process",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } else {
                Log.d("HomeScreen", "No update available or update type not allowed")
            }
        }
        .addOnFailureListener { exception ->
            Log.e("HomeScreen", "Failed to check for updates", exception)
            Toast.makeText(
                context,
                "Failed to check for updates",
                Toast.LENGTH_SHORT
            ).show()
        }
}

private fun checkForDownloadedUpdate(
    appUpdateManager: AppUpdateManager,
    callback: (Boolean) -> Unit
) {
    appUpdateManager.appUpdateInfo.addOnSuccessListener { info ->
        if (info.installStatus() == InstallStatus.DOWNLOADED) {
            callback(true)
        }
    }
}

private fun requestReview(context: Context, reviewManager: ReviewManager) {
    val request = reviewManager.requestReviewFlow()
    request.addOnCompleteListener { task ->
        if (task.isSuccessful) {
            val reviewInfo = task.result
            reviewManager.launchReviewFlow(context as Activity, reviewInfo)
                .addOnCompleteListener {
                    Log.d("HomeScreen", "Review flow completed")
                }
        } else {
            Log.e("HomeScreen", "Error requesting review flow", task.exception)
        }
    }
}