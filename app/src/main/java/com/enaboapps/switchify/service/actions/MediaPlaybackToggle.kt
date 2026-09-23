package com.enaboapps.switchify.service.actions

import kotlinx.coroutines.delay

internal class MediaPlaybackToggle(
    private val isMusicActive: () -> Boolean,
    private val dispatchToggle: () -> Boolean
) {
    suspend fun toggle(): Boolean? {
        val wasActive = isMusicActive()
        if (!dispatchToggle()) return null
        repeat(20) {
            delay(100)
            val active = isMusicActive()
            if (active != wasActive) return active
        }
        return null
    }
}
