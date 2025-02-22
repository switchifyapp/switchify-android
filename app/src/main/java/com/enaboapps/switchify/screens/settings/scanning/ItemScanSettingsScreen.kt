package com.enaboapps.switchify.screens.settings.scanning

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController
import com.enaboapps.switchify.R
import com.enaboapps.switchify.backend.preferences.PreferenceManager
import com.enaboapps.switchify.components.BaseView
import com.enaboapps.switchify.components.Picker
import com.enaboapps.switchify.components.PreferenceSwitch
import com.enaboapps.switchify.components.PreferenceTimeStepper
import com.enaboapps.switchify.components.Section
import com.enaboapps.switchify.utils.Resources

@Composable
fun ItemScanSettingsScreen(navController: NavController) {
    val preferenceManager = PreferenceManager(LocalContext.current)
    var currentScanCycles = remember {
        mutableStateOf(
            preferenceManager.getStringValue(
                PreferenceManager.Keys.PREFERENCE_KEY_SCAN_CYCLES,
                "3"
            )
        )
    }

    BaseView(
        titleResId = R.string.screen_title_item_scan_settings,
        navController = navController
    ) {
        Section(titleResId = R.string.section_title_scan_pattern) {
            PreferenceSwitch(
                titleResId = R.string.preference_title_row_column_scan,
                summaryResId = R.string.preference_summary_row_column_scan,
                checked = preferenceManager.getBooleanValue(
                    PreferenceManager.PREFERENCE_KEY_ROW_COLUMN_SCAN,
                    false
                ),
                onCheckedChange = {
                    preferenceManager.setBooleanValue(
                        PreferenceManager.PREFERENCE_KEY_ROW_COLUMN_SCAN,
                        it
                    )
                }
            )

            PreferenceSwitch(
                titleResId = R.string.preference_title_group_scan,
                summaryResId = R.string.preference_summary_group_scan,
                checked = preferenceManager.getBooleanValue(
                    PreferenceManager.PREFERENCE_KEY_GROUP_SCAN,
                    false
                ),
                onCheckedChange = {
                    preferenceManager.setBooleanValue(
                        PreferenceManager.PREFERENCE_KEY_GROUP_SCAN,
                        it
                    )
                }
            )

            PreferenceSwitch(
                titleResId = R.string.preference_title_item_scan_speech,
                summaryResId = R.string.preference_summary_item_scan_speech,
                isRestrictedToPro = true,
                checked = preferenceManager.getBooleanValue(
                    PreferenceManager.PREFERENCE_KEY_ITEM_SCAN_SPEECH,
                    false
                ),
                onCheckedChange = {
                    preferenceManager.setBooleanValue(
                        PreferenceManager.PREFERENCE_KEY_ITEM_SCAN_SPEECH,
                        it
                    )
                }
            )

            Picker(
                titleResId = R.string.preference_title_scan_cycles,
                selectedItem = currentScanCycles.value,
                items = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "10"),
                onItemSelected = { item ->
                    currentScanCycles.value = item
                    preferenceManager.setStringValue(
                        PreferenceManager.Keys.PREFERENCE_KEY_SCAN_CYCLES,
                        item
                    )
                },
                itemToString = { it.toString() },
                itemDescription = { Resources.getString(R.string.preference_summary_scan_cycles) }
            )
        }

        Section(titleResId = R.string.section_title_timing) {
            PreferenceTimeStepper(
                value = preferenceManager.getLongValue(
                    PreferenceManager.PREFERENCE_KEY_SCAN_RATE,
                    1000
                ),
                titleResId = R.string.preference_title_item_scan_rate,
                summaryResId = R.string.preference_summary_item_scan_rate,
                min = 200,
                max = 10000,
                step = 100,
                onValueChanged = { newValue ->
                    preferenceManager.setLongValue(
                        PreferenceManager.PREFERENCE_KEY_SCAN_RATE,
                        newValue
                    )
                }
            )
        }
    }
} 