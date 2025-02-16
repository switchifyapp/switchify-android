package com.enaboapps.switchify.screens.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.enaboapps.switchify.R
import com.enaboapps.switchify.components.*
import com.enaboapps.switchify.nav.NavigationRoute
import com.enaboapps.switchify.screens.settings.models.SettingsScreenModel
import com.enaboapps.switchify.screens.settings.scanning.ScanMethodSelectionSection
import com.enaboapps.switchify.screens.settings.scanning.ScanModeSelectionSection

@Composable
fun SettingsScreen(navController: NavController) {
    val context = LocalContext.current
    val settingsScreenModel = SettingsScreenModel(context)
    var selectedTabIndex by remember { mutableIntStateOf(0) }

    BaseView(
        title = "Settings",
        navController = navController,
        padding = 0.dp,
        enableScroll = false
    ) {
        TabRow(selectedTabIndex = selectedTabIndex) {
            listOf("General", "Scanning", "Selection", "About").forEachIndexed { index, tab ->
                Tab(
                    selected = selectedTabIndex == index,
                    onClick = { selectedTabIndex = index },
                    text = { Text(tab) }
                )
            }
        }

        when (selectedTabIndex) {
            0 -> GeneralSettingsTab(settingsScreenModel, navController)
            1 -> ScanningSettingsTab(navController)
            2 -> SelectionSettingsTab(settingsScreenModel)
            3 -> AboutSection()
        }
    }
}

@Composable
fun GeneralSettingsTab(settingsScreenModel: SettingsScreenModel, navController: NavController) {
    ScrollableView {
        InputSection(navController)
        MenuSection(settingsScreenModel, navController)
        ActionsSection(navController)
    }
}

@Composable
fun ScanningSettingsTab(navController: NavController) {
    ScrollableView {
        ScanMethodSelectionSection()

        InfoCard(
            title = "Looking for timing and scan settings?",
            description = "Switchify is made up of three main methods: the cursor, the radar, and the item scan. " +
                    "Each method has its own set of settings that can be adjusted to suit your needs below." +
                    "\n\nTip: You can switch between methods while using Switchify by opening the menu and selecting the desired method."
        )

        Section(title = "Scanning Method Settings") {
            NavRouteLink(
                title = "Cursor Scan",
                summary = "Move a cursor around the screen to select items - best for precise control and navigation",
                navController = navController,
                route = NavigationRoute.CursorSettings.name
            )

            NavRouteLink(
                title = "Item Scan",
                summary = "Scan through interactive elements one by one or in groups - best for structured content",
                navController = navController,
                route = NavigationRoute.ItemScanSettings.name
            )

            NavRouteLink(
                title = "Radar Scan",
                summary = "A rotating line that helps select items in a circular pattern - best for quick access to screen areas",
                navController = navController,
                route = NavigationRoute.RadarSettings.name
            )
        }

        ScanModeSelectionSection()

        NavRouteLink(
            title = "Other Scan Settings",
            summary = "Configure other scan settings",
            navController = navController,
            route = NavigationRoute.OtherScanSettings.name
        )

        Section(title = "Scan Appearance") {
            NavRouteLink(
                title = "Scan Color",
                summary = "Configure the scan highlight color",
                navController = navController,
                route = NavigationRoute.ScanColor.name
            )
        }
    }
}

@Composable
fun SelectionSettingsTab(settingsScreenModel: SettingsScreenModel) {
    ScrollableView {
        SelectionSection(settingsScreenModel)
    }
}

@Composable
private fun InputSection(navController: NavController) {
    Section(title = "Input") {
        NavRouteLink(
            title = "Switches",
            summary = "Configure your switches",
            navController = navController,
            route = NavigationRoute.Switches.name
        )
        NavRouteLink(
            title = "Switch Stability",
            summary = "Configure switch stability settings",
            navController = navController,
            route = NavigationRoute.SwitchStability.name
        )
    }
}

@Composable
private fun MenuSection(screenModel: SettingsScreenModel, navController: NavController) {
    Section(title = "Menu") {
        NavRouteLink(
            title = "Customize Menu Items",
            summary = "Show or hide menu items",
            navController = navController,
            route = NavigationRoute.MenuItemCustomization.name
        )
        NavRouteLink(
            title = "Menu Size",
            summary = "Change the size of the menu",
            navController = navController,
            route = NavigationRoute.MenuSize.name
        )
        PreferenceSwitch(
            title = "Menu Transparency",
            summary = "Enable transparency for the menu so that you can see content behind it",
            checked = screenModel.menuTransparency.value == true,
            onCheckedChange = {
                screenModel.setMenuTransparency(it)
            }
        )
    }
}

@Composable
private fun ActionsSection(navController: NavController) {
    Section(title = "Actions") {
        NavRouteLink(
            title = "My Actions",
            summary = "Customize your own actions",
            navController = navController,
            route = NavigationRoute.MyActions.name
        )
    }
}

@Composable
private fun SelectionSection(screenModel: SettingsScreenModel) {
    val autoSelect = screenModel.autoSelect.observeAsState()
    Section(title = "Selection") {
        PreferenceSwitch(
            title = "Auto select",
            summary = "Automatically select the item after a delay. If you press the switch again during the delay, the menu will be opened.",
            checked = screenModel.autoSelect.value == true,
            onCheckedChange = {
                screenModel.setAutoSelect(it)
            }
        )
        if (autoSelect.value == true) {
            PreferenceTimeStepper(
                value = screenModel.autoSelectDelay.value ?: 0,
                title = "Auto select delay",
                summary = "The delay before the item is selected. If you press the switch again during the delay, the menu will be opened.",
                min = 100,
                max = 100000
            ) {
                screenModel.setAutoSelectDelay(it)
            }
        }
        PreferenceSwitch(
            title = "Directly select keyboard keys",
            summary = "If enabled, the switch will directly select keyboard keys instead of giving the user the option to open the menu.",
            checked = screenModel.directlySelectKeyboardKeys.value == true,
            onCheckedChange = {
                screenModel.setDirectlySelectKeyboardKeys(it)
            }
        )
        PreferenceSwitch(
            title = "Assisted selection",
            summary = "Assist the user in selecting items by selecting the closest available item to where they select.",
            checked = screenModel.assistedSelection.value == true,
            onCheckedChange = {
                screenModel.setAssistedSelection(it)
            }
        )
    }
}

@Composable
fun AboutSection() {
    val context = LocalContext.current
    val version = context.packageManager.getPackageInfo(context.packageName, 0).versionName

    val websiteUrl = "https://switchifyapp.com"
    val privacyPolicyUrl = "https://www.switchifyapp.com/privacy"

    ScrollableView {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.displayLarge,
                textAlign = TextAlign.Center
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Version $version",
                style = MaterialTheme.typography.bodyMedium
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.app_description),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
        FullWidthButton(text = "Website", onClick = {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(websiteUrl)))
        })
        Spacer(modifier = Modifier.height(16.dp))
        FullWidthButton(
            text = "Privacy Policy",
            onClick = {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(privacyPolicyUrl)))
            }
        )
    }
}
