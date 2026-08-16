package com.enaboapps.switchify.service.pcswitchforwarding

import com.enaboapps.switchify.R
import com.enaboapps.switchify.screens.pcswitchforwarding.PcSwitchForwardingActivity
import com.enaboapps.switchify.service.core.SwitchifyAccessibilityService
import com.enaboapps.switchify.service.menu.menus.main.PcControlLauncher
import kotlinx.coroutines.CoroutineScope

internal class PcSwitchForwardingLauncher(
    accessibilityService: SwitchifyAccessibilityService,
    coroutineScope: CoroutineScope
) {
    private val launcher = PcControlLauncher(
        accessibilityService = accessibilityService,
        coroutineScope = coroutineScope,
        intentFactory = PcSwitchForwardingActivity::createIntent
    )

    fun open() {
        launcher.open()
    }
}

internal fun pcSwitchForwardingLaunchErrorMessage(result: PcSwitchForwardingStartResult): Int? {
    return when (result) {
        PcSwitchForwardingStartResult.Started -> null
        PcSwitchForwardingStartResult.NoExternalSwitches -> R.string.pc_switch_forwarding_no_external_switches
        PcSwitchForwardingStartResult.UnsupportedPc -> R.string.pc_switch_forwarding_pc_unsupported
        PcSwitchForwardingStartResult.ProfileChanged -> R.string.pc_switch_profile_changed
        is PcSwitchForwardingStartResult.Failed -> R.string.pc_switch_start_failed
    }
}
