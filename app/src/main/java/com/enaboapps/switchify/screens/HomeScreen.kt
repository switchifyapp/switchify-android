package com.enaboapps.switchify.screens

import android.util.Log
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Feedback
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.enaboapps.switchify.BuildConfig
import com.enaboapps.switchify.R
import com.enaboapps.switchify.backend.engagement.ProReminderManager
import com.enaboapps.switchify.backend.iap.IAPHandler
import com.enaboapps.switchify.backend.preferences.PreferenceManager
import com.enaboapps.switchify.backend.review.ReviewPrompter
import com.enaboapps.switchify.components.AlertSeverity
import com.enaboapps.switchify.components.BaseView
import com.enaboapps.switchify.components.InAppUpdateBar
import com.enaboapps.switchify.components.InlineAlertCard
import com.enaboapps.switchify.components.Panel
import com.enaboapps.switchify.components.ScrollableView
import com.enaboapps.switchify.components.home.HomeHeroCard
import com.enaboapps.switchify.components.PanelListRow
import com.enaboapps.switchify.components.home.HomePcConnectionCard
import com.enaboapps.switchify.components.home.HomeToggleRow
import com.enaboapps.switchify.components.home.ProUpgradeCard
import com.enaboapps.switchify.nav.NavigationRoute
import com.enaboapps.switchify.service.camera.CameraPermissionManager
import com.enaboapps.switchify.service.utils.ServiceUtils
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
    var isPro by remember { mutableStateOf(false) }
    val switchEventStore = remember { SwitchEventStore.getInstance() }
    val switchConfigValidator = remember { SwitchConfigValidator(context) }
    var isSwitchConfigValid by remember { mutableStateOf(true) }
    var scanModeName by remember { mutableStateOf<String?>(null) }
    var switchCount by remember { mutableStateOf(0) }
    val proReminderManager = remember { ProReminderManager(context) }
    var showProReminder by remember { mutableStateOf(false) }
    var isReady by remember { mutableStateOf(false) }

    val customerInfo by IAPHandler.customerInfo.collectAsState()
    LaunchedEffect(customerInfo) {
        val proPurchased = customerInfo?.entitlements?.get(IAPHandler.ENTITLEMENT)?.isActive == true
        isPro = proPurchased
        if (!proPurchased && customerInfo != null) {
            proReminderManager.recordAppOpen()
            showProReminder = proReminderManager.shouldShowReminder()
        }
    }

    var hasNavigatedToOnboarding by remember { mutableStateOf(false) }

    LaunchedEffect(isSetupComplete) {
        if (!isSetupComplete && !hasNavigatedToOnboarding) {
            hasNavigatedToOnboarding = true
            navController.navigate(NavigationRoute.Onboarding.name) {
                launchSingleTop = true
            }
        }
        IAPHandler.initIfNeeded(context)

        switchEventStore.initializeAsync(context)
        isSwitchConfigValid = switchConfigValidator.isConfigurationValid()
        scanModeName = switchConfigValidator.getCurrentScanModeName()
        switchCount = switchEventStore.getCount()
        isReady = true
    }

    val reviewManager = remember { ReviewManagerFactory.create(context) }

    LaunchedEffect(Unit) { ReviewPrompter.requestIfDue(context, reviewManager) }

    val hasCameraPermission =
        remember { CameraPermissionManager.getInstance(context).hasPermission() }
    val showHeadToggle = isAccessibilityServiceEnabled && hasCameraPermission
    val showCameraAlert = isAccessibilityServiceEnabled && !hasCameraPermission

    BaseView(
        titleResId = R.string.screen_title_switchify,
        navController = navController,
        enableScroll = false,
        bottomBar = {
            InAppUpdateBar { msg ->
                Log.e("HomeScreen", msg)
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            }
        }
    ) {
        ScrollableView {
            AnimatedVisibility(
                visible = isReady,
                enter = fadeIn(animationSpec = tween(300, delayMillis = 50))
            ) {
                Panel(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                        HomeHeroCard(
                            isAccessibilityServiceEnabled = isAccessibilityServiceEnabled,
                            scanModeName = scanModeName,
                            switchCount = switchCount,
                            isConfigValid = isSwitchConfigValid,
                            shape = RectangleShape,
                            onPrimaryAction = {
                                navController.navigate(NavigationRoute.EnableAccessibilityService.name)
                            }
                        )
                        HomeToggleRow(
                            showHeadToggle = showHeadToggle,
                            onSettingsClick = { navController.navigate(NavigationRoute.Settings.name) }
                        )
                        HomePcConnectionCard(navController)

                        AnimatedVisibility(
                            visible = !isSwitchConfigValid,
                            enter = fadeIn(tween(220)) + expandVertically(tween(220)),
                            exit = fadeOut(tween(220)) + shrinkVertically(tween(220))
                        ) {
                            InlineAlertCard(
                                severity = AlertSeverity.ERROR,
                                titleResId = R.string.switch_config_banner_title,
                                descriptionResId = R.string.switch_config_banner_description,
                                leadingIcon = Icons.Rounded.Warning,
                                shape = RectangleShape,
                                onClick = { navController.navigate(NavigationRoute.Switches.name) }
                            )
                        }

                        AnimatedVisibility(
                            visible = !isPro,
                            enter = fadeIn(tween(220)) + expandVertically(tween(220)),
                            exit = fadeOut(tween(220)) + shrinkVertically(tween(220))
                        ) {
                            ProUpgradeCard(
                                isReminder = showProReminder,
                                shape = RectangleShape,
                                onLearnMore = { navController.navigate(NavigationRoute.Paywall.name) },
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

                        PanelListRow(
                            titleResId = R.string.home_feedback_title,
                            summaryResId = R.string.home_feedback_summary,
                            leadingIcon = Icons.Rounded.Feedback,
                            onClick = { navController.navigate(NavigationRoute.UserFeedback.name) }
                        )

                        AnimatedVisibility(
                            visible = showCameraAlert,
                            enter = fadeIn(tween(220)) + expandVertically(tween(220)),
                            exit = fadeOut(tween(220)) + shrinkVertically(tween(220))
                        ) {
                            PanelListRow(
                                titleResId = R.string.home_camera_permission_title,
                                summaryResId = R.string.home_camera_permission_summary,
                                leadingIcon = Icons.Rounded.CameraAlt,
                                onClick = { navController.navigate(NavigationRoute.CameraSettings.name) }
                            )
                        }

                        if (BuildConfig.DEBUG) {
                            PanelListRow(
                                titleResId = R.string.screen_title_debug,
                                summaryResId = R.string.screen_summary_debug,
                                leadingIcon = Icons.Rounded.BugReport,
                                onClick = { navController.navigate(NavigationRoute.Debug.name) }
                            )
                        }
                    }
                }
            }
        }
    }
}
