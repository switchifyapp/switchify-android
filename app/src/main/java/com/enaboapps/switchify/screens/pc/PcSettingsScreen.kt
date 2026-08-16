package com.enaboapps.switchify.screens.pc

import android.content.Context
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.enaboapps.switchify.R
import com.enaboapps.switchify.backend.preferences.PreferenceManager
import com.enaboapps.switchify.components.BaseView
import com.enaboapps.switchify.components.PreferenceValueSelector
import com.enaboapps.switchify.components.Section
import com.enaboapps.switchify.service.pcswitchforwarding.PcSwitchForwardingController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

@Composable
fun PcSettingsScreen(navController: NavController) {
    val context = LocalContext.current
    val settings: PcSettingsViewModel = viewModel { PcSettingsViewModel(context) }
    val holdToStopDurationMs by settings.holdToStopDurationMs.collectAsState()
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
        Section(titleResId = R.string.pc_settings_switch_forwarding_section) {
            PreferenceValueSelector(
                value = holdToStopDurationMs,
                titleResId = R.string.pc_settings_switch_forwarding_hold_to_stop_title,
                summaryResId = R.string.pc_settings_switch_forwarding_hold_to_stop_summary,
                values = intArrayOf(3_000, 5_000, 7_000, 10_000),
                buttonLabelFormatter = { value -> "${value / 1_000}s" },
                displayFormatter = { value -> "${value / 1_000} seconds" },
                onValueChanged = settings::setHoldToStopDuration
            )
        }
    }
}

internal class PcSettingsViewModel(context: Context) : ViewModel() {
    private val preferenceManager = PreferenceManager(context)
    private val _holdToStopDurationMs = MutableStateFlow(
        preferenceManager.getLongValue(
            PreferenceManager.PREFERENCE_KEY_PC_SWITCH_FORWARDING_HOLD_TO_STOP_DURATION,
            PcSwitchForwardingController.DEFAULT_HOLD_TO_STOP_MS
        ).toInt()
    )
    val holdToStopDurationMs: StateFlow<Int> = _holdToStopDurationMs

    fun setHoldToStopDuration(value: Int) {
        preferenceManager.setLongValue(
            PreferenceManager.PREFERENCE_KEY_PC_SWITCH_FORWARDING_HOLD_TO_STOP_DURATION,
            value.toLong()
        )
        _holdToStopDurationMs.value = value
    }
}
