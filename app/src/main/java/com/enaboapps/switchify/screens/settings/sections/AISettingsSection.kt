package com.enaboapps.switchify.screens.settings.sections

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import com.enaboapps.switchify.R
import com.enaboapps.switchify.components.PreferenceSwitch
import com.enaboapps.switchify.components.Section
import com.enaboapps.switchify.screens.settings.models.AISettingsModel

@Composable
fun AISection(screenModel: AISettingsModel) {
    val aiSuggestionsEnabled by screenModel.aiSuggestionsEnabled.observeAsState()
    Section(titleResId = R.string.settings_section_ai) {
        PreferenceSwitch(
            titleResId = R.string.settings_title_ai_suggestions,
            summaryResId = R.string.settings_summary_ai_suggestions,
            isRestrictedToPro = true,
            checked = aiSuggestionsEnabled == true,
            onCheckedChange = {
                screenModel.setAiSuggestionsEnabled(it)
                // Disable visual analysis when smart suggestions is turned off
                if (!it) {
                    screenModel.setAiVisualAnalysisEnabled(false)
                }
            }
        )
        // Only show Visual Analysis setting when Smart Suggestions is enabled
        if (aiSuggestionsEnabled == true) {
            PreferenceSwitch(
                titleResId = R.string.settings_title_ai_visual_analysis,
                summaryResId = R.string.settings_summary_ai_visual_analysis,
                isRestrictedToPro = true,
                checked = screenModel.aiVisualAnalysisEnabled.value == true,
                onCheckedChange = {
                    screenModel.setAiVisualAnalysisEnabled(it)
                }
            )
        }
    }
}