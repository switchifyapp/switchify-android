package com.enaboapps.switchify.pc.remote

import android.content.Context
import com.enaboapps.switchify.pc.connection.PcConnectionGraph
import com.enaboapps.switchify.pc.remote.layouts.PcLayoutStore
import com.enaboapps.switchify.pc.remote.layouts.PersistentPcLayoutStore
import com.enaboapps.switchify.pc.storage.DeviceProtectedPcKeyValueStore
import com.enaboapps.switchify.service.remotebridge.ScanningHold
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

    init {
        PcSwitchRepeatStop.availability = { SwitchifyRemoteBridgeCoordinator.hasConfiguredSwitches() }
        PcSwitchRepeatStop.armedListener = { armed ->
            SwitchifyRemoteBridgeCoordinator.setScanningHeld(ScanningHold.PcRepeat, armed)
        }
        scope.launch { layouts.load() }
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
