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
        val id = SwitchifyOverlayDebugRegistry.recordRootShown(
            generation = 4,
            title = "Switchify:root:generation=4",
            viewId = 12,
            viewClass = "android.widget.RelativeLayout",
            handleIdentityHash = 34
        )

        SwitchifyOverlayDebugRegistry.recordRootRemoved(id)

        val snapshot = SwitchifyOverlayDebugRegistry.snapshotText()
        assertTrue(snapshot.contains("backend=window_manager_root"))
        assertTrue(snapshot.contains("generation=4"))
        assertTrue(snapshot.contains("target=Switchify:root:generation=4"))
        assertTrue(snapshot.contains("released=true"))
    }

    @Test
    fun recordsSurfaceAttachAndRelease() {
        val id = SwitchifyOverlayDebugRegistry.nextOverlayId()

        SwitchifyOverlayDebugRegistry.recordSurfaceAttached(
            id = id,
            backend = SwitchifyOverlayDebugRegistry.BACKEND_SURFACE_CONTROL_DISPLAY,
            generation = 6,
            target = "Display(displayId=0, forceSurface=true)",
            viewId = 56,
            viewClass = "android.widget.FrameLayout",
            handleIdentityHash = 78,
            surface = "Surface(name=test)"
        )
        SwitchifyOverlayDebugRegistry.recordSurfaceReleased(id)

        val snapshot = SwitchifyOverlayDebugRegistry.snapshotText()
        assertTrue(snapshot.contains("backend=surface_control_display"))
        assertTrue(snapshot.contains("generation=6"))
        assertTrue(snapshot.contains("handle=78"))
        assertTrue(snapshot.contains("surface=Surface(name=test)"))
        assertTrue(snapshot.contains("released=true"))
    }

    @Test
    fun recordsSurfaceReleaseFailure() {
        val id = SwitchifyOverlayDebugRegistry.nextOverlayId()

        SwitchifyOverlayDebugRegistry.recordSurfaceAttached(
            id = id,
            backend = SwitchifyOverlayDebugRegistry.BACKEND_SURFACE_CONTROL_WINDOW,
            generation = 7,
            target = "Window(displayId=0, accessibilityWindowId=1, windowType=1)",
            viewId = 90,
            viewClass = "android.widget.FrameLayout",
            handleIdentityHash = 123,
            surface = "Surface(name=failed)"
        )
        SwitchifyOverlayDebugRegistry.recordSurfaceReleaseFailed(
            id,
            IllegalStateException("boom")
        )

        val snapshot = SwitchifyOverlayDebugRegistry.snapshotText()
        assertTrue(snapshot.contains("backend=surface_control_window"))
        assertTrue(snapshot.contains("releaseFailure=IllegalStateException: boom"))
        assertTrue(snapshot.contains("released=false"))
    }

    @Test
    fun clearsReleasedRecordsWithoutClearingActiveRecords() {
        val releasedId = SwitchifyOverlayDebugRegistry.recordRootShown(
            generation = 1,
            title = "released",
            viewId = 1,
            viewClass = "Root",
            handleIdentityHash = 1
        )
        SwitchifyOverlayDebugRegistry.recordRootRemoved(releasedId)
        SwitchifyOverlayDebugRegistry.recordRootShown(
            generation = 2,
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
