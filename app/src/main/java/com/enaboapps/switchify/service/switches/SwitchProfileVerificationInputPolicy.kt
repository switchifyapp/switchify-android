package com.enaboapps.switchify.service.switches

import com.enaboapps.switchify.switches.SwitchAction
import com.enaboapps.switchify.switches.profiles.SwitchProfileActivationState
import com.enaboapps.switchify.switches.profiles.SwitchProfileConfirmationMode

internal enum class SwitchProfileVerificationInputDecision {
    PASS_THROUGH,
    CONSUME,
    CONFIRM
}

internal object SwitchProfileVerificationInputPolicy {
    fun decide(
        state: SwitchProfileActivationState,
        action: SwitchAction
    ): SwitchProfileVerificationInputDecision {
        if (state !is SwitchProfileActivationState.Verifying) {
            return SwitchProfileVerificationInputDecision.PASS_THROUGH
        }
        return when (state.confirmationMode) {
            SwitchProfileConfirmationMode.INPUT_ACTION -> {
                if (action.id == SwitchAction.ACTION_SELECT) {
                    SwitchProfileVerificationInputDecision.CONFIRM
                } else {
                    SwitchProfileVerificationInputDecision.CONSUME
                }
            }
            SwitchProfileConfirmationMode.MENU -> when (action.id) {
                SwitchAction.ACTION_SELECT,
                SwitchAction.ACTION_MOVE_TO_NEXT_ITEM,
                SwitchAction.ACTION_MOVE_TO_PREVIOUS_ITEM ->
                    SwitchProfileVerificationInputDecision.PASS_THROUGH
                else -> SwitchProfileVerificationInputDecision.CONSUME
            }
        }
    }
}
