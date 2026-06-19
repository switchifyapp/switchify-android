package com.enaboapps.switchify.service.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.enaboapps.switchify.BuildConfig
import com.enaboapps.switchify.switches.SwitchAction

class AdbTestingBridgeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (!BuildConfig.DEBUG || intent?.action != ACTION_PERFORM_SWITCH_ACTION) return
        val actionId = resolveActionId(intent)
        if (!isValidActionId(actionId)) {
            Log.w(TAG, "Ignoring invalid ADB testing action: $actionId")
            return
        }
        ServiceBridge.sendCommand(
            ServiceBridge.ServiceCommand.PerformSwitchActionForTesting(
                actionId = actionId,
                source = "adb"
            )
        )
    }

    private fun resolveActionId(intent: Intent): Int {
        if (intent.hasExtra(EXTRA_ACTION_ID)) {
            return intent.getIntExtra(EXTRA_ACTION_ID, INVALID_ACTION_ID)
        }
        return when (intent.getStringExtra(EXTRA_ACTION)?.lowercase()) {
            "none" -> SwitchAction.ACTION_NONE
            "select" -> SwitchAction.ACTION_SELECT
            "stop_scanning" -> SwitchAction.ACTION_STOP_SCANNING
            "change_scanning_direction" -> SwitchAction.ACTION_CHANGE_SCANNING_DIRECTION
            "next" -> SwitchAction.ACTION_MOVE_TO_NEXT_ITEM
            "previous" -> SwitchAction.ACTION_MOVE_TO_PREVIOUS_ITEM
            "toggle_gesture_lock" -> SwitchAction.ACTION_TOGGLE_GESTURE_LOCK
            "home" -> SwitchAction.ACTION_SYS_HOME
            "back" -> SwitchAction.ACTION_SYS_BACK
            "recents" -> SwitchAction.ACTION_SYS_RECENTS
            "quick_settings" -> SwitchAction.ACTION_SYS_QUICK_SETTINGS
            "notifications" -> SwitchAction.ACTION_SYS_NOTIFICATIONS
            "lock_screen" -> SwitchAction.ACTION_SYS_LOCK_SCREEN
            "media_play_pause" -> SwitchAction.ACTION_SYS_HEADSET_HOOK
            "pause" -> SwitchAction.ACTION_PAUSE
            "toggle_gesture_lock_rearm" -> SwitchAction.ACTION_TOGGLE_GESTURE_LOCK_REARM
            "toggle_gesture_repeat" -> SwitchAction.ACTION_TOGGLE_GESTURE_REPEAT
            else -> INVALID_ACTION_ID
        }
    }

    private fun isValidActionId(actionId: Int): Boolean {
        return SwitchAction.actions.any { it.id == actionId }
    }

    companion object {
        const val ACTION_PERFORM_SWITCH_ACTION =
            "com.enaboapps.switchify.debug.PERFORM_SWITCH_ACTION"
        const val EXTRA_ACTION = "action"
        const val EXTRA_ACTION_ID = "action_id"
        private const val INVALID_ACTION_ID = -1
        private const val TAG = "AdbTestingBridge"
    }
}
