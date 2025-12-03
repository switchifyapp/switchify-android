package com.enaboapps.switchify.service.menu.structure

import com.enaboapps.switchify.R

/**
 * Single source of truth for all menu item definitions.
 * This object maintains the metadata for all menu items across the entire application.
 * Menu structures and UI screens should use this registry to get item definitions.
 */
object MenuItemRegistry {

    fun getMainMenuDefinitions(): List<MenuItemDefinition> {
        return listOf(
            MenuItemDefinition("sys_back", labelResource = R.string.system_back),
            MenuItemDefinition("sys_home", labelResource = R.string.system_home),
            MenuItemDefinition("scan_keyboard", labelResource = R.string.menu_item_scan_keyboard),
            MenuItemDefinition("tap", labelResource = R.string.menu_title_tap),
            MenuItemDefinition("gestures", labelResource = R.string.menu_title_gestures),
            MenuItemDefinition("scroll", labelResource = R.string.menu_title_scroll),
            MenuItemDefinition("quick_apps", userProvidedText = "Quick Apps"),
            MenuItemDefinition("gesture_patterns", labelResource = R.string.gesture_patterns_title),
            MenuItemDefinition("device", labelResource = R.string.menu_title_device),
            MenuItemDefinition("media_control", labelResource = R.string.menu_title_media_control),
            MenuItemDefinition("edit", labelResource = R.string.menu_title_edit),
            MenuItemDefinition("item_scan", userProvidedText = "Item Scan"),
            MenuItemDefinition("radar_scan", userProvidedText = "Radar Scan"),
            MenuItemDefinition("point_scan", userProvidedText = "Point Scan"),
            MenuItemDefinition("head_control", labelResource = R.string.menu_item_enable_head_control),
            MenuItemDefinition("pause", labelResource = R.string.menu_item_pause)
        )
    }

    fun getDeviceMenuDefinitions(): List<MenuItemDefinition> {
        return listOf(
            MenuItemDefinition("recent_apps", labelResource = R.string.system_recents),
            MenuItemDefinition("notifications", labelResource = R.string.system_notifications),
            MenuItemDefinition("open_assistant", labelResource = R.string.system_assistant),
            MenuItemDefinition("quick_settings", labelResource = R.string.system_quick_settings),
            MenuItemDefinition("lock_screen", labelResource = R.string.system_lock_screen),
            MenuItemDefinition("power_dialog", labelResource = R.string.system_power_dialog),
            MenuItemDefinition("take_screenshot", labelResource = R.string.system_screenshot),
            MenuItemDefinition("volume_control", labelResource = R.string.action_volume_control)
        )
    }

    fun getVolumeControlMenuDefinitions(): List<MenuItemDefinition> {
        return listOf(
            MenuItemDefinition("volume_up", labelResource = R.string.menu_item_volume_up),
            MenuItemDefinition("volume_down", labelResource = R.string.menu_item_volume_down),
            MenuItemDefinition("full_volume", labelResource = R.string.menu_item_full_volume),
            MenuItemDefinition("mute", labelResource = R.string.menu_item_mute),
            MenuItemDefinition("half_volume", labelResource = R.string.menu_item_half_volume)
        )
    }

    fun getGesturesMenuDefinitions(): List<MenuItemDefinition> {
        return listOf(
            MenuItemDefinition("tap", labelResource = R.string.menu_title_tap),
            MenuItemDefinition("swipe", labelResource = R.string.menu_title_swipe),
            MenuItemDefinition("pinch", labelResource = R.string.menu_title_pinch)
        )
    }

    fun getTapGesturesMenuDefinitions(): List<MenuItemDefinition> {
        return listOf(
            MenuItemDefinition("tap_here", userProvidedText = "Tap Here"),
            MenuItemDefinition("double_tap", userProvidedText = "Double Tap"),
            MenuItemDefinition("long_press", userProvidedText = "Long Press")
        )
    }

    fun getSwipeGesturesMenuDefinitions(): List<MenuItemDefinition> {
        return listOf(
            MenuItemDefinition("swipe_up", labelResource = R.string.menu_item_swipe_up),
            MenuItemDefinition("swipe_down", labelResource = R.string.menu_item_swipe_down),
            MenuItemDefinition("swipe_left", labelResource = R.string.menu_item_swipe_left),
            MenuItemDefinition("swipe_right", labelResource = R.string.menu_item_swipe_right)
        )
    }

    fun getPinchGesturesMenuDefinitions(): List<MenuItemDefinition> {
        return listOf(
            MenuItemDefinition("pinch_in", labelResource = R.string.menu_item_pinch_in),
            MenuItemDefinition("pinch_out", labelResource = R.string.menu_item_pinch_out)
        )
    }

    fun getScrollMenuDefinitions(): List<MenuItemDefinition> {
        return listOf(
            MenuItemDefinition("scroll_up", labelResource = R.string.menu_item_scroll_up),
            MenuItemDefinition("scroll_down", labelResource = R.string.menu_item_scroll_down),
            MenuItemDefinition("scroll_left", labelResource = R.string.menu_item_scroll_left),
            MenuItemDefinition("scroll_right", labelResource = R.string.menu_item_scroll_right)
        )
    }

    fun getMediaControlMenuDefinitions(): List<MenuItemDefinition> {
        return listOf(
            MenuItemDefinition("media_play_pause", userProvidedText = "Play/Pause"),
            MenuItemDefinition("volume_control", labelResource = R.string.action_volume_control)
        )
    }

    fun getEditMenuDefinitions(): List<MenuItemDefinition> {
        return listOf(
            MenuItemDefinition("cut", labelResource = R.string.menu_item_cut),
            MenuItemDefinition("copy", labelResource = R.string.menu_item_copy),
            MenuItemDefinition("paste", labelResource = R.string.menu_item_paste)
        )
    }

    /**
     * Get definitions for a specific menu by ID.
     * This is a convenience method for accessing definitions by menu ID string.
     */
    fun getDefinitionsForMenu(menuId: String): List<MenuItemDefinition> {
        return when (menuId) {
            "main_menu" -> getMainMenuDefinitions()
            "device_menu" -> getDeviceMenuDefinitions()
            "volume_control_menu" -> getVolumeControlMenuDefinitions()
            "gestures_menu" -> getGesturesMenuDefinitions()
            "tap_gestures_menu" -> getTapGesturesMenuDefinitions()
            "swipe_gestures_menu" -> getSwipeGesturesMenuDefinitions()
            "pinch_gestures_menu" -> getPinchGesturesMenuDefinitions()
            "scroll_menu" -> getScrollMenuDefinitions()
            "media_control_menu" -> getMediaControlMenuDefinitions()
            "edit_menu" -> getEditMenuDefinitions()
            else -> emptyList()
        }
    }
}
