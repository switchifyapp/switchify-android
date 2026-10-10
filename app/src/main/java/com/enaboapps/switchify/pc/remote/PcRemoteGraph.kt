package com.enaboapps.switchify.pc.remote

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.enaboapps.switchify.pc.connection.PcConnectionGraph
import com.enaboapps.switchify.pc.remote.layouts.PcLayoutStore
import com.enaboapps.switchify.pc.remote.layouts.PersistentPcLayoutStore
import com.enaboapps.switchify.pc.storage.DeviceProtectedPcKeyValueStore
import com.enaboapps.switchify.service.core.ServiceCore
import com.enaboapps.switchify.service.remotebridge.SwitchifyRemoteBridgeCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class PcRemoteGraph private constructor(context: Context) {
    val connection = PcConnectionGraph.getInstance(context)

    val preferences: PcRemotePreferences = SharedPcRemotePreferences(context)

    val layouts: PcLayoutStore = PersistentPcLayoutStore(
        DeviceProtectedPcKeyValueStore(context, PersistentPcLayoutStore.FILE_NAME, PersistentPcLayoutStore.DIRECTORY)
    )

    val switchStop: PcRepeatSwitchStop = PcSwitchRepeatStop.hook

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val mainHandler = Handler(Looper.getMainLooper())

    init {
        PcSwitchRepeatStop.availability = { SwitchifyRemoteBridgeCoordinator.hasConfiguredSwitches() }
        PcSwitchRepeatStop.armedListener = { armed -> mainHandler.post { holdScanning(armed) } }
        scope.launch { layouts.load() }
    }

    private fun holdScanning(armed: Boolean) {
        val scanningManager = ServiceCore.getScanningManager() ?: return
        if (armed) {
            if (PcSwitchRepeatStop.isActive()) scanningManager.pauseScanning()
        } else if (!PcSwitchRepeatStop.isActive()) {
            scanningManager.resumeScanning()
        }
    }

    companion object {
        @Volatile
        private var instance: PcRemoteGraph? = null

        fun getInstance(context: Context): PcRemoteGraph =
            instance ?: synchronized(this) {
                instance ?: PcRemoteGraph(context.applicationContext).also { instance = it }
            }
    }
}
