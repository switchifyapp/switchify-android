package com.enaboapps.switchify.service.window

import java.util.concurrent.atomic.AtomicLong

internal object SwitchifyOverlayDebugRegistry {
    private val nextId = AtomicLong(1L)
    private val records = linkedMapOf<Long, OverlayDebugRecord>()

    fun recordRootShown(
        generation: Int,
        title: String,
        viewId: Int,
        viewClass: String,
        handleIdentityHash: Int
    ): Long {
        val id = nextId.getAndIncrement()
        synchronized(records) {
            records[id] = OverlayDebugRecord(
                id = id,
                backend = BACKEND_WINDOW_MANAGER_ROOT,
                generation = generation,
                target = title,
                viewId = viewId,
                viewClass = viewClass,
                handleIdentityHash = handleIdentityHash,
                attachedAtMs = System.currentTimeMillis()
            )
        }
        return id
    }

    fun recordRootRemoved(id: Long?) {
        recordReleased(id)
    }

    fun recordSurfaceAttached(
        id: Long,
        backend: String,
        generation: Int,
        target: String,
        viewId: Int,
        viewClass: String,
        handleIdentityHash: Int,
        surface: String
    ) {
        synchronized(records) {
            records[id] = OverlayDebugRecord(
                id = id,
                backend = backend,
                generation = generation,
                target = target,
                viewId = viewId,
                viewClass = viewClass,
                handleIdentityHash = handleIdentityHash,
                attachedAtMs = System.currentTimeMillis(),
                surface = surface
            )
        }
    }

    fun recordSurfaceReleased(id: Long) {
        recordReleased(id)
    }

    fun recordSurfaceReleaseFailed(id: Long, throwable: Throwable) {
        synchronized(records) {
            records[id] = records[id]?.copy(
                releaseFailure = "${throwable.javaClass.simpleName}: ${throwable.message}"
            ) ?: return
        }
    }

    fun snapshotText(): String {
        val snapshot = synchronized(records) { records.values.toList() }
        if (snapshot.isEmpty()) return "Switchify overlay registry is empty"
        return buildString {
            appendLine("Switchify overlay registry:")
            snapshot.forEach { record ->
                append("id=").append(record.id)
                    .append(" backend=").append(record.backend)
                    .append(" generation=").append(record.generation)
                    .append(" target=").append(record.target)
                    .append(" viewId=").append(record.viewId)
                    .append(" viewClass=").append(record.viewClass)
                    .append(" handle=").append(record.handleIdentityHash)
                    .append(" attachedAtMs=").append(record.attachedAtMs)
                    .append(" released=").append(record.released)
                record.releasedAtMs?.let { append(" releasedAtMs=").append(it) }
                record.surface?.let { append(" surface=").append(it) }
                record.releaseFailure?.let { append(" releaseFailure=").append(it) }
                appendLine()
            }
        }.trimEnd()
    }

    fun clearReleased() {
        synchronized(records) {
            records.entries.removeAll { it.value.released }
        }
    }

    internal fun resetForTesting() {
        synchronized(records) {
            records.clear()
        }
        nextId.set(1L)
    }

    fun nextOverlayId(): Long {
        return nextId.getAndIncrement()
    }

    private fun recordReleased(id: Long?) {
        if (id == null) return
        synchronized(records) {
            records[id] = records[id]?.copy(
                released = true,
                releasedAtMs = System.currentTimeMillis()
            ) ?: return
        }
    }

    internal const val BACKEND_WINDOW_MANAGER_ROOT = "window_manager_root"
    internal const val BACKEND_SURFACE_CONTROL_DISPLAY = "surface_control_display"
    internal const val BACKEND_SURFACE_CONTROL_WINDOW = "surface_control_window"

    private data class OverlayDebugRecord(
        val id: Long,
        val backend: String,
        val generation: Int,
        val target: String,
        val viewId: Int,
        val viewClass: String,
        val handleIdentityHash: Int,
        val attachedAtMs: Long,
        val releasedAtMs: Long? = null,
        val released: Boolean = false,
        val surface: String? = null,
        val releaseFailure: String? = null
    )
}
