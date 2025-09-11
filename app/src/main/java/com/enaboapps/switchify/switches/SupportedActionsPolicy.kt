package com.enaboapps.switchify.switches

import android.content.Context
import com.enaboapps.switchify.service.scanning.ScanSettings

object SupportedActionsPolicy {
    fun supportedActionIds(context: Context): Set<Int> {
        val settings = ScanSettings(context)
        val sys = setOf(
            SwitchAction.ACTION_SYS_HOME,
            SwitchAction.ACTION_SYS_BACK,
            SwitchAction.ACTION_SYS_RECENTS,
            SwitchAction.ACTION_SYS_QUICK_SETTINGS,
            SwitchAction.ACTION_SYS_NOTIFICATIONS,
            SwitchAction.ACTION_SYS_LOCK_SCREEN,
            SwitchAction.ACTION_SYS_HEADSET_HOOK
        )

        return when {
            settings.isAutoScanMode() -> {
                setOf(
                    SwitchAction.ACTION_SELECT,
                    SwitchAction.ACTION_STOP_SCANNING,
                    SwitchAction.ACTION_CHANGE_SCANNING_DIRECTION,
                    SwitchAction.ACTION_TOGGLE_GESTURE_LOCK,
                    SwitchAction.ACTION_PAUSE
                ) + sys
            }
            settings.isManualScanMode() -> {
                setOf(
                    SwitchAction.ACTION_SELECT,
                    SwitchAction.ACTION_MOVE_TO_NEXT_ITEM,
                    SwitchAction.ACTION_MOVE_TO_PREVIOUS_ITEM,
                    SwitchAction.ACTION_STOP_SCANNING,
                    SwitchAction.ACTION_TOGGLE_GESTURE_LOCK,
                    SwitchAction.ACTION_PAUSE
                ) + sys
            }
            settings.isDirectionalScanMode() -> {
                setOf(
                    SwitchAction.ACTION_SELECT,
                    SwitchAction.ACTION_STOP_SCANNING,
                    SwitchAction.ACTION_TOGGLE_GESTURE_LOCK,
                    SwitchAction.ACTION_PAUSE
                ) + sys
            }
            else -> emptySet()
        }
    }

    fun supportedActions(context: Context): List<SwitchAction> {
        val allowed = supportedActionIds(context)
        return SwitchAction.actions.filter { allowed.contains(it.id) }
    }
}

