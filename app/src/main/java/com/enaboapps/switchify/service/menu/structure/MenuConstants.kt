package com.enaboapps.switchify.service.menu.structure

/**
 * Constants for menu and menu item identifiers.
 * Provides type-safe access to menu structure identifiers without using raw strings.
 */
object MenuConstants {
    /**
     * Menu identifiers
     */
    object MenuIds {
        const val MAIN_MENU = "main_menu"
        const val DEVICE_MENU = "device_menu"
        const val VOLUME_CONTROL_MENU = "volume_control_menu"
        const val GESTURES_MENU = "gestures_menu"
        const val TAP_GESTURES_MENU = "tap_gestures_menu"
        const val SWIPE_GESTURES_MENU = "swipe_gestures_menu"
        const val PINCH_GESTURES_MENU = "pinch_gestures_menu"
        const val SCROLL_MENU = "scroll_menu"
        const val MEDIA_CONTROL_MENU = "media_control_menu"
        const val EDIT_MENU = "edit_menu"
    }

    /**
     * Menu item identifiers organized by menu
     */
    object ItemIds {
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
    }
}
