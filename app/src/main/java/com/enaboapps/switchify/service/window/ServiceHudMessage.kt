package com.enaboapps.switchify.service.window

import com.enaboapps.switchify.service.scanning.ScanInterval
import com.enaboapps.switchify.service.window.overlay.OverlayTarget
import com.enaboapps.switchify.service.window.overlay.OverlayTargets

/** Visual severity of a service HUD message. Colours and icons are resolved in the HUD's composable. */
enum class MessageSeverity {
    Info,
    Success,
    Warning,
    Error
}

/**
 * One message for [ServiceMessageHUD].
 *
 * A message with a [durationMillis] is a toast: it replaces whatever toast is
 * showing at once and hides itself. A message without one is a status banner:
 * it stays until cleared or replaced, sits underneath any toast, and collapses
 * to a chip after a while so it stops covering content.
 *
 * @property key Lets a status be dismissed by key so one feature never drops
 * another's banner.
 * @property countdown Optional timer drawn as a draining bar under the text,
 * for statuses that end on their own such as a pause timeout.
 * @property speak Whether to read the text aloud when item-scan speech is on.
 * Turn off for repeated re-shows of the same status.
 */
data class ServiceHudMessage(
    val text: String,
    val severity: MessageSeverity = MessageSeverity.Info,
    val durationMillis: Long? = ServiceMessageHUD.Time.MEDIUM.milliseconds,
    val target: OverlayTarget.Display = OverlayTargets.defaultDisplay(),
    val key: String? = null,
    val countdown: ScanInterval? = null,
    val speak: Boolean = true
) {
    val isStatus: Boolean get() = durationMillis == null

    companion object {
        /** Maps the legacy type and time pair onto a duration; permanent messages have none. */
        fun durationFor(type: ServiceMessageHUD.MessageType, time: ServiceMessageHUD.Time): Long? =
            if (type == ServiceMessageHUD.MessageType.DISAPPEARING) time.milliseconds else null
    }
}
