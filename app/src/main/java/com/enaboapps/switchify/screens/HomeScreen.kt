package com.enaboapps.switchify.screens

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Feedback
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.navigation.NavController
import com.enaboapps.switchify.BuildConfig
import com.enaboapps.switchify.R
import com.enaboapps.switchify.backend.iap.IAPHandler
import com.enaboapps.switchify.backend.preferences.PreferenceManager
import com.enaboapps.switchify.backend.review.ReviewPrompter
import com.enaboapps.switchify.components.BaseView
import com.enaboapps.switchify.components.InAppUpdateBar
import com.enaboapps.switchify.components.StatusBannerComponent
import com.enaboapps.switchify.nav.NavigationRoute
import com.enaboapps.switchify.service.utils.QuickAppsManager
import com.enaboapps.switchify.service.utils.ServiceUtils
import com.enaboapps.switchify.switches.SwitchConfigInvalidBanner
import com.enaboapps.switchify.utils.LogEvent
import com.enaboapps.switchify.utils.Logger
import com.enaboapps.switchify.switches.SwitchConfigValidator
import com.enaboapps.switchify.switches.SwitchEventStore
import com.google.android.play.core.review.ReviewManager
import com.google.android.play.core.review.ReviewManagerFactory

@Composable
fun HomeScreen(navController: NavController, serviceUtils: ServiceUtils = ServiceUtils()) {
    val context = LocalContext.current
    val isAccessibilityServiceEnabled = serviceUtils.isAccessibilityServiceEnabled(context)
    val isSetupComplete = PreferenceManager(context).isSetupComplete()
    val isPro = remember { mutableStateOf(true) }
    val switchEventStore = remember { SwitchEventStore.getInstance() }
    val switchConfigValidator = remember { SwitchConfigValidator(context) }
    var isSwitchConfigValid by remember { mutableStateOf(true) }
    val quickAppsManager = remember { QuickAppsManager(context) }
    val hasUsageStatsPermission =
        remember { mutableStateOf(quickAppsManager.hasUsageStatsPermission()) }

    LaunchedEffect(Unit) {
        if (!isSetupComplete) {
            navController.navigate(NavigationRoute.Onboarding.name)
        }
        IAPHandler.initIfNeeded(context) {
            IAPHandler.refreshPurchaseStatus { proPurchased ->
                isPro.value = proPurchased
            }
        }

        // Initialize switch store and wait for completion before validation
        switchEventStore.initializeAsync(context)
        isSwitchConfigValid = switchConfigValidator.isConfigurationValid()
    }

    val reviewManager = remember { ReviewManagerFactory.create(context) }

    LaunchedEffect(Unit) { ReviewPrompter.requestIfDue(context, reviewManager) }

    BaseView(
        titleResId = R.string.screen_title_switchify,
        navController = navController,
        enableScroll = false,
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
        },
        bottomBar = {
            InAppUpdateBar { msg ->
                Log.e("HomeScreen", msg)
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            }
        }
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Switch Configuration Banner
            if (!isSwitchConfigValid) {
                SwitchConfigInvalidBanner(
                    onClick = {
                        navController.navigate(NavigationRoute.Switches.name)
                    }
                )
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

                // Feedback Card
                item {
                    GridCard(
                        titleResId = R.string.home_feedback_title,
                        summaryResId = R.string.home_feedback_summary,
                        onClick = { navController.navigate(NavigationRoute.UserFeedback.name) },
                        icon = {
                            Icon(
                                imageVector = Icons.Rounded.Feedback,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    )
                }

                // Discord Card
                item {
                    val context = LocalContext.current
                    GridCard(
                        titleResId = R.string.home_discord_title,
                        summaryResId = R.string.home_discord_summary,
                        onClick = {
                            val intent =
                                Intent(Intent.ACTION_VIEW, "https://discord.gg/2VgnwhS9".toUri())
                            context.startActivity(intent)
                        },
                        icon = {
                            Icon(
                                imageVector = Icons.Rounded.Groups,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    )
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

        // Restart prompt handled in bottomBar component
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