package com.enaboapps.switchify.service.menu.structure

import com.enaboapps.switchify.R

/**
 * Constants for menu and menu item identifiers.
 * Provides type-safe access to menu structure identifiers without using raw strings.
 */
object MenuConstants {
    /**
     * Menus the user can customize from the Menu Customization screen, in the
     * order shown to them. Single source of truth for both the picker screen
     * and the palette dialog's filter dropdown.
     */
    val customizableMenus: List<Pair<String, Int>> = listOf(
        "main_menu" to R.string.menu_title_main,
        "device_menu" to R.string.menu_title_device,
        "volume_control_menu" to R.string.action_volume_control,
        "gestures_menu" to R.string.menu_title_gestures,
        "tap_gestures_menu" to R.string.menu_title_tap,
        "tap_and_hold_menu" to R.string.menu_title_tap_and_hold,
        "swipe_gestures_menu" to R.string.menu_title_swipe,
        "pinch_gestures_menu" to R.string.menu_title_pinch,
        "scroll_menu" to R.string.menu_title_scroll,
        "media_control_menu" to R.string.menu_title_media,
        "edit_menu" to R.string.menu_title_edit
    )

    /**
     * Menu identifiers
     */
    object MenuIds {
        const val MAIN_MENU = "main_menu"
        const val DEVICE_MENU = "device_menu"
        const val VOLUME_CONTROL_MENU = "volume_control_menu"
        const val GESTURES_MENU = "gestures_menu"
        const val TAP_GESTURES_MENU = "tap_gestures_menu"
        const val TAP_AND_HOLD_MENU = "tap_and_hold_menu"
        const val SWIPE_GESTURES_MENU = "swipe_gestures_menu"
        const val PINCH_GESTURES_MENU = "pinch_gestures_menu"
        const val SCROLL_MENU = "scroll_menu"
        const val MEDIA_CONTROL_MENU = "media_control_menu"
        const val EDIT_MENU = "edit_menu"
        const val SETTINGS_MENU = "settings_menu"
        const val FAVOURITE_APPS_MENU = "favourite_apps_menu"
        const val GESTURE_PATTERNS_MENU = "gesture_patterns_menu"
        const val FINGER_MODE_MENU = "finger_mode_menu"
        const val AI_MENU = "ai_menu"
    }

    fun getTitleResource(menuId: String?): Int? = when (menuId) {
        MenuIds.MAIN_MENU -> R.string.menu_title_main
        MenuIds.AI_MENU -> R.string.menu_title_ai
        MenuIds.DEVICE_MENU -> R.string.menu_title_device
        MenuIds.VOLUME_CONTROL_MENU -> R.string.menu_title_volume_control
        MenuIds.GESTURES_MENU -> R.string.menu_title_gestures
        MenuIds.TAP_GESTURES_MENU -> R.string.menu_title_tap
        MenuIds.TAP_AND_HOLD_MENU -> R.string.menu_title_tap_and_hold
        MenuIds.SWIPE_GESTURES_MENU -> R.string.menu_title_swipe
        MenuIds.PINCH_GESTURES_MENU -> R.string.menu_title_pinch
        MenuIds.SCROLL_MENU -> R.string.menu_title_scroll
        MenuIds.MEDIA_CONTROL_MENU -> R.string.menu_title_media_control
        MenuIds.EDIT_MENU -> R.string.menu_title_edit
        MenuIds.SETTINGS_MENU -> R.string.menu_title_settings
        MenuIds.FAVOURITE_APPS_MENU -> R.string.menu_title_favourite_apps
        MenuIds.GESTURE_PATTERNS_MENU -> R.string.gesture_patterns_title
        MenuIds.FINGER_MODE_MENU -> R.string.menu_item_finger_mode
        else -> null
    }

    /**
     * Menu item identifiers organized by menu
     */
    object ItemIds {
        /**
         * Menu-wide navigation / manipulator items (close, prev/next page, etc.)
         * rendered outside the per-menu content (in the menu page's nav row).
         */
        object Navigation {
            const val CLOSE_MENU = "close_menu"
            const val PREV_PAGE = "prevPage"
            const val NEXT_PAGE = "nextPage"
        }

        /**
         * Main menu items
         */
        object Main {
            const val SYS_BACK = "sys_back"
            const val SYS_HOME = "sys_home"
            const val SCAN_KEYBOARD = "scan_keyboard"
            const val TAP = "tap"
            const val GESTURES = "gestures"
            const val SCROLL = "scroll"
            const val FAVOURITE_APPS = "favourite_apps"
            const val GESTURE_PATTERNS = "gesture_patterns"
            const val DEVICE = "device"
            const val SETTINGS = "settings"
            const val MEDIA_CONTROL = "media_control"
            const val EDIT = "edit"
            const val AI = "ai"
            const val PAUSE = "pause"
        }

