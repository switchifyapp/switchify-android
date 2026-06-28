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
            logError = { message, _ -> errors += message }
        ).cleanup(state)

        assertTrue(first.releaseAttempted)
        assertTrue(second.released)
        assertTrue(handles.isEmpty())
        assertEquals(listOf("Error releasing surface overlay"), errors)
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
