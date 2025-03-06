package com.enaboapps.switchify.screens.settings.scanning

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.enaboapps.switchify.R
import com.enaboapps.switchify.backend.preferences.PreferenceManager
import com.enaboapps.switchify.components.Picker
import com.enaboapps.switchify.components.Section
import com.enaboapps.switchify.service.techniques.AccessTechnique

@Composable
fun AccessTechniqueSelector() {
    val scanTechniques = listOf(
        AccessTechnique.Technique.CURSOR,
        AccessTechnique.Technique.RADAR,
        AccessTechnique.Technique.ITEM_SCAN
    )
    val preferenceManager = PreferenceManager(LocalContext.current)
    var currentMethod by remember {
        mutableStateOf(
            preferenceManager.getStringValue(PreferenceManager.Keys.PREFERENCE_KEY_ACCESS_TECHNIQUE)
        )
    }

    val setScanMethod = { method: String ->
        preferenceManager.setStringValue(
            PreferenceManager.Keys.PREFERENCE_KEY_ACCESS_TECHNIQUE,
            method
        )
        currentMethod = method
    }

    Section(titleResId = R.string.section_title_access_technique) {
        Picker(
            titleResId = R.string.picker_title_select_access_technique,
            selectedItem = currentMethod,
            items = scanTechniques,
            onItemSelected = setScanMethod,
            itemToString = { AccessTechnique.getName(it) },
            itemDescription = { AccessTechnique.getDescription(it) }
        )
    }
}