package com.enaboapps.switchify.service.window

import com.enaboapps.switchify.service.window.overlay.OverlayTargets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ServiceHudMessageTest {
    @Test
    fun disappearingMessagesMapTheirPresetOntoADuration() {
        ServiceMessageHUD.Time.entries.forEach { time ->
            assertEquals(
                time.milliseconds,
                ServiceHudMessage.durationFor(ServiceMessageHUD.MessageType.DISAPPEARING, time)
            )
        }
    }

    @Test
    fun permanentMessagesHaveNoDurationRegardlessOfTime() {
        ServiceMessageHUD.Time.entries.forEach { time ->
            assertNull(ServiceHudMessage.durationFor(ServiceMessageHUD.MessageType.PERMANENT, time))
        }
    }

    @Test
    fun defaultsMatchTheLegacyOverloads() {
        val message = ServiceHudMessage("hello")
        assertEquals(MessageSeverity.Info, message.severity)
        assertEquals(ServiceMessageHUD.Time.MEDIUM.milliseconds, message.durationMillis)
        assertEquals(OverlayTargets.defaultDisplay(), message.target)
    }
}
