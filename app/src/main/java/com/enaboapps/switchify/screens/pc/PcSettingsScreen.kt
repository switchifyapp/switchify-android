package com.enaboapps.switchify.screens.pc

import androidx.compose.runtime.Composable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
import androidx.navigation.NavController
import com.enaboapps.switchify.R
import com.enaboapps.switchify.components.BaseView
import com.enaboapps.switchify.components.Section

@Composable
fun PcSettingsScreen(navController: NavController) {
    BaseView(
        titleResId = R.string.pc_settings_title,
        navController = navController
    ) {
        Section(titleResId = R.string.pc_settings_mouse_section) {
            Text(
                text = stringResource(R.string.pc_settings_mouse_repeat_configured_on_pc),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
