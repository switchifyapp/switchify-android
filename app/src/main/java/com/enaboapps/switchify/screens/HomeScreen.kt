package com.enaboapps.switchify.screens

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.rounded.AccessibilityNew
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.navigation.NavController
import com.enaboapps.switchify.BuildConfig
import com.enaboapps.switchify.R
import com.enaboapps.switchify.auth.AuthManager
import com.enaboapps.switchify.backend.iap.IAPHandler
import com.enaboapps.switchify.backend.preferences.PreferenceManager
import com.enaboapps.switchify.components.BaseView
import com.enaboapps.switchify.components.NavBarAction
import com.enaboapps.switchify.components.StatusBannerComponent
import com.enaboapps.switchify.nav.NavigationRoute
import com.enaboapps.switchify.service.utils.ServiceUtils
import com.enaboapps.switchify.service.utils.QuickAppsManager
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
    val isSetupComplete = PreferenceManager(context).isSetupComplete()
    val isPro = remember { mutableStateOf(true) }
    val signedIn = AuthManager.instance.isUserSignedIn()
    var showUpdateDialog by remember { mutableStateOf(false) }
    var updateProgress by remember { mutableFloatStateOf(0f) }
    var isDownloading by remember { mutableStateOf(false) }
    val quickAppsManager = remember { QuickAppsManager(context) }
    val hasUsageStatsPermission = remember { mutableStateOf(quickAppsManager.hasUsageStatsPermission()) }

    LaunchedEffect(Unit) {
        if (!isSetupComplete && !signedIn) {
            navController.navigate(NavigationRoute.Onboarding.name)
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
                InstallStatus.DOWNLOADING -> {
                    isDownloading = true
                    val totalBytes = state.totalBytesToDownload().toFloat()
                    if (totalBytes > 0) { // Prevent division by zero
                        updateProgress = state.bytesDownloaded().toFloat() / totalBytes
                    }
                }

                InstallStatus.DOWNLOADED -> {
                    isDownloading = false
                    showUpdateDialog = true
                }

                InstallStatus.FAILED -> {
                    isDownloading = false
                    Log.e("HomeScreen", "Update failed! Error code: ${state.installErrorCode()}")
                    Toast.makeText(
                        context,
                        "Update failed: ${state.installErrorCode()}",
                        Toast.LENGTH_SHORT
                    ).show()
                }

                InstallStatus.INSTALLED -> {
                    isDownloading = false
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

    // Combine the effects to ensure proper registration/unregistration
    DisposableEffect(Unit) {
        // Register the listener
        appUpdateManager.registerListener(installStateUpdatedListener)

        // Check for updates
        checkForUpdates(context, appUpdateManager, updateResultLauncher)

        // Check if an update has already been downloaded
        appUpdateManager.appUpdateInfo.addOnSuccessListener { info ->
            if (info.installStatus() == InstallStatus.DOWNLOADED) {
                showUpdateDialog = true
            }
        }

        // Request review
        requestReview(context, reviewManager)

        onDispose {
            // Always unregister the listener when the composable is disposed
            appUpdateManager.unregisterListener(installStateUpdatedListener)
        }
    }

    BaseView(
        titleResId = R.string.screen_title_switchify,
        navController = navController,
        enableScroll = false,
        navBarActions = listOf(
            NavBarAction(
                textResId = R.string.action_feedback,
                onClick = {
                    val url = "https://switchify.featurebase.app/"
                    context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
                }
            )
        ),
        headerContent = {
            StatusBannerComponent(
                isAccessibilityServiceEnabled = isAccessibilityServiceEnabled,
                isPro = isPro.value,
                onAccessibilityClick = { 
                    navController.navigate(NavigationRoute.EnableAccessibilityService.name) 
                },
                onProUpgradeClick = { 
                    navController.navigate(NavigationRoute.Paywall.name) 
                }
            )
        }
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Update Progress
            if (isDownloading) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.dialog_title_downloading_update),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "${(updateProgress * 100).toInt()}%",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    LinearProgressIndicator(
                        progress = { updateProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                    )
                }
            }

            // Grid Layout
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
            ) {
            // Settings Card
            item {
                GridCard(
                    titleResId = R.string.screen_title_settings,
                    summaryResId = R.string.screen_summary_settings,
                    onClick = { navController.navigate(NavigationRoute.Settings.name) },
                    icon = {
                        Icon(
                            imageVector = Icons.Rounded.Settings,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                )
            }


            // Account Card
            item {
                AccountGridCard(navController)
            }

            // Quick Apps Permission Card (only show if permission not granted)
            if (!hasUsageStatsPermission.value) {
                item {
                    GridCard(
                        titleResId = R.string.menu_title_quick_apps,
                        summaryResId = R.string.screen_summary_quick_apps_permission,
                        onClick = { navController.navigate(NavigationRoute.UsageStatsPermission.name) },
                        icon = {
                            Icon(
                                imageVector = Icons.Rounded.Apps,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    )
                }
            }

            // Debug Card (only visible in debug mode)
            if (BuildConfig.DEBUG) {
                item {
                    GridCard(
                        titleResId = R.string.screen_title_debug,
                        summaryResId = R.string.screen_summary_debug,
                        onClick = { navController.navigate(NavigationRoute.Debug.name) },
                        icon = {
                            Icon(
                                imageVector = Icons.Rounded.BugReport,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    )
                }
            }
        }
        } // End Column

        if (showUpdateDialog) {
            AlertDialog(
                onDismissRequest = { showUpdateDialog = false },
                title = { Text(stringResource(R.string.dialog_title_update)) },
                text = { Text(stringResource(R.string.dialog_message_update)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showUpdateDialog = false
                            appUpdateManager.completeUpdate()
                        }
                    ) {
                        Text(stringResource(R.string.dialog_button_restart))
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showUpdateDialog = false }
                    ) {
                        Text(stringResource(R.string.dialog_button_later))
                    }
                }
            )
        }
    }
}

@Composable
private fun GridCard(
    titleResId: Int,
    summaryResId: Int,
    summaryArgs: Array<Any>? = null,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .padding(bottom = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                icon()
            }
            Text(
                text = stringResource(titleResId),
                style = MaterialTheme.typography.titleMedium,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
            Text(
                text = if (summaryArgs != null) {
                    stringResource(summaryResId, *summaryArgs)
                } else {
                    stringResource(summaryResId)
                },
                style = MaterialTheme.typography.bodySmall,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp),
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun AccountGridCard(navController: NavController) {
    val authManager = AuthManager.instance
    val isUserSignedIn = authManager.isUserSignedIn()
    val currentUser = authManager.getCurrentUser()

    GridCard(
        titleResId = if (isUserSignedIn) R.string.screen_title_account else R.string.screen_title_sign_in,
        summaryResId = if (isUserSignedIn) {
            currentUser?.email?.let { R.string.screen_summary_account_email }
                ?: R.string.screen_summary_account_no_email
        } else {
            R.string.screen_summary_sign_in_to_access_settings
        },
        summaryArgs = if (isUserSignedIn) {
            currentUser?.email?.let { arrayOf(it) }
        } else {
            null
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
                modifier = Modifier.fillMaxSize()
            )
        }
    )
}

private fun checkForUpdates(
    context: Context,
    appUpdateManager: AppUpdateManager,
    updateResultLauncher: androidx.activity.result.ActivityResultLauncher<IntentSenderRequest>
) {
    try {
        appUpdateManager.appUpdateInfo
            .addOnSuccessListener { appUpdateInfo ->
                if (appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE &&
                    appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)
                ) {
                    try {
                        val updateOptions =
                            AppUpdateOptions.newBuilder(AppUpdateType.FLEXIBLE).build()
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
                            "Failed to start update: ${e.localizedMessage}",
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
                    "Failed to check for updates: ${exception.localizedMessage}",
                    Toast.LENGTH_SHORT
                ).show()
            }
    } catch (e: Exception) {
        Log.e("HomeScreen", "Exception during update check", e)
    }
}

private fun requestReview(context: Context, reviewManager: ReviewManager) {
    try {
        val request = reviewManager.requestReviewFlow()
        request.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val reviewInfo = task.result
                try {
                    reviewManager.launchReviewFlow(context as Activity, reviewInfo)
                        .addOnCompleteListener {
                            Log.d("HomeScreen", "Review flow completed")
                        }
                        .addOnFailureListener { e ->
                            Log.e("HomeScreen", "Error launching review flow", e)
                        }
                } catch (e: Exception) {
                    Log.e("HomeScreen", "Exception launching review flow", e)
                }
            } else {
                Log.e("HomeScreen", "Error requesting review flow", task.exception)
            }
        }
    } catch (e: Exception) {
        Log.e("HomeScreen", "Exception in requestReview", e)
    }
}