package com.enaboapps.switchify.pc.connection

import android.content.Context
import android.location.LocationManager
import android.os.Build
import androidx.core.location.LocationManagerCompat
import com.enaboapps.switchify.pc.storage.DeviceProtectedPcKeyValueStore
import com.enaboapps.switchify.pc.storage.PcPairingStore
import com.enaboapps.switchify.pc.storage.keystorePcSecretStore
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
            publicStore = DeviceProtectedPcKeyValueStore(appContext),
            secretStore = keystorePcSecretStore(appContext)
        ),
        diagnostics = diagnostics,
        requestPermission = permissions::request,
        remoteName = { PcRemoteName.deviceModelName(Build.MODEL) },
        locationServicesOff = ::locationServicesOff
    )

    private fun locationServicesOff(): Boolean {
        if (!PcBluetoothScanRequirements.requiresLocationServices(Build.VERSION.SDK_INT)) return false
        val locationManager = appContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return false
        return !LocationManagerCompat.isLocationEnabled(locationManager)
    }

    companion object {
        @Volatile
        private var instance: PcConnectionGraph? = null

        fun getInstance(context: Context): PcConnectionGraph =
            instance ?: synchronized(this) {
                instance ?: PcConnectionGraph(context).also { instance = it }
            }
    }
}

object PcBluetoothScanRequirements {
    fun requiresLocationServices(sdkInt: Int): Boolean = sdkInt < Build.VERSION_CODES.S
}
