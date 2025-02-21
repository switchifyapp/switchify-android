package com.enaboapps.switchify.switches

import com.enaboapps.switchify.R
import com.enaboapps.switchify.utils.Resources
import com.google.gson.annotations.SerializedName

data class SwitchActionExtra(
    @SerializedName("my_actions_id") val myActionsId: String? = null,
    @SerializedName("my_action_name") val myActionName: String? = null
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "my_actions_id" to myActionsId,
        "my_action_name" to myActionName
    )
}

data class SwitchAction(
    @SerializedName("id") val id: Int,
    @SerializedName("extra") val extra: SwitchActionExtra? = null
) {
    companion object {
        fun fromMap(map: Map<String, Any>): SwitchAction {
            val id = map["id"] as Int
            val extra = map["extra"] as Map<*, *>?
            return SwitchAction(
                id,
                extra?.let {
                    SwitchActionExtra(
                        it["my_actions_id"] as String,
                        it["my_action_name"] as String
                    )
                })
        }

        val actions: List<SwitchAction> = listOf(
            ACTION_NONE,
            ACTION_SELECT,
            ACTION_STOP_SCANNING,
            ACTION_CHANGE_SCANNING_DIRECTION,
            ACTION_MOVE_TO_NEXT_ITEM,
            ACTION_MOVE_TO_PREVIOUS_ITEM,
            ACTION_TOGGLE_GESTURE_LOCK,
            ACTION_SYS_HOME,
            ACTION_SYS_BACK,
            ACTION_SYS_RECENTS,
            ACTION_SYS_QUICK_SETTINGS,
            ACTION_SYS_NOTIFICATIONS,
            ACTION_SYS_LOCK_SCREEN,
            ACTION_SYS_HEADSET_HOOK,
            ACTION_PERFORM_USER_ACTION
        ).map { SwitchAction(it) }

        const val ACTION_NONE = 0
        const val ACTION_SELECT = 1
        const val ACTION_STOP_SCANNING = 2
        const val ACTION_CHANGE_SCANNING_DIRECTION = 3
        const val ACTION_MOVE_TO_NEXT_ITEM = 4
        const val ACTION_MOVE_TO_PREVIOUS_ITEM = 5
        const val ACTION_TOGGLE_GESTURE_LOCK = 6
        const val ACTION_SYS_HOME = 7
        const val ACTION_SYS_BACK = 8
        const val ACTION_SYS_RECENTS = 9
        const val ACTION_SYS_QUICK_SETTINGS = 10
        const val ACTION_SYS_NOTIFICATIONS = 11
        const val ACTION_SYS_LOCK_SCREEN = 12
        const val ACTION_SYS_HEADSET_HOOK = 13
        const val ACTION_PERFORM_USER_ACTION = 14
    }

    fun toMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "extra" to extra?.toMap()
    )

    fun hasExtra(): Boolean = extra != null

    fun isExtraAvailable(): Boolean = id == ACTION_PERFORM_USER_ACTION

    fun getActionName(): String = when {
        hasExtra() -> getActionNameWithExtra()
        else -> getActionNameWithoutExtra()
    }

    private fun getActionNameWithExtra(): String = when (id) {
        ACTION_PERFORM_USER_ACTION -> Resources.getString(
            R.string.action_custom_with_name,
            extra?.myActionName ?: ""
        )

        else -> getActionNameWithoutExtra()
    }

    private fun getActionNameWithoutExtra(): String = when (id) {
        ACTION_NONE -> Resources.getString(R.string.action_none)
        ACTION_SELECT -> Resources.getString(R.string.action_select)
        ACTION_STOP_SCANNING -> Resources.getString(R.string.action_stop_scan)
        ACTION_CHANGE_SCANNING_DIRECTION -> Resources.getString(R.string.action_change_scan_direction)
        ACTION_MOVE_TO_NEXT_ITEM -> Resources.getString(R.string.action_next_item)
        ACTION_MOVE_TO_PREVIOUS_ITEM -> Resources.getString(R.string.action_previous_item)
        ACTION_TOGGLE_GESTURE_LOCK -> Resources.getString(R.string.system_gesture_lock)
        ACTION_SYS_HOME -> Resources.getString(R.string.system_home)
        ACTION_SYS_BACK -> Resources.getString(R.string.system_back)
        ACTION_SYS_RECENTS -> Resources.getString(R.string.system_recents)
        ACTION_SYS_QUICK_SETTINGS -> Resources.getString(R.string.system_quick_settings)
        ACTION_SYS_NOTIFICATIONS -> Resources.getString(R.string.system_notifications)
        ACTION_SYS_LOCK_SCREEN -> Resources.getString(R.string.system_lock_screen)
        ACTION_SYS_HEADSET_HOOK -> Resources.getString(R.string.action_headset)
        ACTION_PERFORM_USER_ACTION -> Resources.getString(R.string.action_custom)
        else -> Resources.getString(R.string.unknown)
    }

    fun getActionDescription(): String = when (id) {
        ACTION_NONE -> Resources.getString(R.string.action_none_desc)
        ACTION_SELECT -> Resources.getString(R.string.action_select_desc)
        ACTION_STOP_SCANNING -> Resources.getString(R.string.action_stop_scan_desc)
        ACTION_CHANGE_SCANNING_DIRECTION -> Resources.getString(R.string.action_change_scan_direction_desc)
        ACTION_MOVE_TO_NEXT_ITEM -> Resources.getString(R.string.action_next_item_desc)
        ACTION_MOVE_TO_PREVIOUS_ITEM -> Resources.getString(R.string.action_previous_item_desc)
        ACTION_TOGGLE_GESTURE_LOCK -> Resources.getString(R.string.system_gesture_lock_desc)
        ACTION_SYS_HOME -> Resources.getString(R.string.system_home_desc)
        ACTION_SYS_BACK -> Resources.getString(R.string.system_back_desc)
        ACTION_SYS_RECENTS -> Resources.getString(R.string.system_recents_desc)
        ACTION_SYS_QUICK_SETTINGS -> Resources.getString(R.string.system_quick_settings_desc)
        ACTION_SYS_NOTIFICATIONS -> Resources.getString(R.string.system_notifications_desc)
        ACTION_SYS_LOCK_SCREEN -> Resources.getString(R.string.system_lock_screen_desc)
        ACTION_SYS_HEADSET_HOOK -> Resources.getString(R.string.action_headset_desc)
        ACTION_PERFORM_USER_ACTION -> Resources.getString(R.string.action_custom_desc)
        else -> Resources.getString(R.string.unknown)
    }
}
