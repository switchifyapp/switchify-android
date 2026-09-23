package com.enaboapps.switchify.switches

import com.enaboapps.switchify.backend.preferences.PreferenceManager

internal object SwitchHoldPolicy {
    fun isEnabled(preferenceManager: PreferenceManager): Boolean =
        preferenceManager.getBooleanValue(
            PreferenceManager.PREFERENCE_KEY_SWITCH_HOLD_ENABLED,
            true
        )

    fun effectiveHoldActions(
        switchEvent: SwitchEvent,
        enabled: Boolean
    ): List<SwitchAction> = if (enabled) switchEvent.holdActions else emptyList()

    fun configuredActionIds(
        switchEvents: Iterable<SwitchEvent>,
        holdEnabled: Boolean
    ): Set<Int> = switchEvents.flatMap { switchEvent ->
        listOf(switchEvent.pressAction.id) +
            effectiveHoldActions(switchEvent, holdEnabled).map { it.id }
    }.toSet()
}
