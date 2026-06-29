package com.enaboapps.switchify.service.window

import android.util.Log

internal data class WindowCleanupState(
    val surfaceHandles: MutableMap<Any, WindowCleanupHandle>,
    val root: WindowCleanupRoot?,
    val wasVisible: Boolean,
    val generation: Int = -1
)

internal interface WindowCleanupHandle {
    fun release()
}

internal interface WindowCleanupRoot {
    val isAttachedToWindow: Boolean
    val debugOverlayId: Long?

    fun removeDescendantViews()
    fun removeImmediately()
}

internal class WindowStateCleaner(
    private val logError: (String, Throwable) -> Unit = { message, throwable ->
        Log.e(TAG, message, throwable)
    },
    private val logWarning: (String, Throwable) -> Unit = { message, throwable ->
        Log.w(TAG, message, throwable)
    },
    private val onCleanupStarted: (WindowCleanupState, Boolean) -> Unit = { _, _ -> },
    private val onHandleReleased: (Any) -> Unit = {},
    private val onHandleReleaseFailed: (Any, Throwable) -> Unit = { _, _ -> },
    private val onRootRemoved: (Long?) -> Unit = {},
    private val onRootAlreadyRemoved: (Long?, Throwable) -> Unit = { _, _ -> },
    private val onRootRemoveFailed: (Long?, Throwable) -> Unit = { _, _ -> }
) {
    fun cleanup(state: WindowCleanupState) {
        val rootAttachedAtCapture = state.root?.isAttachedToWindow == true
        onCleanupStarted(state, rootAttachedAtCapture)
        state.surfaceHandles.forEach { (key, handle) ->
            try {
                handle.release()
                onHandleReleased(key)
            } catch (e: Exception) {
                onHandleReleaseFailed(key, e)
                logError("Error releasing surface overlay", e)
            }
        }
        state.surfaceHandles.clear()

        val root = state.root ?: return
        try {
            root.removeDescendantViews()
            if (state.wasVisible || rootAttachedAtCapture || root.isAttachedToWindow) {
                root.removeImmediately()
                onRootRemoved(root.debugOverlayId)
            }
        } catch (e: IllegalArgumentException) {
            onRootAlreadyRemoved(root.debugOverlayId, e)
            logWarning("Window was already removed during cleanup", e)
        } catch (e: Exception) {
            onRootRemoveFailed(root.debugOverlayId, e)
            logError("Error in cleanup", e)
        }
    }

    private companion object {
        private const val TAG = "WindowStateCleaner"
    }
}
