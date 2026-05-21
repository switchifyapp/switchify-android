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

import com.enaboapps.switchify.screens.settings.CameraSettingsScreen
import com.enaboapps.switchify.screens.settings.HeadControlSettingsScreen
import com.enaboapps.switchify.screens.settings.SettingsScreen
import com.enaboapps.switchify.screens.settings.replydrafter.ReplyDrafterSettingsScreen
import com.enaboapps.switchify.screens.settings.gestures.ScrollingSettingsScreen
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
import com.enaboapps.switchify.screens.settings.pause.PauseSettingsScreen
import com.enaboapps.switchify.screens.settings.favouriteapps.FavouriteAppsScreen
import com.enaboapps.switchify.screens.settings.menu.MenuCustomizationScreen
import com.enaboapps.switchify.screens.settings.switches.actions.SwitchActionSelectionScreen
import com.enaboapps.switchify.screens.settings.switches.LongPressActionsScreen
import com.enaboapps.switchify.screens.stats.StatsScreen

/**
 * Declares the app's navigation graph and registers each route to its corresponding screen composable.
 *
 * The graph's start destination is Home. The EditExternalSwitch and EditCameraSwitch routes accept a
 * "code" path argument and forward it to their respective screens when present.
 *
 * @param navController Controller used to host and navigate between destinations in this graph.
 */
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
        composable(NavigationRoute.ScrollingSettings.name) {
            ScrollingSettingsScreen(navController)
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

        composable(NavigationRoute.UserFeedback.name) {
            UserFeedbackScreen(navController)
        }
        composable(NavigationRoute.Stats.name) {
            StatsScreen(navController)
        }
        composable(NavigationRoute.CameraSettings.name) {
            CameraSettingsScreen(navController)
        }
        composable(NavigationRoute.HeadControlSettings.name) {
            HeadControlSettingsScreen(navController)
        }
        composable(NavigationRoute.PauseSettings.name) {
            PauseSettingsScreen(navController)
        }
        composable(NavigationRoute.MenuCustomization.name) {
            MenuCustomizationScreen(navController)
        }
        composable(NavigationRoute.FavouriteApps.name) {
            FavouriteAppsScreen(navController)
        }
        composable(NavigationRoute.ReplyDrafterSettings.name) {
            ReplyDrafterSettingsScreen(navController)
        }
        composable("${NavigationRoute.SwitchActionSelection.name}/{currentActionId}") {
            it.arguments?.getString("currentActionId")?.toIntOrNull()?.let { actionId ->
                SwitchActionSelectionScreen(navController, actionId)
            }
        }
        composable("${NavigationRoute.LongPressActions.name}/{code}") {
            it.arguments?.getString("code")?.let { code ->
                LongPressActionsScreen(navController, code)
            }
        }
    }
}