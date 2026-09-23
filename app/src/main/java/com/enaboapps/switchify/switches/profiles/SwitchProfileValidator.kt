package com.enaboapps.switchify.switches.profiles

import com.enaboapps.switchify.switches.SwitchEvent
import com.enaboapps.switchify.switches.SwitchHoldPolicy

internal object SwitchProfileValidator {
    fun validate(
        events: List<SwitchEvent>,
        requiredActionIds: Set<Int>,
        supportedActionIds: Set<Int>,
        holdEnabled: Boolean
    ): SwitchProfileValidationResult {
        val configured = SwitchHoldPolicy.configuredActionIds(events, holdEnabled)
        val unsupported = configured - supportedActionIds
        val missing = requiredActionIds - configured
        return SwitchProfileValidationResult(
            isValid = unsupported.isEmpty() && missing.isEmpty(),
            missingActionIds = missing,
            unsupportedActionIds = unsupported
        )
    }
}
