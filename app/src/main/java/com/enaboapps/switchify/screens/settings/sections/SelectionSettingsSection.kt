package com.enaboapps.switchify.screens.settings.sections

import androidx.compose.runtime.Composable
import androidx.compose.runtime.livedata.observeAsState
import com.enaboapps.switchify.R
import com.enaboapps.switchify.components.PreferenceSwitch
import com.enaboapps.switchify.components.PreferenceTimeStepper
import com.enaboapps.switchify.components.Section
import com.enaboapps.switchify.screens.settings.models.SelectionSettingsModel

@Composable
fun SelectionSection(screenModel: SelectionSettingsModel) {
    val autoSelect = screenModel.autoSelect.observeAsState()
    Section(titleResId = R.string.settings_section_selection) {
        PreferenceSwitch(
            titleResId = R.string.settings_title_auto_select,
            summaryResId = R.string.settings_summary_auto_select,
            checked = screenModel.autoSelect.value == true,
            onCheckedChange = {
                screenModel.setAutoSelect(it)
            }
        )
        if (autoSelect.value == true) {
            PreferenceTimeStepper(
                value = screenModel.autoSelectDelay.value ?: 0,
                titleResId = R.string.settings_title_auto_select_delay,
                summaryResId = R.string.settings_summary_auto_select_delay,
                min = 100,
                max = 100000
            ) {
                screenModel.setAutoSelectDelay(it)
            }
        }
        PreferenceSwitch(
            titleResId = R.string.settings_title_directly_select_keyboard,
            summaryResId = R.string.settings_summary_directly_select_keyboard,
            checked = screenModel.directlySelectKeyboardKeys.value == true,
            onCheckedChange = {
                screenModel.setDirectlySelectKeyboardKeys(it)
            }
        )
        PreferenceSwitch(
            titleResId = R.string.settings_title_assisted_selection,
            summaryResId = R.string.settings_summary_assisted_selection,
            checked = screenModel.assistedSelection.value == true,
            onCheckedChange = {
                screenModel.setAssistedSelection(it)
            }
        )
    }
}