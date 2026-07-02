package com.enaboapps.switchify.service.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.enaboapps.switchify.BuildConfig
import com.enaboapps.switchify.service.window.MenuHighlightHud
import com.enaboapps.switchify.service.window.ServiceMessageHUD
import com.enaboapps.switchify.service.window.ServiceStartupSplash
import com.enaboapps.switchify.service.window.SwitchifyAccessibilityWindow
import com.enaboapps.switchify.switches.SwitchAction

class AdbTestingBridgeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (!BuildConfig.DEBUG || intent?.action != ACTION_PERFORM_SWITCH_ACTION) return

        val actionName = intent.getStringExtra(EXTRA_ACTION)?.lowercase()
        if (actionName == ACTION_RELOAD_SETTINGS) {
            Log.d(TAG, "Performing ADB testing command: $ACTION_RELOAD_SETTINGS")
            ServiceBridge.sendCommand(ServiceBridge.ServiceCommand.ReloadSettings)
            return
        }
        if (actionName == ACTION_DUMP_OVERLAY_STATE) {
            Log.d(TAG, "Performing ADB testing command: $ACTION_DUMP_OVERLAY_STATE")
            SwitchifyAccessibilityWindow.instance.dumpOverlayDebugState()
            return
        }
        if (actionName == ACTION_CLEAR_OVERLAY_STATE) {
            Log.d(TAG, "Performing ADB testing command: $ACTION_CLEAR_OVERLAY_STATE")
            ServiceMessageHUD.instance.dispose()
            MenuHighlightHud.instance.dispose()
            ServiceStartupSplash.instance.dispose()
            SwitchifyAccessibilityWindow.instance.cleanup()
            return
        }

        val actionId = resolveActionId(intent)
        if (!isValidActionId(actionId)) {
            Log.w(TAG, "Ignoring invalid ADB testing action: ${actionName ?: actionId}")
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
        return actionNameToId[intent.getStringExtra(EXTRA_ACTION)?.lowercase()] ?: INVALID_ACTION_ID
    }

    private fun isValidActionId(actionId: Int): Boolean {
        return SwitchAction.actions.any { it.id == actionId }
    }

    companion object {
        const val ACTION_PERFORM_SWITCH_ACTION =
            "com.enaboapps.switchify.debug.PERFORM_SWITCH_ACTION"
        const val EXTRA_ACTION = "action"
        const val EXTRA_ACTION_ID = "action_id"
        const val ACTION_RELOAD_SETTINGS = "reload_settings"
        const val ACTION_DUMP_OVERLAY_STATE = "dump_overlay_state"
        const val ACTION_CLEAR_OVERLAY_STATE = "clear_overlay_state"
        private const val INVALID_ACTION_ID = -1
        private const val TAG = "AdbTestingBridge"

        val actionNameToId = mapOf(
            "none" to SwitchAction.ACTION_NONE,
            "select" to SwitchAction.ACTION_SELECT,
            "stop_scanning" to SwitchAction.ACTION_STOP_SCANNING,
            "change_scanning_direction" to SwitchAction.ACTION_CHANGE_SCANNING_DIRECTION,
            "next" to SwitchAction.ACTION_MOVE_TO_NEXT_ITEM,
            "previous" to SwitchAction.ACTION_MOVE_TO_PREVIOUS_ITEM,
            "toggle_gesture_lock" to SwitchAction.ACTION_TOGGLE_GESTURE_LOCK,
            "home" to SwitchAction.ACTION_SYS_HOME,
            "back" to SwitchAction.ACTION_SYS_BACK,
            "recents" to SwitchAction.ACTION_SYS_RECENTS,
            "quick_settings" to SwitchAction.ACTION_SYS_QUICK_SETTINGS,
            "notifications" to SwitchAction.ACTION_SYS_NOTIFICATIONS,
            "lock_screen" to SwitchAction.ACTION_SYS_LOCK_SCREEN,
            "media_play_pause" to SwitchAction.ACTION_SYS_HEADSET_HOOK,
            "pause" to SwitchAction.ACTION_PAUSE,
            "toggle_gesture_lock_rearm" to SwitchAction.ACTION_TOGGLE_GESTURE_LOCK_REARM,
            "toggle_gesture_repeat" to SwitchAction.ACTION_TOGGLE_GESTURE_REPEAT
        )
    }
}
