package com.enaboapps.switchify.switches

import android.content.Context
import com.enaboapps.switchify.service.remotebridge.SwitchifyRemoteLauncher
import com.enaboapps.switchify.service.scanning.ScanMode
import com.enaboapps.switchify.service.scanning.ScanSettings

object SupportedActionsPolicy {
    fun supportedActionIds(context: Context): Set<Int> {
        val settings = ScanSettings(context)
        val mode = if (settings.isManualScanMode()) {
            ScanMode.Modes.MODE_MANUAL
        } else {
            ScanMode.Modes.MODE_AUTO
        }
        return supportedActionIdsForMode(mode)
    }

    internal fun supportedActionIdsForMode(mode: String): Set<Int> {
        val sys = setOf(
            SwitchAction.ACTION_LAUNCH_APP,
            SwitchAction.ACTION_SYS_HOME,
            SwitchAction.ACTION_SYS_BACK,
            SwitchAction.ACTION_SYS_RECENTS,
            SwitchAction.ACTION_SYS_QUICK_SETTINGS,
            SwitchAction.ACTION_SYS_NOTIFICATIONS,
            SwitchAction.ACTION_SYS_LOCK_SCREEN,
            SwitchAction.ACTION_SYS_HEADSET_HOOK,
            SwitchAction.ACTION_CONTROL_PC,
            SwitchAction.ACTION_PC_SWITCH_FORWARDING
        )

        return when (mode) {
            ScanMode.Modes.MODE_AUTO -> {
                setOf(
                    SwitchAction.ACTION_SELECT,
                    SwitchAction.ACTION_STOP_SCANNING,
                    SwitchAction.ACTION_CHANGE_SCANNING_DIRECTION,
                    SwitchAction.ACTION_TOGGLE_GESTURE_LOCK,
                    SwitchAction.ACTION_TOGGLE_GESTURE_LOCK_REARM,
                    SwitchAction.ACTION_TOGGLE_GESTURE_REPEAT,
                    SwitchAction.ACTION_PAUSE
                ) + sys
            }

            ScanMode.Modes.MODE_MANUAL -> {
                setOf(
                    SwitchAction.ACTION_SELECT,
                    SwitchAction.ACTION_MOVE_TO_NEXT_ITEM,
                    SwitchAction.ACTION_MOVE_TO_PREVIOUS_ITEM,
                    SwitchAction.ACTION_STOP_SCANNING,
                    SwitchAction.ACTION_TOGGLE_GESTURE_LOCK,
                    SwitchAction.ACTION_TOGGLE_GESTURE_LOCK_REARM,
                    SwitchAction.ACTION_TOGGLE_GESTURE_REPEAT,
                    SwitchAction.ACTION_PAUSE
                ) + sys
            }

            else -> supportedActionIdsForMode(ScanMode.Modes.MODE_AUTO)
        }
    }

    fun supportedActions(context: Context): List<SwitchAction> {
        val allowed = supportedActionIds(context)
        return SwitchAction.actions.filter { allowed.contains(it.id) }
    }

    /**
     * Actions offered in the action picker. Narrower than [supportedActionIds]: the
     * remote actions are dropped when Switchify Remote is not installed, so they
     * cannot be newly bound. [supportedActionIds] deliberately still reports them as
     * supported, because callers such as AddEditExternalSwitchScreenModel coerce any
     * action outside that set back to ACTION_SELECT, which would silently destroy an
     * existing binding while the remote app happened to be uninstalled.
     */
    fun selectableActions(context: Context): List<SwitchAction> {
        val allowed = selectableActionIds(
            supportedActionIds(context),
            SwitchifyRemoteLauncher.isInstalled(context)
        )
        return SwitchAction.actions.filter { allowed.contains(it.id) }
    }

    internal fun selectableActionIds(supported: Set<Int>, remoteInstalled: Boolean): Set<Int> {
        if (remoteInstalled) return supported
        return supported - REMOTE_ACTION_IDS
    }

    private val REMOTE_ACTION_IDS = setOf(
        SwitchAction.ACTION_CONTROL_PC,
        SwitchAction.ACTION_PC_SWITCH_FORWARDING
    )
}

