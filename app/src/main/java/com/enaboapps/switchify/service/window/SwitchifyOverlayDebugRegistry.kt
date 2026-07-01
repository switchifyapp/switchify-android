package com.enaboapps.switchify.service.window

internal object SwitchifyOverlayDebugRegistry {
    private val records = linkedMapOf<String, OverlayDebugRecord>()

    fun recordRootShown(
        identity: OverlayDebugIdentity,
        title: String,
        viewId: Int,
        viewClass: String,
        handleIdentityHash: Int
    ): String {
        val id = identity.diagnosticId
        synchronized(records) {
            records[id] = OverlayDebugRecord(
                id = id,
                stableId = identity.stableId,
                backend = BACKEND_WINDOW_MANAGER_ROOT,
                serviceEpoch = identity.serviceEpoch,
                generation = identity.generation,
                sequence = identity.sequence,
                target = title,
                viewId = viewId,
                viewClass = viewClass,
                handleIdentityHash = handleIdentityHash,
                attachedAtMs = System.currentTimeMillis()
            )
        }
        return id
    }

    fun recordRootRemoved(id: String?) {
        recordReleased(id)
    }

    fun recordSurfaceAttached(
        identity: OverlayDebugIdentity,
        backend: String,
        target: String,
        viewId: Int,
        viewClass: String,
        handleIdentityHash: Int,
        surface: String
    ) {
        val id = identity.diagnosticId
        synchronized(records) {
            records[id] = OverlayDebugRecord(
                id = id,
                stableId = identity.stableId,
                backend = backend,
                serviceEpoch = identity.serviceEpoch,
                generation = identity.generation,
                sequence = identity.sequence,
                target = target,
                viewId = viewId,
                viewClass = viewClass,
                handleIdentityHash = handleIdentityHash,
                attachedAtMs = System.currentTimeMillis(),
                surface = surface
            )
        }
    }

    fun recordSurfaceReleased(id: String) {
        recordReleased(id)
    }

    fun recordSurfaceReleaseFailed(id: String, throwable: Throwable) {
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
                    .append(" stableId=").append(record.stableId)
                    .append(" backend=").append(record.backend)
                    .append(" serviceEpoch=").append(record.serviceEpoch)
                    .append(" generation=").append(record.generation)
                    .append(" sequence=").append(record.sequence)
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
    }

    private fun recordReleased(id: String?) {
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
        val id: String,
        val stableId: String,
        val backend: String,
        val serviceEpoch: Long,
        val generation: Int,
        val sequence: Long,
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
