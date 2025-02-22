package com.enaboapps.switchify.screens

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.enaboapps.switchify.R
import com.enaboapps.switchify.components.BaseView
import com.enaboapps.switchify.components.FullWidthButton
import com.enaboapps.switchify.service.utils.ServiceUtils
import com.enaboapps.switchify.utils.Logger

@Composable
fun EnableAccessibilityServiceScreen(navController: NavController) {
    val context = LocalContext.current

    BaseView(
        titleResId = R.string.screen_title_accessibility_service,
        navController = navController
    ) {
        Text(
            text = stringResource(R.string.accessibility_service_description),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 20.dp)
        )
        val disclosure = context.resources.getString(R.string.accessibility_service_disclosure)
        Text(
            text = disclosure,
            modifier = Modifier.padding(bottom = 20.dp)
        )
        FullWidthButton(
            textResId = R.string.button_take_me_there,
            onClick = {
                ServiceUtils().openAccessibilitySettings(context)
                Logger.logEvent("Opened Accessibility Settings")
            }
        )
        FullWidthButton(
            textResId = R.string.button_enabled_it,
            onClick = {
                navController.popBackStack()
            }
        )
        FullWidthButton(
            textResId = R.string.button_not_right_now,
            onClick = {
                navController.popBackStack()
            }
        )
    }
}