package com.enaboapps.switchify.switches

import android.content.Context
import com.enaboapps.switchify.service.scanning.ScanSettings

object RequiredActionsPolicy {
    fun requiredActionIds(context: Context): Set<Int> {
        val settings = ScanSettings(context)
        return when {
            settings.isAutoScanMode() -> setOf(SwitchAction.ACTION_SELECT)
            settings.isManualScanMode() -> setOf(
                SwitchAction.ACTION_SELECT,
                SwitchAction.ACTION_MOVE_TO_NEXT_ITEM,
                SwitchAction.ACTION_MOVE_TO_PREVIOUS_ITEM
            )
            settings.isDirectionalScanMode() -> setOf(
                SwitchAction.ACTION_SELECT
            )
            else -> emptySet()
        }
    }
}

