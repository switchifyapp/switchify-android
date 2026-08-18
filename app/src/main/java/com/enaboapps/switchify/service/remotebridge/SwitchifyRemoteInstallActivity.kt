package com.enaboapps.switchify.service.remotebridge

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import com.enaboapps.switchify.R

class SwitchifyRemoteInstallActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AlertDialog.Builder(this)
            .setTitle(R.string.switchify_remote_required_title)
            .setMessage(R.string.switchify_remote_required_message)
            .setPositiveButton(R.string.install_switchify_remote) { _, _ -> openStore() }
            .setNegativeButton(android.R.string.cancel) { _, _ -> finish() }
            .setOnCancelListener { finish() }
            .show()
    }
    private fun openStore() {
        val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${SwitchifyRemoteLauncher.REMOTE_PACKAGE}"))
        val web = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=${SwitchifyRemoteLauncher.REMOTE_PACKAGE}"))
        runCatching { startActivity(market) }.getOrElse { startActivity(web) }
        finish()
    }
}
