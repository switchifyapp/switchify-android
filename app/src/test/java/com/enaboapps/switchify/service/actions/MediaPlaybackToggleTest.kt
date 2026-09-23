package com.enaboapps.switchify.service.actions

import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class MediaPlaybackToggleTest {
    @Test
    fun acceptedDispatchWithoutPlaybackDoesNotConfirmSuccess() = runTest {
        val toggle = MediaPlaybackToggle({ false }, { true })
        assertNull(toggle.toggle())
    }

    @Test
    fun unchangedActivePlaybackDoesNotConfirmPause() = runTest {
        val toggle = MediaPlaybackToggle({ true }, { true })
        assertNull(toggle.toggle())
    }

    @Test
    fun rejectedDispatchDoesNotConfirmAPlaybackChange() = runTest {
        var active = true
        val toggle = MediaPlaybackToggle({ active }, {
            active = false
            false
        })
        assertNull(toggle.toggle())
    }

    @Test
    fun waitsForDelayedPlaybackChange() = runTest {
        var active = false
        val toggle = MediaPlaybackToggle({ active }, {
            launch {
                delay(500)
                active = true
            }
            true
        })
        assertEquals(true, toggle.toggle())
    }

    @Test
    fun reusedActionReadsCurrentPlaybackOnEverySelection() = runTest {
        var active = false
        val toggle = MediaPlaybackToggle({ active }, {
            active = !active
            true
        })
        val cachedAction: suspend () -> Boolean? = { toggle.toggle() }
        active = true
        assertEquals(false, cachedAction())
        assertEquals(true, cachedAction())
        assertEquals(false, cachedAction())
    }

    @Test
    fun cancellationStopsPendingConfirmation() = runTest {
        var completed = false
        val toggle = MediaPlaybackToggle({ false }, { true })
        val job = launch {
            toggle.toggle()
            completed = true
        }
        delay(200)
        job.cancel()
        job.join()
        assertFalse(completed)
    }
}
