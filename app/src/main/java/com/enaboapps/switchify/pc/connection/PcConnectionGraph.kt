package com.enaboapps.switchify.pc.connection

import android.content.Context
import android.os.Build
import com.enaboapps.switchify.pc.storage.CredentialProtectedPcKeyValueStore
import com.enaboapps.switchify.pc.storage.KeystorePcSecretStore
import com.enaboapps.switchify.pc.storage.PcPairingStore
import com.enaboapps.switchify.pc.transport.AndroidPcGattAdapter
import com.enaboapps.switchify.pc.transport.PcBleTransport
import com.enaboapps.switchify.pc.transport.PcBluetoothPermissions

class PcConnectionGraph private constructor(context: Context) {
    private val appContext = context.applicationContext

    val diagnostics = PcDiagnosticLog()

    val permissions = PcPermissionRequester { PcBluetoothPermissions.hasAll(appContext) }

    val manager = PcConnectionManager(
        transport = PcBleTransport(AndroidPcGattAdapter(appContext), stageObserver = diagnostics),
        storage = PcPairingStore(
            publicStore = CredentialProtectedPcKeyValueStore(appContext),
            secretStore = KeystorePcSecretStore(appContext)
        ),
        diagnostics = diagnostics,
        requestPermission = permissions::request,
        remoteName = { PcRemoteName.deviceModelName(Build.MODEL) }
    )

    companion object {
        @Volatile
        private var instance: PcConnectionGraph? = null

        fun getInstance(context: Context): PcConnectionGraph =
            instance ?: synchronized(this) {
                instance ?: PcConnectionGraph(context).also { instance = it }
            }
    }
}
