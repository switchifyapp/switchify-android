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
import com.enaboapps.switchify.service.scanning.ScanMethod

@Composable
fun ScanMethodSelectionSection() {
    val methods = listOf(
        ScanMethod.MethodType.CURSOR,
        ScanMethod.MethodType.RADAR,
        ScanMethod.MethodType.ITEM_SCAN
    )
    val preferenceManager = PreferenceManager(LocalContext.current)
    var currentMethod by remember {
        mutableStateOf(
            preferenceManager.getStringValue(PreferenceManager.Keys.PREFERENCE_KEY_SCAN_METHOD)
        )
    }

    val setScanMethod = { method: String ->
        preferenceManager.setStringValue(PreferenceManager.Keys.PREFERENCE_KEY_SCAN_METHOD, method)
        currentMethod = method
    }

    Section(titleResId = R.string.section_title_scanning_method) {
        Picker(
            titleResId = R.string.picker_title_select_scan_method,
            selectedItem = currentMethod,
            items = methods,
            onItemSelected = setScanMethod,
            itemToString = { ScanMethod.getName(it) },
            itemDescription = { ScanMethod.getDescription(it) }
        )
    }
}