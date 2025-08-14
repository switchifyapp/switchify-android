package com.enaboapps.switchify.screens.settings.techniques

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.enaboapps.switchify.R
import com.enaboapps.switchify.backend.preferences.PreferenceManager
import com.enaboapps.switchify.components.Picker
import com.enaboapps.switchify.components.PreferenceSwitch
import com.enaboapps.switchify.components.Section
import com.enaboapps.switchify.screens.settings.shared.ItemScanSpeedStepper
import com.enaboapps.switchify.service.scanning.ScanHighlightStyle
import com.enaboapps.switchify.utils.Resources

@Composable
fun ItemScanSettingsView() {
    val preferenceManager = PreferenceManager(LocalContext.current)
    var currentScanCycles = remember {
        mutableStateOf(
            preferenceManager.getStringValue(
                PreferenceManager.Keys.PREFERENCE_KEY_SCAN_CYCLES,
                "3"
            )
        )
    }
    val scanHighlightStyle = ScanHighlightStyle(LocalContext.current)
    var currentScanHighlightStyle = remember {
        mutableStateOf(
            scanHighlightStyle.getType()
        )
    }

    Section(titleResId = R.string.section_title_item_scan_timing) {
        ItemScanSpeedStepper()
    }

    Section(titleResId = R.string.section_title_item_scan_highlight_style) {
        Picker(
            titleResId = R.string.preference_title_scan_highlight_type,
            selectedItem = currentScanHighlightStyle.value,
            items = ScanHighlightStyle.ALL,
            onItemSelected = { item ->
                currentScanHighlightStyle.value = item
                scanHighlightStyle.setType(item)
            },
            itemToString = { scanHighlightStyle.getName(it) },
            itemDescription = { scanHighlightStyle.getDescription(it) }
        )
    }

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
}