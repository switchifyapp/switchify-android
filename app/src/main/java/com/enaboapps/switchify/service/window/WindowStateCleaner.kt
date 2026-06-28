package com.enaboapps.switchify.service.window

import android.util.Log

internal data class WindowCleanupState(
    val surfaceHandles: MutableMap<Any, WindowCleanupHandle>,
    val root: WindowCleanupRoot?,
    val wasVisible: Boolean
)

internal interface WindowCleanupHandle {
    fun release()
}

internal interface WindowCleanupRoot {
    val isAttachedToWindow: Boolean

    fun removeDescendantViews()
    fun removeImmediately()
}

internal class WindowStateCleaner(
    private val logError: (String, Throwable) -> Unit = { message, throwable ->
        Log.e(TAG, message, throwable)
    },
    private val logWarning: (String, Throwable) -> Unit = { message, throwable ->
        Log.w(TAG, message, throwable)
    }
) {
    fun cleanup(state: WindowCleanupState) {
        state.surfaceHandles.values.forEach { handle ->
            try {
                handle.release()
            } catch (e: Exception) {
                logError("Error releasing surface overlay", e)
            }
        }
        state.surfaceHandles.clear()

        val root = state.root ?: return
        try {
            root.removeDescendantViews()
            if (state.wasVisible || root.isAttachedToWindow) {
                root.removeImmediately()
            }
        } catch (e: IllegalArgumentException) {
            logWarning("Window was already removed during cleanup", e)
        } catch (e: Exception) {
            logError("Error in cleanup", e)
        }
    }

    private companion object {
        private const val TAG = "WindowStateCleaner"
    }
}
