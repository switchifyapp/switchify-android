package com.enaboapps.switchify.service.window

/** How the HUD should draw the current message. */
internal enum class HudPresentation {
    /** Compact transient pill. */
    TOAST,

    /** Full status card. */
    BANNER,

    /** Collapsed status: icon plus one line. */
    CHIP
}

/**
 * What the HUD should show right now and when it next needs a [ServiceHudMessageController.tick].
 */
internal data class HudFrame(
    val message: ServiceHudMessage?,
    val presentation: HudPresentation?,
    val nextTickAt: Long?
) {
    companion object {
        val HIDDEN = HudFrame(null, null, null)
    }
}

/**
 * Pure state machine behind [ServiceMessageHUD]. All times are caller-supplied
 * milliseconds on one monotonic clock, so the rules can be unit tested.
 *
 * Rules:
 * - A toast shows immediately, replacing any toast already on screen. Prompts
 *   such as "release to perform" must land the instant they are sent, so
 *   nothing is ever held back or queued.
 * - A status message replaces any previous status. It shows as a banner when
 *   no toast is up, collapses to a chip after [collapseAfterMillis], and
 *   returns after the toast finishes.
 * - [dismiss] ends whatever is on screen: a toast, or otherwise the status.
 */
internal class ServiceHudMessageController(
    private val collapseAfterMillis: Long = COLLAPSE_AFTER_MS
) {
    companion object {
        const val COLLAPSE_AFTER_MS = 8000L
    }

    private var status: ServiceHudMessage? = null
    private var statusShownAt: Long? = null
    private var statusCollapsed = false
    private var toast: ServiceHudMessage? = null
    private var toastHideAt = 0L

    fun show(message: ServiceHudMessage, now: Long): HudFrame {
        if (message.isStatus) {
            status = message
            statusCollapsed = false
            statusShownAt = if (toast == null) now else null
        } else {
            toast = message
            toastHideAt = now + (message.durationMillis ?: 0L)
        }
        return frame(now)
    }

    fun tick(now: Long): HudFrame {
        if (toast != null) {
            if (now >= toastHideAt) endToast(now)
        } else if (status != null && !statusCollapsed) {
            val shownAt = statusShownAt
            if (shownAt != null && now - shownAt >= collapseAfterMillis) statusCollapsed = true
        }
        return frame(now)
    }

    fun dismiss(now: Long): HudFrame {
        if (toast != null) {
            endToast(now)
        } else {
            dropStatus()
        }
        return frame(now)
    }

    /** Whether a status message is held, shown or not. */
    fun hasStatus(): Boolean = status != null

    /**
     * Drops the status message without touching the toast. With a [key], only
     * a status carrying that key is dropped, so features cannot clear each
     * other's banners.
     */
    fun dismissStatus(now: Long, key: String? = null): HudFrame {
        val current = status
        if (current != null && (key == null || current.key == key)) dropStatus()
        return frame(now)
    }

    fun clear(now: Long): HudFrame {
        dropStatus()
        toast = null
        return frame(now)
    }

    private fun endToast(now: Long) {
        toast = null
        if (status != null && !statusCollapsed) statusShownAt = now
    }

    private fun dropStatus() {
        status = null
        statusShownAt = null
        statusCollapsed = false
    }

    private fun frame(now: Long): HudFrame {
        toast?.let { current ->
            return HudFrame(current, HudPresentation.TOAST, toastHideAt)
        }
        val banner = status ?: return HudFrame.HIDDEN
        if (statusCollapsed) return HudFrame(banner, HudPresentation.CHIP, null)
        val shownAt = statusShownAt ?: now.also { statusShownAt = it }
        return HudFrame(banner, HudPresentation.BANNER, shownAt + collapseAfterMillis)
    }
}
