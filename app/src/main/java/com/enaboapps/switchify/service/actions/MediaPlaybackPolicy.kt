package com.enaboapps.switchify.service.actions

enum class MediaPlaybackState {
    /** Audio is playing right now. */
    ACTIVE,

    /** Audio was playing recently, so a resume entry is still useful. */
    RECENT,

    /** Nothing playing, nothing recent, or the toggle is unsupported on this device. */
    NONE
}

/**
 * Decides whether media controls belong in the main menu. Pure so it can be
 * unit tested; the caller supplies the audio manager's music-active flag,
 * whether the toggle is supported, and the clock.
 *
 * After playback stops the state stays [MediaPlaybackState.RECENT] for
 * [recentWindowMillis], so a user who just paused can resume from the same
 * place instead of digging into the media submenu.
 */
internal class MediaPlaybackPolicy(
    private val recentWindowMillis: Long = RECENT_WINDOW_MS
) {
    companion object {
        const val RECENT_WINDOW_MS = 10 * 60 * 1000L
    }

    private var lastActiveAt: Long? = null

    fun observe(isMusicActive: Boolean, supported: Boolean, now: Long): MediaPlaybackState {
        if (!supported) return MediaPlaybackState.NONE
        if (isMusicActive) {
            lastActiveAt = now
            return MediaPlaybackState.ACTIVE
        }
        val last = lastActiveAt ?: return MediaPlaybackState.NONE
        if (now - last <= recentWindowMillis) return MediaPlaybackState.RECENT
        lastActiveAt = null
        return MediaPlaybackState.NONE
    }

    /** Records that the user toggled playback, restarting the grace window from [now]. */
    fun noteToggled(now: Long) {
        lastActiveAt = now
    }
}
