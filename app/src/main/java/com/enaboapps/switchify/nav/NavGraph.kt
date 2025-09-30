package com.enaboapps.switchify.nav

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.enaboapps.switchify.screens.DebugScreen
import com.enaboapps.switchify.screens.EnableAccessibilityServiceScreen
import com.enaboapps.switchify.screens.HomeScreen
import com.enaboapps.switchify.screens.UserFeedbackScreen
import com.enaboapps.switchify.screens.account.AccountScreen
import com.enaboapps.switchify.screens.account.AuthScreen
import com.enaboapps.switchify.screens.onboarding.OnboardingScreen
import com.enaboapps.switchify.screens.paywall.AppPaywallScreen
import com.enaboapps.switchify.screens.permissions.UsageStatsPermissionScreen
import com.enaboapps.switchify.screens.settings.CameraSettingsScreen
import com.enaboapps.switchify.screens.settings.HeadControlSettingsScreen
import com.enaboapps.switchify.screens.settings.SettingsScreen
import com.enaboapps.switchify.screens.settings.gestures.GestureSettingsScreen
import com.enaboapps.switchify.screens.settings.patterns.GesturePatternsScreen
import com.enaboapps.switchify.screens.settings.scanning.AutoScanSettingsScreen
import com.enaboapps.switchify.screens.settings.scanning.ManualScanSettingsScreen
import com.enaboapps.switchify.screens.settings.scanning.ScanColorSelectionScreen
import com.enaboapps.switchify.screens.settings.scanning.ScanSpeedsScreen
import com.enaboapps.switchify.screens.settings.switches.AddEditCameraSwitchScreen
import com.enaboapps.switchify.screens.settings.switches.AddEditExternalSwitchScreen
import com.enaboapps.switchify.screens.settings.switches.CameraSwitchesScreen
import com.enaboapps.switchify.screens.settings.switches.ExternalSwitchesScreen
import com.enaboapps.switchify.screens.settings.switches.SwitchStabilityScreen
import com.enaboapps.switchify.screens.settings.switches.SwitchesScreen
import com.enaboapps.switchify.screens.settings.techniques.AccessTechniqueSettingsScreen

@Composable
fun NavGraph(navController: NavHostController) {
    NavHost(navController, startDestination = NavigationRoute.Home.name) {
        composable(NavigationRoute.Home.name) {
            HomeScreen(navController)
        }
        composable(NavigationRoute.Onboarding.name) {
            OnboardingScreen(navController)
        }
        composable(
            route = NavigationRoute.Paywall.name
        ) {
            AppPaywallScreen(navController)
        }
        composable(NavigationRoute.Authentication.name) {
            AuthScreen(navController)
        }
        composable(NavigationRoute.Account.name) {
            AccountScreen(navController)
        }
        composable(NavigationRoute.Settings.name) {
            SettingsScreen(navController)
        }
        composable(NavigationRoute.SwitchStability.name) {
            SwitchStabilityScreen(navController)
        }
        composable(NavigationRoute.ScanSpeeds.name) {
            ScanSpeedsScreen(navController)
        }
        composable(NavigationRoute.ManualScanSettings.name) {
            ManualScanSettingsScreen(navController)
        }
        composable(NavigationRoute.ScanColor.name) {
            ScanColorSelectionScreen(navController)
        }
        composable(NavigationRoute.GestureSettings.name) {
            GestureSettingsScreen(navController)
        }
        composable(NavigationRoute.Switches.name) {
            SwitchesScreen(navController)
        }
        composable(NavigationRoute.ExternalSwitches.name) {
            ExternalSwitchesScreen(navController)
        }
        composable(NavigationRoute.CameraSwitches.name) {
            CameraSwitchesScreen(navController)
        }
        composable(NavigationRoute.AddNewExternalSwitch.name) {
            AddEditExternalSwitchScreen(navController)
        }
        composable("${NavigationRoute.EditExternalSwitch.name}/{code}") {
            it.arguments?.getString("code")?.let { code ->
                AddEditExternalSwitchScreen(navController, code)
            }
        }
        composable(NavigationRoute.EnableAccessibilityService.name) {
            EnableAccessibilityServiceScreen(navController)
        }
        composable(NavigationRoute.AutoScanSettings.name) {
            AutoScanSettingsScreen(navController)
        }
        composable(NavigationRoute.AccessTechniqueSettings.name) {
            AccessTechniqueSettingsScreen(navController)
        }
        composable(NavigationRoute.AddNewCameraSwitch.name) {
            AddEditCameraSwitchScreen(navController)
        }
        composable("${NavigationRoute.EditCameraSwitch.name}/{code}") {
            it.arguments?.getString("code")?.let { code ->
                AddEditCameraSwitchScreen(navController, code)
            }
        }
        composable(NavigationRoute.Debug.name) {
            DebugScreen(navController)
        }
        composable(NavigationRoute.GesturePatterns.name) {
            GesturePatternsScreen(navController)
        }
        composable(NavigationRoute.UsageStatsPermission.name) {
            UsageStatsPermissionScreen(navController)
        }
        composable(NavigationRoute.UserFeedback.name) {
            UserFeedbackScreen(navController)
        }
        composable(NavigationRoute.CameraSettings.name) {
            CameraSettingsScreen(navController)
        }
        composable(NavigationRoute.HeadControlSettings.name) {
            HeadControlSettingsScreen(navController)
        }
    }
}
