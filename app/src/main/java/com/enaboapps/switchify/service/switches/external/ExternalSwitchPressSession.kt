package com.enaboapps.switchify.service.switches.external

import com.enaboapps.switchify.switches.SwitchEvent

internal sealed class ExternalSwitchPressSession {
    data object None : ExternalSwitchPressSession()

    data class ShortPressCandidate(
        val switchEvent: SwitchEvent,
        val pressTime: Long
    ) : ExternalSwitchPressSession()

    data class HoldPicker(
        val switchEvent: SwitchEvent,
        val pressTime: Long
    ) : ExternalSwitchPressSession()

    data class GestureLockReplayCandidate(
        val switchEvent: SwitchEvent,
        val pressTime: Long
    ) : ExternalSwitchPressSession()

    data object ReleaseSwallowed : ExternalSwitchPressSession()
}
