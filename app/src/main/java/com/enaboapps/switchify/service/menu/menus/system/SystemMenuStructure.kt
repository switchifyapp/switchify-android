package com.enaboapps.switchify.service.menu.menus.system

import android.content.Intent
import com.enaboapps.switchify.service.actions.AudioActionManager
import com.enaboapps.switchify.service.actions.GlobalActionManager
import com.enaboapps.switchify.service.core.SwitchifyAccessibilityService
import com.enaboapps.switchify.service.menu.MenuItem
import com.enaboapps.switchify.service.menu.MenuManager
import com.enaboapps.switchify.service.menu.structure.MenuConstants
import com.enaboapps.switchify.service.menu.structure.MenuItemRegistry
import com.enaboapps.switchify.service.menu.structure.MenuStructure
import com.enaboapps.switchify.service.screenshot.ScreenshotManager
import kotlinx.coroutines.CoroutineScope

class SystemMenuStructure(
    private val accessibilityService: SwitchifyAccessibilityService?,
    private val coroutineScope: CoroutineScope
) {

    private val openVolumeControlMenu: MenuItem? =
        MenuItemRegistry.getDefinition(MenuConstants.MenuIds.DEVICE_MENU, MenuConstants.ItemIds.Device.VOLUME_CONTROL)?.let { def ->
            MenuItem(
                definition = def,
                isLinkToMenu = true,
                action = { MenuManager.getInstance().openVolumeControlMenu() }
            )
        }

    /**
     * Builds the system "device" menu structure containing device-related actions (recent apps, notifications,
     * assistant, quick settings, lock, power dialog, screenshot, and volume control when available).
     *
     * Missing menu item definitions from the registry are omitted; the menu's context is set to the class'
     * accessibilityService.
     *
     * @return A MenuStructure with id "device_menu" whose items perform the corresponding system actions
     * (e.g., open recents, open notifications, start voice command, open quick settings, lock screen,
     * open power dialog, take screenshot) and may include the volume control submenu if defined.
     */
    fun buildDeviceMenuObject(): MenuStructure {
        return MenuStructure(
            id = MenuConstants.MenuIds.DEVICE_MENU,
            items = listOfNotNull(
                MenuItemRegistry.getDefinition(MenuConstants.MenuIds.DEVICE_MENU, MenuConstants.ItemIds.Device.RECENT_APPS)?.let { def ->
                    MenuItem(
                        definition = def,
                        action = { GlobalActionManager.openRecents() }
                    )
                },
                MenuItemRegistry.getDefinition(MenuConstants.MenuIds.DEVICE_MENU, MenuConstants.ItemIds.Device.NOTIFICATIONS)?.let { def ->
                    MenuItem(
                        definition = def,
                        action = { GlobalActionManager.openNotifications() }
                    )
                },
                MenuItemRegistry.getDefinition(MenuConstants.MenuIds.DEVICE_MENU, MenuConstants.ItemIds.Device.OPEN_ASSISTANT)?.let { def ->
                    MenuItem(
                        definition = def,
                        action = {
                            val intent = Intent(Intent.ACTION_VOICE_COMMAND)
                            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            accessibilityService?.startActivity(intent)
                        }
                    )
                },
                MenuItemRegistry.getDefinition(MenuConstants.MenuIds.DEVICE_MENU, MenuConstants.ItemIds.Device.QUICK_SETTINGS)?.let { def ->
                    MenuItem(
                        definition = def,
                        action = { GlobalActionManager.openQuickSettings() }
                    )
                },
                MenuItemRegistry.getDefinition(MenuConstants.MenuIds.DEVICE_MENU, MenuConstants.ItemIds.Device.LOCK_SCREEN)?.let { def ->
                    MenuItem(
                        definition = def,
                        action = { GlobalActionManager.lockScreen() }
                    )
                },
                MenuItemRegistry.getDefinition(MenuConstants.MenuIds.DEVICE_MENU, MenuConstants.ItemIds.Device.POWER_DIALOG)?.let { def ->
                    MenuItem(
                        definition = def,
                        action = { GlobalActionManager.openPowerDialog() }
                    )
                },
                MenuItemRegistry.getDefinition(MenuConstants.MenuIds.DEVICE_MENU, MenuConstants.ItemIds.Device.TAKE_SCREENSHOT)?.let { def ->
                    MenuItem(
                        definition = def,
                        action = {
                            accessibilityService?.let { service ->
                                ScreenshotManager.takeScreenshotAndSave(
                                    accessibilityService = service,
                                    context = service.applicationContext
                                )
                            }
                        }
                    )
                },
                openVolumeControlMenu
            ),
            context = accessibilityService,
            coroutineScope = coroutineScope
        )
    }

    /**
     * Builds the volume control submenu containing volume-related actions.
     *
     * The returned menu includes items for "volume_up", "volume_down", "full_volume", "mute", and "half_volume" when those definitions exist in the registry; missing definitions are omitted. Each item invokes the corresponding AudioActionManager operation and preserves the menu (does not close) on selection.
     *
     * @return A MenuStructure with id "volume_control_menu" whose context is the current accessibility service and whose items map to the available volume control definitions.
     */
    fun buildVolumeControlMenuObject(): MenuStructure {
        return MenuStructure(
            id = MenuConstants.MenuIds.VOLUME_CONTROL_MENU,
            items = listOfNotNull(
                MenuItemRegistry.getDefinition(MenuConstants.MenuIds.VOLUME_CONTROL_MENU, MenuConstants.ItemIds.Volume.VOLUME_UP)?.let { def ->
                    MenuItem(
                        definition = def,
                        closeOnSelect = false,
                        action = { AudioActionManager.volumeUp() }
                    )
                },
                MenuItemRegistry.getDefinition(MenuConstants.MenuIds.VOLUME_CONTROL_MENU, MenuConstants.ItemIds.Volume.VOLUME_DOWN)?.let { def ->
                    MenuItem(
                        definition = def,
                        closeOnSelect = false,
                        action = { AudioActionManager.volumeDown() }
                    )
                },
                MenuItemRegistry.getDefinition(MenuConstants.MenuIds.VOLUME_CONTROL_MENU, MenuConstants.ItemIds.Volume.FULL_VOLUME)?.let { def ->
                    MenuItem(
                        definition = def,
                        closeOnSelect = false,
                        action = { AudioActionManager.setFullVolume() }
                    )
                },
                MenuItemRegistry.getDefinition(MenuConstants.MenuIds.VOLUME_CONTROL_MENU, MenuConstants.ItemIds.Volume.MUTE)?.let { def ->
                    MenuItem(
                        definition = def,
                        closeOnSelect = false,
                        action = { AudioActionManager.mute() }
                    )
                },
                MenuItemRegistry.getDefinition(MenuConstants.MenuIds.VOLUME_CONTROL_MENU, MenuConstants.ItemIds.Volume.HALF_VOLUME)?.let { def ->
                    MenuItem(
                        definition = def,
                        closeOnSelect = false,
                        action = { AudioActionManager.setHalfVolume() }
                    )
                }
            ),
            context = accessibilityService,
            coroutineScope = coroutineScope
        )
    }
} 