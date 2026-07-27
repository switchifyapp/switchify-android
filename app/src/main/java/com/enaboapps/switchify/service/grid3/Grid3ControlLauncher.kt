package com.enaboapps.switchify.service.grid3

import com.enaboapps.switchify.R
import com.enaboapps.switchify.screens.grid3.PcSwitchControlActivity
import com.enaboapps.switchify.service.core.SwitchifyAccessibilityService
import com.enaboapps.switchify.service.menu.menus.main.PcControlLauncher
import kotlinx.coroutines.CoroutineScope

internal class Grid3ControlLauncher(
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

internal fun grid3LaunchErrorMessage(result: Grid3StartResult): Int? {
    return when (result) {
        Grid3StartResult.Started -> null
        Grid3StartResult.NoExternalSwitches -> R.string.grid3_no_external_switches
        Grid3StartResult.UnsupportedPc -> R.string.grid3_pc_unsupported
        Grid3StartResult.ProfileChanged -> R.string.pc_switch_profile_changed
        is Grid3StartResult.Failed -> R.string.pc_switch_start_failed
    }
}
