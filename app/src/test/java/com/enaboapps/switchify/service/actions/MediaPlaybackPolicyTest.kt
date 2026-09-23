package com.enaboapps.switchify.service.actions

import org.junit.Assert.assertEquals
import org.junit.Test

class MediaPlaybackPolicyTest {
    private val policy = MediaPlaybackPolicy(recentWindowMillis = 1000)

    @Test
    fun nothingPlayingAndNoHistoryIsNone() {
        assertEquals(MediaPlaybackState.NONE, policy.observe(isMusicActive = false, supported = true, now = 0))
    }

    @Test
    fun playingIsActive() {
        assertEquals(MediaPlaybackState.ACTIVE, policy.observe(isMusicActive = true, supported = true, now = 0))
    }

    @Test
    fun recentlyStoppedStaysRecentUntilTheWindowExpires() {
        policy.observe(isMusicActive = true, supported = true, now = 0)
        assertEquals(MediaPlaybackState.RECENT, policy.observe(isMusicActive = false, supported = true, now = 500))
        assertEquals(MediaPlaybackState.RECENT, policy.observe(isMusicActive = false, supported = true, now = 1000))
        assertEquals(MediaPlaybackState.NONE, policy.observe(isMusicActive = false, supported = true, now = 1001))
        assertEquals(MediaPlaybackState.NONE, policy.observe(isMusicActive = false, supported = true, now = 1002))
    }

    @Test
    fun toggleRestartsTheGraceWindow() {
        policy.observe(isMusicActive = true, supported = true, now = 0)
        policy.noteToggled(now = 900)
        assertEquals(MediaPlaybackState.RECENT, policy.observe(isMusicActive = false, supported = true, now = 1800))
        assertEquals(MediaPlaybackState.NONE, policy.observe(isMusicActive = false, supported = true, now = 1901))
    }

    @Test
    fun resumingWithinTheWindowReturnsToActive() {
        policy.observe(isMusicActive = true, supported = true, now = 0)
        policy.observe(isMusicActive = false, supported = true, now = 500)
        assertEquals(MediaPlaybackState.ACTIVE, policy.observe(isMusicActive = true, supported = true, now = 800))
    }

    @Test
    fun unsupportedDevicesNeverShowControls() {
        assertEquals(MediaPlaybackState.NONE, policy.observe(isMusicActive = true, supported = false, now = 0))
        assertEquals(MediaPlaybackState.NONE, policy.observe(isMusicActive = false, supported = false, now = 100))
    }
}
