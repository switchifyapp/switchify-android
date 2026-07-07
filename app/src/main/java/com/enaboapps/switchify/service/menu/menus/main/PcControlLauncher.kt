package com.enaboapps.switchify.service.menu.menus.main

import com.enaboapps.switchify.R
import com.enaboapps.switchify.pc.DiscoveredPc
import com.enaboapps.switchify.pc.PcErrorReason
import com.enaboapps.switchify.pc.PcServiceConnectResult
import com.enaboapps.switchify.pc.PcServiceConnectionController
import com.enaboapps.switchify.pc.PcTokenStore
import com.enaboapps.switchify.screens.pc.PcMouseControlActivity
import com.enaboapps.switchify.service.core.ServiceCore
import com.enaboapps.switchify.service.core.SwitchifyAccessibilityService
import com.enaboapps.switchify.service.menu.MenuManager
import com.enaboapps.switchify.service.window.MessageSeverity
import com.enaboapps.switchify.service.window.ServiceMessageHUD
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PcControlLauncher(
    private val accessibilityService: SwitchifyAccessibilityService,
    private val coroutineScope: CoroutineScope
) {
    private val pcTokenStore = PcTokenStore(accessibilityService.applicationContext)

    fun open() {
        MenuManager.getInstance().closeMenuHierarchy()
        val controller = ServiceCore.getPcServiceConnectionController()
        if (controller?.hasLiveControlSession() == true) {
            launchPcControlActivity()
            return
        }
        if (controller == null) {
            showMessage(R.string.pc_control_no_pc_found, MessageSeverity.Warning)
            return
        }
        showMessage(R.string.pc_control_connecting, MessageSeverity.Info)
        coroutineScope.launch {
            val discovered = controller.discoverPairedPcs()
            val defaultPreference = pcTokenStore.getDefaultPcPreference()
            val lastConnectedDesktopId = pcTokenStore.getLastConnectedDesktopId()
            withContext(Dispatchers.Main) {
                when (val selection = selectPcForMainMenu(discovered, defaultPreference, lastConnectedDesktopId)) {
                    PcMainMenuSelection.NoPcFound -> showMessage(R.string.pc_control_no_pc_found, MessageSeverity.Warning)
                    is PcMainMenuSelection.Connect -> connectToPcAndLaunch(controller, selection.pc)
                    is PcMainMenuSelection.ShowChooser -> MenuManager.getInstance().openChoosePcMenu(selection.pcs) { pc ->
                        connectToPcAndLaunch(controller, pc)
                    }
                }
            }
        }
    }

    private fun connectToPcAndLaunch(controller: PcServiceConnectionController, pc: DiscoveredPc) {
        showMessage(R.string.pc_control_connecting, MessageSeverity.Info)
        coroutineScope.launch {
            val result = controller.connectTo(pc) { approvalCode ->
                coroutineScope.launch(Dispatchers.Main) {
                    showMessage(
                        R.string.pc_control_pairing_code,
                        arrayOf(approvalCode.verificationCode),
                        MessageSeverity.Info
                    )
                }
            }
            withContext(Dispatchers.Main) {
                when (result) {
                    is PcServiceConnectResult.Connected -> launchPcControlActivity()
                    is PcServiceConnectResult.Failed -> {
                        val message = when (result.reason) {
                            PcErrorReason.NoPcFound -> R.string.pc_control_no_pc_found
                            PcErrorReason.PairingRejected -> R.string.request_rejected
                            PcErrorReason.PairingRequestExpired -> R.string.request_expired_try_again
                            else -> R.string.pc_control_could_not_connect
                        }
                        showMessage(message, MessageSeverity.Warning)
                    }
                }
            }
        }
    }

    private fun launchPcControlActivity() {
        MenuManager.getInstance().closeMenuHierarchy()
        accessibilityService.startActivity(PcMouseControlActivity.createIntent(accessibilityService))
    }

    private fun showMessage(messageResId: Int, severity: MessageSeverity) {
        ServiceMessageHUD.instance.showMessage(
            messageResId,
            ServiceMessageHUD.MessageType.DISAPPEARING,
            ServiceMessageHUD.Time.MEDIUM,
            severity
        )
    }

    private fun showMessage(messageResId: Int, messageArgs: Array<out Any>, severity: MessageSeverity) {
        ServiceMessageHUD.instance.showMessage(
            messageResId,
            messageArgs,
            ServiceMessageHUD.MessageType.DISAPPEARING,
            ServiceMessageHUD.Time.LONG,
            severity
        )
    }
}
