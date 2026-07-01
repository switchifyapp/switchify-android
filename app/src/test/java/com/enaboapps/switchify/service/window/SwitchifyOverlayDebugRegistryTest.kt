package com.enaboapps.switchify.service.window

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SwitchifyOverlayDebugRegistryTest {
    @After
    fun tearDown() {
        SwitchifyOverlayDebugRegistry.resetForTesting()
    }

    @Test
    fun recordsRootShowAndRemove() {
        val identity = OverlayDebugIdentity(
            stableId = "root:default-display",
            serviceEpoch = 3,
            generation = 4,
            sequence = 1
        )
        val id = SwitchifyOverlayDebugRegistry.recordRootShown(
            identity = identity,
            title = "Switchify:${identity.diagnosticId}",
            viewId = 12,
            viewClass = "android.widget.RelativeLayout",
            handleIdentityHash = 34
        )

        SwitchifyOverlayDebugRegistry.recordRootRemoved(id)

        val snapshot = SwitchifyOverlayDebugRegistry.snapshotText()
        assertTrue(snapshot.contains("id=root:default-display:epoch=3:generation=4:sequence=1"))
        assertTrue(snapshot.contains("stableId=root:default-display"))
        assertTrue(snapshot.contains("backend=window_manager_root"))
        assertTrue(snapshot.contains("serviceEpoch=3"))
        assertTrue(snapshot.contains("generation=4"))
        assertTrue(snapshot.contains("sequence=1"))
        assertTrue(snapshot.contains("target=Switchify:root:default-display:epoch=3:generation=4:sequence=1"))
        assertTrue(snapshot.contains("released=true"))
    }

    @Test
    fun recordsSurfaceAttachAndRelease() {
        val identity = OverlayDebugIdentity(
            stableId = "surface:display:0:menu",
            serviceEpoch = 5,
            generation = 6,
            sequence = 2
        )
        val id = identity.diagnosticId

        SwitchifyOverlayDebugRegistry.recordSurfaceAttached(
            identity = identity,
            backend = SwitchifyOverlayDebugRegistry.BACKEND_SURFACE_CONTROL_DISPLAY,
            target = "Display(displayId=0, forceSurface=true)",
            viewId = 56,
            viewClass = "android.widget.FrameLayout",
            handleIdentityHash = 78,
            surface = "Surface(name=test)"
        )
        SwitchifyOverlayDebugRegistry.recordSurfaceReleased(id)

        val snapshot = SwitchifyOverlayDebugRegistry.snapshotText()
        assertTrue(snapshot.contains("id=surface:display:0:menu:epoch=5:generation=6:sequence=2"))
        assertTrue(snapshot.contains("stableId=surface:display:0:menu"))
        assertTrue(snapshot.contains("backend=surface_control_display"))
        assertTrue(snapshot.contains("serviceEpoch=5"))
        assertTrue(snapshot.contains("generation=6"))
        assertTrue(snapshot.contains("sequence=2"))
        assertTrue(snapshot.contains("handle=78"))
        assertTrue(snapshot.contains("surface=Surface(name=test)"))
        assertTrue(snapshot.contains("released=true"))
    }

    @Test
    fun recordsSurfaceReleaseFailure() {
        val identity = OverlayDebugIdentity(
            stableId = "surface:window:0:1:1:menu",
            serviceEpoch = 6,
            generation = 7,
            sequence = 3
        )

        SwitchifyOverlayDebugRegistry.recordSurfaceAttached(
            identity = identity,
            backend = SwitchifyOverlayDebugRegistry.BACKEND_SURFACE_CONTROL_WINDOW,
            target = "Window(displayId=0, accessibilityWindowId=1, windowType=1)",
            viewId = 90,
            viewClass = "android.widget.FrameLayout",
            handleIdentityHash = 123,
            surface = "Surface(name=failed)"
        )
        SwitchifyOverlayDebugRegistry.recordSurfaceReleaseFailed(
            identity.diagnosticId,
            IllegalStateException("boom")
        )

        val snapshot = SwitchifyOverlayDebugRegistry.snapshotText()
        assertTrue(snapshot.contains("backend=surface_control_window"))
        assertTrue(snapshot.contains("releaseFailure=IllegalStateException: boom"))
        assertTrue(snapshot.contains("released=false"))
    }

    @Test
    fun clearsReleasedRecordsWithoutClearingActiveRecords() {
        val releasedIdentity = OverlayDebugIdentity(
            stableId = "released",
            serviceEpoch = 1,
            generation = 1,
            sequence = 1
        )
        val releasedId = SwitchifyOverlayDebugRegistry.recordRootShown(
            identity = releasedIdentity,
            title = "released",
            viewId = 1,
            viewClass = "Root",
            handleIdentityHash = 1
        )
        SwitchifyOverlayDebugRegistry.recordRootRemoved(releasedId)
        val activeIdentity = OverlayDebugIdentity(
            stableId = "active",
            serviceEpoch = 1,
            generation = 2,
            sequence = 2
        )
        SwitchifyOverlayDebugRegistry.recordRootShown(
            identity = activeIdentity,
            title = "active",
            viewId = 2,
            viewClass = "Root",
            handleIdentityHash = 2
        )

        SwitchifyOverlayDebugRegistry.clearReleased()

        val snapshot = SwitchifyOverlayDebugRegistry.snapshotText()
        assertFalse(snapshot.contains("target=released"))
        assertTrue(snapshot.contains("target=active"))
    }
}
