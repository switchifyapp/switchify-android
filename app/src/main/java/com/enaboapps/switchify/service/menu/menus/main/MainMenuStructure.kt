package com.enaboapps.switchify.service.menu.menus.main

import com.enaboapps.switchify.R
import com.enaboapps.switchify.backend.iap.IAPHandler
import com.enaboapps.switchify.backend.preferences.PreferenceManager
import com.enaboapps.switchify.pc.DiscoveredPc
import com.enaboapps.switchify.pc.PcErrorReason
import com.enaboapps.switchify.pc.PcServiceConnectResult
import com.enaboapps.switchify.pc.PcServiceConnectionController
import com.enaboapps.switchify.service.actions.GlobalActionManager
import com.enaboapps.switchify.service.core.ServiceCore
import com.enaboapps.switchify.service.core.SwitchifyAccessibilityService
import com.enaboapps.switchify.service.gestures.GesturePoint
import com.enaboapps.switchify.service.keyboard.KeyboardManager
import com.enaboapps.switchify.service.menu.MenuItem
import com.enaboapps.switchify.service.menu.MenuManager
import com.enaboapps.switchify.service.menu.database.MenuConfigurationRepository
import com.enaboapps.switchify.service.menu.menus.gestures.GestureMenuStructure
import com.enaboapps.switchify.service.menu.structure.MenuActionResolver
import com.enaboapps.switchify.service.menu.structure.MenuConstants
import com.enaboapps.switchify.service.menu.structure.MenuItemRegistry
import com.enaboapps.switchify.service.menu.structure.MenuStructure
import com.enaboapps.switchify.screens.pc.PcMouseControlActivity
import com.enaboapps.switchify.service.techniques.nodes.NodeExaminer
import com.enaboapps.switchify.service.utils.DeviceLockObserver
import com.enaboapps.switchify.service.window.MessageSeverity
import com.enaboapps.switchify.service.window.ServiceMessageHUD
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainMenuStructure(
    private val accessibilityService: SwitchifyAccessibilityService,
    private val coroutineScope: CoroutineScope
) {
    private val gestureMenuStructure = GestureMenuStructure(accessibilityService, coroutineScope)
    private val deviceLockObserver = DeviceLockObserver(accessibilityService)
    private val preferenceManager = PreferenceManager(accessibilityService)
    private val repository = MenuConfigurationRepository(accessibilityService)

    val deviceItem = MenuItem(
        id = "device",
        labelResource = R.string.menu_title_device,
        descriptionResource = R.string.menu_item_device_description,
        drawableId = R.drawable.ic_device,
        isLinkToMenu = true,
        action = { MenuManager.getInstance().openDeviceMenu() }
    )

    /**
     * Constructs the main menu structure based on current runtime state.
     *
     * The returned menu reflects current conditions such as keyboard visibility,
     * device lock state, access technique, camera permission, and gesture context.
     * Items include system navigation, scanning and technique switches, gesture and
     * media submenus, head-control toggle, favourite apps, edit actions, pause, and
     * any user-added items from other menus.
     *
     * @return A MenuStructure representing the main menu configured for the current state.
     */
    fun buildMainMenuObject() = MenuStructure(
        id = MenuConstants.MenuIds.MAIN_MENU,
        items = buildDefaultItems(),
        context = accessibilityService,
        coroutineScope = coroutineScope
    )

    /**
     * Builds the default menu items for the main menu.
     */
    private fun buildDefaultItems() = listOfNotNull(
            // System navigation items - back and home
            MenuItemRegistry.getMainMenuDefinition("sys_back")?.let { def ->
                MenuItem(
                    definition = def,
                    action = { GlobalActionManager.goBack() }
                )
            },
            MenuItemRegistry.getMainMenuDefinition("sys_home")?.let { def ->
                MenuItem(
                    definition = def,
                    action = { GlobalActionManager.goHome() }
                )
            },
            // Show "Scan Keyboard" menu item when keyboard is visible but user has escaped
            if (KeyboardManager.shouldShowScanKeyboardMenuItem()) {
                MenuItemRegistry.getMainMenuDefinition("scan_keyboard")?.let { def ->
                    MenuItem(
                        definition = def,
                        action = {
                            KeyboardManager.returnToKeyboard()
                            MenuManager.getInstance().closeMenuHierarchy()
                        }
                    )
                }
            } else null,
            gestureMenuStructure.tapMenuItem,
            MenuItemRegistry.getMainMenuDefinition("gestures")?.let { def ->
                MenuItem(
                    definition = def,
                    isLinkToMenu = true,
                    action = { MenuManager.getInstance().openGesturesMenu() }
                )
            },
            MenuItemRegistry.getMainMenuDefinition("scroll")?.let { def ->
                MenuItem(
                    definition = def,
                    isLinkToMenu = true,
                    action = { MenuManager.getInstance().openScrollMenu() }
                )
            },
            if (deviceLockObserver.isUserUnlocked() == true) {
                MenuItemRegistry.getMainMenuDefinition("favourite_apps")?.let { def ->
                    MenuItem(
                        definition = def,
                        isLinkToMenu = true,
                        action = { MenuManager.getInstance().openFavouriteAppsMenu() }
                    )
                }
            } else null,
            if (deviceLockObserver.isUserUnlocked() == true) {
                MenuItemRegistry.getMainMenuDefinition("gesture_patterns")?.let { def ->
                    MenuItem(
                        definition = def,
                        isLinkToMenu = true,
                        action = { MenuManager.getInstance().openGesturePatternsMenu() }
                    )
                }
            } else null,
            deviceItem,
            MenuItemRegistry.getMainMenuDefinition("settings")?.let { def ->
                MenuItem(
                    definition = def,
                    isLinkToMenu = true,
                    action = { MenuManager.getInstance().openSettingsMenu() }
                )
            },
            MenuItemRegistry.getMainMenuDefinition("media_control")?.let { def ->
                MenuItem(
                    definition = def,
                    isLinkToMenu = true,
                    action = { MenuManager.getInstance().openMediaControlMenu() }
                )
            },
            if (deviceLockObserver.isUserUnlocked() == true) {
                MenuItemRegistry.getMainMenuDefinition("control_pc")?.let { def ->
                    MenuItem(
                        definition = def,
                        isLinkToMenu = true,
                        action = { openPcControlActivity() }
                    )
                }
            } else null,
            if (NodeExaminer.canPerformEditActions(GesturePoint.getPoint())) {
                MenuItemRegistry.getMainMenuDefinition("edit")?.let { def ->
                    MenuItem(
                        definition = def,
                        isLinkToMenu = true,
                        action = { MenuManager.getInstance().openEditMenu() }
                    )
                }
            } else null,
            MenuItemRegistry.getMainMenuDefinition("ai")?.let { def ->
                MenuItem(
                    definition = def,
                    isLinkToMenu = true,
                    action = { MenuManager.getInstance().openAiMenu() }
                )
            },
            MenuItemRegistry.getMainMenuDefinition("pause")?.let { def ->
                MenuItem(
                    definition = def,
                    action = { ServiceCore.getPauseManager().startPause() }
                )
            }
    )

    private fun openPcControlActivity() {
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
            withContext(Dispatchers.Main) {
                when {
                    discovered.isEmpty() -> showMessage(R.string.pc_control_no_pc_found, MessageSeverity.Warning)
                    discovered.size == 1 -> connectToPcAndLaunch(controller, discovered.single())
                    else -> MenuManager.getInstance().openChoosePcMenu(discovered) { pc ->
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

    val menuManipulatorItems = listOfNotNull(
        MenuItem(
            id = MenuConstants.ItemIds.Navigation.CLOSE_MENU,
            drawableId = R.drawable.ic_close_menu,
            labelResource = R.string.menu_item_close_menu,
            descriptionResource = R.string.menu_item_close_menu_description,
            isMenuHierarchyManipulator = true,
            action = { MenuManager.getInstance().closeMenuHierarchy() }
        )
    )
}
