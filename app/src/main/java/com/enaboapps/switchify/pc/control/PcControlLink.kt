package com.enaboapps.switchify.pc.control

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import com.enaboapps.switchify.activities.MainActivity
import com.enaboapps.switchify.pc.remote.PcRemoteSurface
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object PcControlLink {
    const val SCHEME = "switchify"
    const val HOST = "pc-control"
    const val SURFACE_PARAMETER = "surface"
    const val LEGACY_REMOTE_PACKAGE = "com.enaboapps.switchify.remote"

    fun matches(scheme: String?, host: String?): Boolean = scheme == SCHEME && host == HOST

    fun surface(value: String?): PcRemoteSurface? = PcRemoteSurface.entries.firstOrNull { it.storageKey == value }

    fun uri(surface: PcRemoteSurface?): String {
        val base = "$SCHEME://$HOST"
        return if (surface == null) base else "$base?$SURFACE_PARAMETER=${surface.storageKey}"
    }

    fun open(context: Context, surface: PcRemoteSurface?): Boolean = runCatching {
        val intent = Intent(Intent.ACTION_VIEW, uri(surface).toUri())
            .setClass(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        context.startActivity(intent)
        true
    }.getOrDefault(false)
}

object PcControlRequests {
    private val _surface = MutableStateFlow<PcRemoteSurface?>(null)
    val surface: StateFlow<PcRemoteSurface?> = _surface.asStateFlow()

    fun request(surface: PcRemoteSurface) {
        _surface.value = surface
    }

    fun consume(surface: PcRemoteSurface) {
        _surface.compareAndSet(surface, null)
    }
}
