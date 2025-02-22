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
import com.enaboapps.switchify.service.scanning.ScanMode

@Composable
fun ScanModeSelectionSection(
    onChange: ((String) -> Unit)? = null
) {
    val preferenceManager = PreferenceManager(LocalContext.current)
    var currentMode by remember {
        mutableStateOf(
            ScanMode.fromId(
                preferenceManager.getStringValue(PreferenceManager.Keys.PREFERENCE_KEY_SCAN_MODE)
            )
        )
    }

    val setScanMode = { mode: ScanMode ->
        preferenceManager.setStringValue(PreferenceManager.Keys.PREFERENCE_KEY_SCAN_MODE, mode.id)
        currentMode = mode
    }

    Section(titleResId = R.string.section_title_scanning_mode) {
        Picker(
            titleResId = R.string.picker_title_select_scan_mode,
            selectedItem = currentMode,
            items = ScanMode.modes.toList(),
            onItemSelected = { mode ->
                setScanMode(mode)
                onChange?.invoke(mode.id)
            },
            itemToString = { it.getModeName() },
            itemDescription = { it.getModeDescription() }
        )
    }
}