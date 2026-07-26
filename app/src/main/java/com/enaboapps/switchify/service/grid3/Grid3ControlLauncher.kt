package com.enaboapps.switchify.service.grid3

import com.enaboapps.switchify.R
import com.enaboapps.switchify.screens.grid3.Grid3ControlActivity
import com.enaboapps.switchify.service.core.ServiceCore
import com.enaboapps.switchify.service.core.SwitchifyAccessibilityService
import com.enaboapps.switchify.service.menu.menus.main.PcControlLauncher
import com.enaboapps.switchify.service.window.MessageSeverity
import com.enaboapps.switchify.service.window.ServiceMessageHUD
import kotlinx.coroutines.CoroutineScope

internal class Grid3ControlLauncher(
    accessibilityService: SwitchifyAccessibilityService,
    coroutineScope: CoroutineScope
) {
    private val launcher = PcControlLauncher(
        accessibilityService = accessibilityService,
        coroutineScope = coroutineScope,
        intentFactory = Grid3ControlActivity::createIntent,
        beforeLaunch = {
            val errorMessage = grid3LaunchErrorMessage(
                ServiceCore.getGrid3SwitchForwarder()?.start()
                    ?: Grid3StartResult.UnsupportedPc
            )
            if (errorMessage != null) {
                ServiceMessageHUD.instance.showMessage(
                    errorMessage,
                    ServiceMessageHUD.MessageType.DISAPPEARING,
                    ServiceMessageHUD.Time.LONG,
                    MessageSeverity.Warning
                )
            }
            errorMessage == null
        }
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
    }
}
