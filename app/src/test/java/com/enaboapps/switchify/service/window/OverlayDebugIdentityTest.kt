package com.enaboapps.switchify.service.window

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class OverlayDebugIdentityTest {
    @Test
    fun diagnosticIdIsDeterministic() {
        val identity = OverlayDebugIdentity(
            stableId = "surface:display:0:menu",
            serviceEpoch = 42,
            generation = 3,
            sequence = 2
        )

        assertEquals(
            "surface:display:0:menu:epoch=42:generation=3:sequence=2",
            identity.diagnosticId
        )
    }

    @Test
    fun differentEpochProducesDifferentDiagnosticId() {
        val first = OverlayDebugIdentity("surface:display:0:menu", 1, 3, 2)
        val second = OverlayDebugIdentity("surface:display:0:menu", 2, 3, 2)

        assertNotEquals(first.diagnosticId, second.diagnosticId)
    }

    @Test
    fun differentSequenceProducesDifferentDiagnosticId() {
        val first = OverlayDebugIdentity("surface:display:0:menu", 1, 3, 1)
        val second = OverlayDebugIdentity("surface:display:0:menu", 1, 3, 2)

        assertNotEquals(first.diagnosticId, second.diagnosticId)
    }
}
