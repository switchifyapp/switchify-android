package com.enaboapps.switchify.service.menu.menus.system

import android.content.Intent
import com.enaboapps.switchify.service.actions.AudioActionManager
import com.enaboapps.switchify.service.actions.GlobalActionManager
import com.enaboapps.switchify.service.core.SwitchifyAccessibilityService
import com.enaboapps.switchify.service.menu.MenuItem
import com.enaboapps.switchify.service.menu.MenuManager
import com.enaboapps.switchify.service.menu.structure.MenuItemRegistry
import com.enaboapps.switchify.service.menu.structure.MenuStructure
import com.enaboapps.switchify.service.screenshot.ScreenshotManager

class SystemMenuStructure(private val accessibilityService: SwitchifyAccessibilityService?) {

    private val openVolumeControlMenu: MenuItem? = MenuItemRegistry.getDefinitionsForMenu("device_menu")
        .find { it.id == "volume_control" }?.let { def ->
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
        val definitions = MenuItemRegistry.getDeviceMenuDefinitions()
        return MenuStructure(
            id = "device_menu",
            items = listOfNotNull(
                definitions.find { it.id == "recent_apps" }?.let { def ->
                    MenuItem(
                        definition = def,
                        action = { GlobalActionManager.openRecents() }
                    )
                },
                definitions.find { it.id == "notifications" }?.let { def ->
                    MenuItem(
                        definition = def,
                        action = { GlobalActionManager.openNotifications() }
                    )
                },
                definitions.find { it.id == "open_assistant" }?.let { def ->
                    MenuItem(
                        definition = def,
                        action = {
                            val intent = Intent(Intent.ACTION_VOICE_COMMAND)
                            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            accessibilityService?.startActivity(intent)
                        }
                    )
                },
                definitions.find { it.id == "quick_settings" }?.let { def ->
                    MenuItem(
                        definition = def,
                        action = { GlobalActionManager.openQuickSettings() }
                    )
                },
                definitions.find { it.id == "lock_screen" }?.let { def ->
                    MenuItem(
                        definition = def,
                        action = { GlobalActionManager.lockScreen() }
                    )
                },
                definitions.find { it.id == "power_dialog" }?.let { def ->
                    MenuItem(
                        definition = def,
                        action = { GlobalActionManager.openPowerDialog() }
                    )
                },
                definitions.find { it.id == "take_screenshot" }?.let { def ->
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
            context = accessibilityService
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
        val definitions = MenuItemRegistry.getVolumeControlMenuDefinitions()
        return MenuStructure(
            id = "volume_control_menu",
            items = listOfNotNull(
                definitions.find { it.id == "volume_up" }?.let { def ->
                    MenuItem(
                        definition = def,
                        closeOnSelect = false,
                        action = { AudioActionManager.volumeUp() }
                    )
                },
                definitions.find { it.id == "volume_down" }?.let { def ->
                    MenuItem(
                        definition = def,
                        closeOnSelect = false,
                        action = { AudioActionManager.volumeDown() }
                    )
                },
                definitions.find { it.id == "full_volume" }?.let { def ->
                    MenuItem(
                        definition = def,
                        closeOnSelect = false,
                        action = { AudioActionManager.setFullVolume() }
                    )
                },
                definitions.find { it.id == "mute" }?.let { def ->
                    MenuItem(
                        definition = def,
                        closeOnSelect = false,
                        action = { AudioActionManager.mute() }
                    )
                },
                definitions.find { it.id == "half_volume" }?.let { def ->
                    MenuItem(
                        definition = def,
                        closeOnSelect = false,
                        action = { AudioActionManager.setHalfVolume() }
                    )
                }
            ),
            context = accessibilityService
        )
    }
} 