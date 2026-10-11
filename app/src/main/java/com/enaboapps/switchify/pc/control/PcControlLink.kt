package com.enaboapps.switchify.pc.control

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import com.enaboapps.switchify.activities.MainActivity
import com.enaboapps.switchify.pc.remote.PcRemoteSurface
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.URLEncoder

object PcControlLink {
    const val SCHEME = "switchify"
    const val HOST = "pc-control"
    const val SURFACE_PARAMETER = "surface"
    const val DESKTOP_PARAMETER = "pc"

    fun matches(scheme: String?, host: String?): Boolean = scheme == SCHEME && host == HOST

    fun surface(value: String?): PcRemoteSurface? = PcRemoteSurface.entries.firstOrNull { it.storageKey == value }

    fun desktopId(value: String?): String? = value?.takeIf { it.isNotBlank() }

    fun uri(surface: PcRemoteSurface?, desktopId: String? = null): String {
        val parameters = listOfNotNull(
            surface?.let { "$SURFACE_PARAMETER=${it.storageKey}" },
            desktopId?.let { "$DESKTOP_PARAMETER=${URLEncoder.encode(it, Charsets.UTF_8.name())}" }
        )
        val base = "$SCHEME://$HOST"
        return if (parameters.isEmpty()) base else "$base?${parameters.joinToString("&")}"
    }

    fun intent(context: Context): Intent = Intent(Intent.ACTION_VIEW)
        .setClass(context, MainActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)

    fun intent(context: Context, surface: PcRemoteSurface?, desktopId: String? = null): Intent =
        intent(context).setData(uri(surface, desktopId).toUri())

    fun open(context: Context, surface: PcRemoteSurface?): Boolean = runCatching {
        context.startActivity(intent(context, surface))
        true
    }.getOrDefault(false)
}

object PcControlRequests {
    private val _surface = MutableStateFlow<PcRemoteSurface?>(null)
    val surface: StateFlow<PcRemoteSurface?> = _surface.asStateFlow()

    private val _desktop = MutableStateFlow<String?>(null)
    val desktop: StateFlow<String?> = _desktop.asStateFlow()

    fun request(surface: PcRemoteSurface) {
        _surface.value = surface
    }

    fun consume(surface: PcRemoteSurface) {
        _surface.compareAndSet(surface, null)
    }

    fun requestDesktop(desktopId: String) {
        _desktop.value = desktopId
    }

    fun consumeDesktop(desktopId: String) {
        _desktop.compareAndSet(desktopId, null)
    }
}
