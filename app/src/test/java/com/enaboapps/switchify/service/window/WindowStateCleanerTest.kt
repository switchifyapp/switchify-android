package com.enaboapps.switchify.service.window

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WindowStateCleanerTest {
    @Test
    fun cleanupReleasesAllSurfaceHandlesAndClearsTracking() {
        val first = FakeHandle()
        val second = FakeHandle()
        val handles = mutableMapOf<Any, WindowCleanupHandle>(
            "first" to first,
            "second" to second
        )
        val state = WindowCleanupState(
            surfaceHandles = handles,
            root = null,
            wasVisible = false
        )

        WindowStateCleaner().cleanup(state)

        assertTrue(first.released)
        assertTrue(second.released)
        assertTrue(handles.isEmpty())
    }

    @Test
    fun cleanupRemovesRootWhenVisible() {
        val root = FakeRoot(attached = false)
        val state = WindowCleanupState(
            surfaceHandles = mutableMapOf(),
            root = root,
            wasVisible = true
        )

        WindowStateCleaner().cleanup(state)

        assertTrue(root.descendantsRemoved)
        assertTrue(root.removedImmediately)
    }

    @Test
    fun cleanupRemovesRootWhenAttachedEvenIfVisibilityIsStale() {
        val root = FakeRoot(attached = true)
        val state = WindowCleanupState(
            surfaceHandles = mutableMapOf(),
            root = root,
            wasVisible = false
        )

        WindowStateCleaner().cleanup(state)

        assertTrue(root.descendantsRemoved)
        assertTrue(root.removedImmediately)
    }

    @Test
    fun cleanupKeepsDetachedHiddenRootDetached() {
        val root = FakeRoot(attached = false)
        val state = WindowCleanupState(
            surfaceHandles = mutableMapOf(),
            root = root,
            wasVisible = false
        )

        WindowStateCleaner().cleanup(state)

        assertTrue(root.descendantsRemoved)
        assertFalse(root.removedImmediately)
    }

    @Test
    fun cleanupTreatsAlreadyRemovedRootAsSuccess() {
        val warnings = mutableListOf<String>()
        val root = FakeRoot(attached = true, removeFailure = IllegalArgumentException("already removed"))
        val state = WindowCleanupState(
            surfaceHandles = mutableMapOf(),
            root = root,
            wasVisible = true
        )

        WindowStateCleaner(
            logWarning = { message, _ -> warnings += message }
        ).cleanup(state)

        assertTrue(root.descendantsRemoved)
        assertEquals(listOf("Window was already removed during cleanup"), warnings)
    }

    @Test
    fun cleanupContinuesWhenAHandleThrows() {
        val errors = mutableListOf<String>()
        val releasedKeys = mutableListOf<Any>()
        val failedKeys = mutableListOf<Any>()
        val first = FakeHandle(releaseFailure = IllegalStateException("boom"))
        val second = FakeHandle()
        val handles = mutableMapOf<Any, WindowCleanupHandle>(
            "first" to first,
            "second" to second
        )
        val state = WindowCleanupState(
            surfaceHandles = handles,
            root = null,
            wasVisible = false
        )

        WindowStateCleaner(
            logError = { message, _ -> errors += message },
            onHandleReleased = { key -> releasedKeys += key },
            onHandleReleaseFailed = { key, _ -> failedKeys += key }
        ).cleanup(state)

        assertTrue(first.releaseAttempted)
        assertTrue(second.released)
        assertTrue(handles.isEmpty())
        assertEquals(listOf("Error releasing surface overlay"), errors)
        assertEquals(listOf("second"), releasedKeys)
        assertEquals(listOf("first"), failedKeys)
    }

    @Test
    fun cleanupReportsCapturedState() {
        var capturedHandles = -1
        var capturedVisible = false
        var capturedAttached = false
        var capturedGeneration = -1
        val root = FakeRoot(attached = true)
        val state = WindowCleanupState(
            surfaceHandles = mutableMapOf("first" to FakeHandle()),
            root = root,
            wasVisible = false,
            generation = 9
        )

        WindowStateCleaner(
            onCleanupStarted = { cleanupState, rootAttached ->
                capturedHandles = cleanupState.surfaceHandles.size
                capturedVisible = cleanupState.wasVisible
                capturedAttached = rootAttached
                capturedGeneration = cleanupState.generation
            }
        ).cleanup(state)

        assertEquals(1, capturedHandles)
        assertFalse(capturedVisible)
        assertTrue(capturedAttached)
        assertEquals(9, capturedGeneration)
    }

    @Test
    fun cleanupReportsRootRemovalOutcome() {
        val removedRoots = mutableListOf<String?>()
        val rootId = "root:default-display:epoch=1:generation=1:sequence=1"
        val root = FakeRoot(attached = true, debugOverlayId = rootId)
        val state = WindowCleanupState(
            surfaceHandles = mutableMapOf(),
            root = root,
            wasVisible = true
        )

        WindowStateCleaner(
            onRootRemoved = { id -> removedRoots += id }
        ).cleanup(state)

        assertEquals(listOf(rootId), removedRoots)
    }

    @Test
    fun cleanupReportsAlreadyRemovedRootOutcome() {
        val alreadyRemovedRoots = mutableListOf<String?>()
        val rootId = "root:default-display:epoch=1:generation=1:sequence=1"
        val root = FakeRoot(
            attached = true,
            debugOverlayId = rootId,
            removeFailure = IllegalArgumentException("already removed")
        )
        val state = WindowCleanupState(
            surfaceHandles = mutableMapOf(),
            root = root,
            wasVisible = true
        )

        WindowStateCleaner(
            logWarning = { _, _ -> },
            onRootAlreadyRemoved = { id, _ -> alreadyRemovedRoots += id }
        ).cleanup(state)

        assertEquals(listOf(rootId), alreadyRemovedRoots)
    }

    private class FakeHandle(
        private val releaseFailure: RuntimeException? = null
    ) : WindowCleanupHandle {
        var releaseAttempted = false
            private set
        var released = false
            private set

        override fun release() {
            releaseAttempted = true
            releaseFailure?.let { throw it }
            released = true
        }
    }

    private class FakeRoot(
        private val attached: Boolean,
        override val debugOverlayId: String? = null,
        private val removeFailure: RuntimeException? = null
    ) : WindowCleanupRoot {
        var descendantsRemoved = false
            private set
        var removedImmediately = false
            private set

        override val isAttachedToWindow: Boolean
            get() = attached

        override fun removeDescendantViews() {
            descendantsRemoved = true
        }

        override fun removeImmediately() {
            removedImmediately = true
            removeFailure?.let { throw it }
        }
    }
}
