package com.enaboapps.switchify.service.pcswitchcontrol

import com.enaboapps.switchify.R
import com.enaboapps.switchify.screens.pcswitchcontrol.PcSwitchControlActivity
import com.enaboapps.switchify.service.core.SwitchifyAccessibilityService
import com.enaboapps.switchify.service.menu.menus.main.PcControlLauncher
import kotlinx.coroutines.CoroutineScope

internal class PcSwitchControlLauncher(
    accessibilityService: SwitchifyAccessibilityService,
    coroutineScope: CoroutineScope
) {
    private val launcher = PcControlLauncher(
        accessibilityService = accessibilityService,
        coroutineScope = coroutineScope,
        intentFactory = PcSwitchControlActivity::createIntent
    )

    fun open() {
        launcher.open()
    }
}

internal fun pcSwitchControlLaunchErrorMessage(result: PcSwitchControlStartResult): Int? {
    return when (result) {
        PcSwitchControlStartResult.Started -> null
        PcSwitchControlStartResult.NoExternalSwitches -> R.string.pc_switch_control_no_external_switches
        PcSwitchControlStartResult.UnsupportedPc -> R.string.pc_switch_control_pc_unsupported
        PcSwitchControlStartResult.ProfileChanged -> R.string.pc_switch_profile_changed
        is PcSwitchControlStartResult.Failed -> R.string.pc_switch_start_failed
    }
}
