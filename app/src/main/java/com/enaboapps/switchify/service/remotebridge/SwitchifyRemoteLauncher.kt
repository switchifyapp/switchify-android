package com.enaboapps.switchify.service.remotebridge

import android.content.Context
import android.content.Intent
import android.net.Uri

class SwitchifyRemoteLauncher(private val context: Context) {
    fun openMouse() = open("mouse")
    fun openForwarding() = open("forwarding")

    private fun open(surface: String) {
        val intent = remoteIntent(surface).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (intent.resolveActivity(context.packageManager) != null) context.startActivity(intent)
        else context.startActivity(Intent(context, SwitchifyRemoteInstallActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    companion object {
        const val REMOTE_PACKAGE = "com.enaboapps.switchify.remote"
        internal fun remoteUri(surface: String) = "switchify-remote://remote?surface=$surface"
        internal fun remoteIntent(surface: String) = Intent(Intent.ACTION_VIEW, Uri.parse(remoteUri(surface))).setPackage(REMOTE_PACKAGE)
    }
}
