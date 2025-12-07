package com.enaboapps.switchify.screens

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Feedback
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.enaboapps.switchify.BuildConfig
import com.enaboapps.switchify.R
import com.enaboapps.switchify.backend.engagement.ProReminderManager
import com.enaboapps.switchify.backend.iap.IAPHandler
import com.enaboapps.switchify.backend.preferences.PreferenceManager
import com.enaboapps.switchify.backend.review.ReviewPrompter
import com.enaboapps.switchify.components.BaseView
import com.enaboapps.switchify.components.CollapsibleActionList
import com.enaboapps.switchify.components.HeadControlToggleCard
import com.enaboapps.switchify.components.InAppUpdateBar
import com.enaboapps.switchify.components.ProReminderBanner
import com.enaboapps.switchify.components.ScrollableView
import com.enaboapps.switchify.components.StatusBannerComponent
import com.enaboapps.switchify.nav.NavigationRoute
import com.enaboapps.switchify.service.camera.CameraPermissionManager
import com.enaboapps.switchify.service.utils.QuickAppsManager
import com.enaboapps.switchify.service.utils.ServiceUtils
import com.enaboapps.switchify.switches.SwitchConfigInvalidBanner
import com.enaboapps.switchify.switches.SwitchConfigValidator
import com.enaboapps.switchify.switches.SwitchEventStore
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.android.play.core.review.ReviewManagerFactory

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun HomeScreen(navController: NavController, serviceUtils: ServiceUtils = ServiceUtils()) {
    val context = LocalContext.current
    val isAccessibilityServiceEnabled = serviceUtils.isAccessibilityServiceEnabled(context)
    val isSetupComplete = PreferenceManager(context).isSetupComplete()
    // Default to false (show upgrade banner) until confirmed pro via RevenueCat
    var isPro by remember { mutableStateOf(false) }
    val switchEventStore = remember { SwitchEventStore.getInstance() }
    val switchConfigValidator = remember { SwitchConfigValidator(context) }
    var isSwitchConfigValid by remember { mutableStateOf(true) }
    val quickAppsManager = remember { QuickAppsManager(context) }
    val hasUsageStatsPermission =
        remember { mutableStateOf(quickAppsManager.hasUsageStatsPermission()) }
    var isActionListExpanded by remember { mutableStateOf(false) }
    val proReminderManager = remember { ProReminderManager(context) }
    var showProReminder by remember { mutableStateOf(false) }

    // Observe CustomerInfo for real-time pro status updates
    val customerInfo by IAPHandler.customerInfo.collectAsState()
    LaunchedEffect(customerInfo) {
        val proPurchased = customerInfo?.entitlements?.get(IAPHandler.ENTITLEMENT)?.isActive == true
        isPro = proPurchased
        if (!proPurchased && customerInfo != null) {
            proReminderManager.recordAppOpen()
            showProReminder = proReminderManager.shouldShowReminder()
        }
    }

    LaunchedEffect(Unit) {
        if (!isSetupComplete) {
            navController.navigate(NavigationRoute.Onboarding.name)
        }
        // Initialize RevenueCat and refresh status
        IAPHandler.initIfNeeded(context) {
            IAPHandler.refreshPurchaseStatus()
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
                isPro = isPro,
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
        ScrollableView {
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

                // Pro Reminder Banner (for non-Pro users after usage threshold)
                if (showProReminder) {
                    ProReminderBanner(
                        onLearnMore = {
                            navController.navigate(NavigationRoute.Paywall.name)
                        },
                        onDismiss = {
                            proReminderManager.onDismiss()
                            showProReminder = false
                        },
                        onRemindLater = {
                            proReminderManager.onRemindLater()
                            showProReminder = false
                        }
                    )
                }

                // Collapsible Quick Actions List
                CollapsibleActionList(
                    isExpanded = isActionListExpanded,
                    onToggleExpanded = { isActionListExpanded = !isActionListExpanded },
                    navController = navController,
                    hasUsageStatsPermission = hasUsageStatsPermission.value,
                    showDebug = BuildConfig.DEBUG,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Determine whether to show head control toggle
                val hasCameraPermission =
                    remember { CameraPermissionManager.getInstance(context).hasPermission() }
                val showHeadToggle = isAccessibilityServiceEnabled && hasCameraPermission

                // Settings and second slot (Head Control when available, otherwise Feedback)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Settings Card
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
                        },
                        modifier = Modifier.weight(1f)
                    )
                    if (showHeadToggle) {
                        HeadControlToggleCard(modifier = Modifier.weight(1f))
                    } else {
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
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                // Reuse showHeadToggle to place Feedback below when Head Control is shown above
                if (showHeadToggle) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
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
                            },
                            modifier = Modifier.weight(1f)
                        )
                        Box(modifier = Modifier.weight(1f))
                    }
                }
            } // End Column
        } // End ScrollableView

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
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
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
