package com.enaboapps.switchify.screens.settings.techniques

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.enaboapps.switchify.R
import com.enaboapps.switchify.components.Picker
import com.enaboapps.switchify.screens.settings.models.AccessTechniqueSettingsModel
import com.enaboapps.switchify.service.techniques.AccessTechnique

@Composable
fun AccessTechniqueSelector() {
    val context = LocalContext.current
    val viewModel: AccessTechniqueSettingsModel = viewModel {
        AccessTechniqueSettingsModel(context)
    }
    val uiState = viewModel.uiState.collectAsState()

    Picker(
        titleResId = R.string.picker_title_select_access_technique,
        selectedItem = uiState.value.currentTechnique,
        items = uiState.value.availableTechniques,
        onItemSelected = { method -> viewModel.selectTechnique(method) },
        itemToString = { AccessTechnique.getName(it) },
        itemDescription = { AccessTechnique.getDescription(it) }
    )

}
