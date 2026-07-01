package com.enaboapps.switchify.service.window.overlay

import org.junit.Assert.assertFalse
import org.junit.Test

class SurfaceControlOverlayBackendTest {
    @Test
    fun missingHostTokenRejectsSurfaceAttachment() {
        assertFalse(SurfaceControlOverlayBackend.hasRequiredHostToken(null))
    }
}
