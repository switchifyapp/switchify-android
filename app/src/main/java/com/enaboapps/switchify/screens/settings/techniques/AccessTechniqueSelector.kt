package com.enaboapps.switchify.screens.settings.techniques

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.enaboapps.switchify.R
import com.enaboapps.switchify.backend.preferences.PreferenceManager
import com.enaboapps.switchify.components.Picker
import com.enaboapps.switchify.service.techniques.AccessTechnique
import com.enaboapps.switchify.service.scanning.ScanSettings
import com.enaboapps.switchify.service.scanning.ScanMode

@Composable
fun AccessTechniqueSelector() {
    val context = LocalContext.current
    val preferenceManager = PreferenceManager(context)
    val scanSettings = remember { ScanSettings(context) }
    
    val allTechniques = listOf(
        AccessTechnique.Technique.ITEM_SCAN,
        AccessTechnique.Technique.POINT_SCAN,
        AccessTechnique.Technique.RADAR,
        AccessTechnique.Technique.DIRECT_CONTROL
    )
    
    val isDirectionalMode = scanSettings.isDirectionalScanMode()
    
    // Filter techniques based on scan mode
    val availableTechniques = if (isDirectionalMode) {
        allTechniques
    } else {
        allTechniques.filter { it != AccessTechnique.Technique.DIRECT_CONTROL }
    }
    
    var currentMethod by remember {
        mutableStateOf(
            preferenceManager.getStringValue(PreferenceManager.Keys.PREFERENCE_KEY_ACCESS_TECHNIQUE)
        )
    }

    val setScanMethod = { method: String ->
        // If selecting Direct Control and not in directional mode, switch to directional
        if (method == AccessTechnique.Technique.DIRECT_CONTROL && !isDirectionalMode) {
            preferenceManager.setStringValue(
                PreferenceManager.Keys.PREFERENCE_KEY_SCAN_MODE, 
                ScanMode.Modes.MODE_DIRECTIONAL
            )
        }
        
        preferenceManager.setStringValue(
            PreferenceManager.Keys.PREFERENCE_KEY_ACCESS_TECHNIQUE,
            method
        )
        currentMethod = method
    }

    Picker(
        titleResId = R.string.picker_title_select_access_technique,
        selectedItem = currentMethod,
        items = availableTechniques,
        onItemSelected = setScanMethod,
        itemToString = { AccessTechnique.getName(it) },
        itemDescription = { AccessTechnique.getDescription(it) }
    )
    
    // Helper text when Direct Control is not available
    if (!isDirectionalMode) {
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.direct_control_requires_directional_mode),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
    }
}