        /**
         * AI submenu items
         */
        object Ai {
            const val REPLY_DRAFTER = "reply_drafter"
            const val SCREEN_HIGHLIGHTS = "screen_highlights"
        }

        /**
         * Settings menu items
         */
        object Settings {
            const val SWITCH_TO_ITEM_SCAN = "switch_to_item_scan"
            const val SWITCH_TO_RADAR = "switch_to_radar"
            const val SWITCH_TO_POINT_SCAN = "switch_to_point_scan"
            const val TOGGLE_HEAD_CONTROL = "toggle_head_control"
            const val TOGGLE_GROUP_SCAN = "toggle_group_scan"
        }

        /**
         * Edit menu items
         */
        object Edit {
            const val CUT = "cut"
            const val COPY = "copy"
            const val PASTE = "paste"
        }

        /**
         * Media control menu items
         */
        object Media {
            const val PLAY_PAUSE = "play_pause"
            const val PREVIOUS_TRACK = "previous_track"
            const val NEXT_TRACK = "next_track"
            const val VOLUME_CONTROL = "volume_control"
        }

        /**
         * Scroll menu items
         */
        object Scroll {
            const val SCROLL_UP = "scroll_up"
            const val SCROLL_DOWN = "scroll_down"
            const val SCROLL_LEFT = "scroll_left"
            const val SCROLL_RIGHT = "scroll_right"
        }

        /**
         * Device menu items
         */
        object Device {
            const val RECENT_APPS = "recent_apps"
            const val NOTIFICATIONS = "notifications"
            const val OPEN_ASSISTANT = "open_assistant"
            const val QUICK_SETTINGS = "quick_settings"
            const val LOCK_SCREEN = "lock_screen"
            const val POWER_DIALOG = "power_dialog"
            const val TAKE_SCREENSHOT = "take_screenshot"
            const val VOLUME_CONTROL = "volume_control"
        }

        /**
         * Volume control menu items
         */
        object Volume {
            const val VOLUME_UP = "volume_up"
            const val VOLUME_DOWN = "volume_down"
            const val FULL_VOLUME = "full_volume"
            const val MUTE = "mute"
            const val HALF_VOLUME = "half_volume"
        }

        /**
         * Gesture menu items
         */
        object Gestures {
            const val TAP_GESTURES = "tap_gestures"
            const val SWIPE_GESTURES = "swipe_gestures"
            const val DRAG = "drag"
            const val PINCH_GESTURES = "pinch_gestures"
            const val FINGER_MODE = "finger_mode"
            const val TOGGLE_GESTURE_LOCK = "toggle_gesture_lock"
        }

        /**
         * Tap gestures menu items
         */
        object TapGestures {
            const val TAP = "tap"
            const val DOUBLE_TAP = "double_tap"
            const val TAP_AND_HOLD = "tap_and_hold"
            const val TOGGLE_GESTURE_LOCK = "toggle_gesture_lock"
        }

        /**
         * Tap and hold submenu items
         */
        object TapAndHold {
            const val TAP_AND_HOLD_0_5S = "tap_and_hold_0_5s"
            const val TAP_AND_HOLD_1S = "tap_and_hold_1s"
            const val TAP_AND_HOLD_2S = "tap_and_hold_2s"
            const val TAP_AND_HOLD_3S = "tap_and_hold_3s"
            const val TAP_AND_HOLD_5S = "tap_and_hold_5s"
            const val TAP_AND_HOLD_10S = "tap_and_hold_10s"
        }

        /**
         * Swipe gestures menu items
         */
        object SwipeGestures {
            const val SWIPE_UP = "swipe_up"
            const val SWIPE_DOWN = "swipe_down"
            const val SWIPE_LEFT = "swipe_left"
            const val SWIPE_RIGHT = "swipe_right"
            const val CUSTOM_SWIPE = "custom_swipe"
            const val TOGGLE_GESTURE_LOCK = "toggle_gesture_lock"
        }

        /**
         * Pinch gestures menu items
         */
        object PinchGestures {
            const val PINCH_IN = "pinch_in"
            const val PINCH_OUT = "pinch_out"
            const val TOGGLE_GESTURE_LOCK = "toggle_gesture_lock"
        }
    }
}
