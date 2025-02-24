package com.enaboapps.switchify.nav

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.enaboapps.switchify.screens.DebugScreen
import com.enaboapps.switchify.screens.EnableAccessibilityServiceScreen
import com.enaboapps.switchify.screens.HomeScreen
import com.enaboapps.switchify.screens.account.AccountScreen
import com.enaboapps.switchify.screens.account.ChangePasswordScreen
import com.enaboapps.switchify.screens.account.ForgotPasswordScreen
import com.enaboapps.switchify.screens.account.SignInScreen
import com.enaboapps.switchify.screens.account.SignUpScreen
import com.enaboapps.switchify.screens.paywall.AppPaywallScreen
import com.enaboapps.switchify.screens.settings.SettingsScreen
import com.enaboapps.switchify.screens.settings.actions.AddEditActionScreen
import com.enaboapps.switchify.screens.settings.actions.MyActionsScreen
import com.enaboapps.switchify.screens.settings.gestures.GestureSettingsScreen
import com.enaboapps.switchify.screens.settings.menu.MenuSizeScreen
import com.enaboapps.switchify.screens.settings.scanning.CursorSettingsScreen
import com.enaboapps.switchify.screens.settings.scanning.ItemScanSettingsScreen
import com.enaboapps.switchify.screens.settings.scanning.OtherScanSettingsScreen
import com.enaboapps.switchify.screens.settings.scanning.RadarSettingsScreen
import com.enaboapps.switchify.screens.settings.scanning.ScanColorSelectionScreen
import com.enaboapps.switchify.screens.settings.switches.AddEditCameraSwitchScreen
import com.enaboapps.switchify.screens.settings.switches.AddEditExternalSwitchScreen
import com.enaboapps.switchify.screens.settings.switches.SwitchStabilityScreen
import com.enaboapps.switchify.screens.settings.switches.SwitchesScreen
import com.enaboapps.switchify.screens.settings.switches.TestSwitchesScreen
import com.enaboapps.switchify.screens.setup.SetupScreen

@Composable
fun NavGraph(navController: NavHostController) {
    NavHost(navController, startDestination = NavigationRoute.Home.name) {
        composable(NavigationRoute.Home.name) {
            HomeScreen(navController)
        }
        composable(NavigationRoute.Setup.name) {
            SetupScreen(navController)
        }
        composable(NavigationRoute.Paywall.name) {
            AppPaywallScreen(navController)
        }
        composable(NavigationRoute.SignIn.name) {
            SignInScreen(navController)
        }
        composable(NavigationRoute.SignUp.name) {
            SignUpScreen(navController)
        }
        composable(NavigationRoute.ForgotPassword.name) {
            ForgotPasswordScreen(navController)
        }
        composable(NavigationRoute.Account.name) {
            AccountScreen(navController)
        }
        composable(NavigationRoute.ChangePassword.name) {
            ChangePasswordScreen(navController)
        }
        composable(NavigationRoute.Settings.name) {
            SettingsScreen(navController)
        }
        composable(NavigationRoute.SwitchStability.name) {
            SwitchStabilityScreen(navController)
        }
        composable(NavigationRoute.ScanColor.name) {
            ScanColorSelectionScreen(navController)
        }
        composable(NavigationRoute.GestureSettings.name) {
            GestureSettingsScreen(navController)
        }
        composable(NavigationRoute.CursorSettings.name) {
            CursorSettingsScreen(navController)
        }
        composable(NavigationRoute.Switches.name) {
            SwitchesScreen(navController)
        }
        composable(NavigationRoute.AddNewExternalSwitch.name) {
            AddEditExternalSwitchScreen(navController)
        }
        composable(NavigationRoute.TestSwitches.name) {
            TestSwitchesScreen(navController)
        }
        composable("${NavigationRoute.EditExternalSwitch.name}/{code}") {
            it.arguments?.getString("code")?.let { code ->
                AddEditExternalSwitchScreen(navController, code)
            }
        }
        composable(NavigationRoute.MenuSize.name) {
            MenuSizeScreen(navController)
        }
        composable(NavigationRoute.MyActions.name) {
            MyActionsScreen(navController)
        }
        composable(NavigationRoute.EnableAccessibilityService.name) {
            EnableAccessibilityServiceScreen(navController)
        }
        composable(NavigationRoute.AddMyActionsMenuItem.name) {
            AddEditActionScreen(navController)
        }
        composable("${NavigationRoute.EditMyActionsMenuItem.name}/{id}") {
            it.arguments?.getString("id")?.let { id ->
                AddEditActionScreen(navController, id)
            }
        }
        composable(NavigationRoute.ItemScanSettings.name) {
            ItemScanSettingsScreen(navController)
        }
        composable(NavigationRoute.OtherScanSettings.name) {
            OtherScanSettingsScreen(navController)
        }
        composable(NavigationRoute.RadarSettings.name) {
            RadarSettingsScreen(navController)
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
    }
}
