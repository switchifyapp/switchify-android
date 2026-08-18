package com.enaboapps.switchify.service.remotebridge

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.enaboapps.switchify.remotebridge.ISwitchifyRemoteBridge
import com.enaboapps.switchify.remotebridge.ISwitchifyRemoteBridgeCallback

class SwitchifyRemoteBridgeService : Service() {
    private val binder = object : ISwitchifyRemoteBridge.Stub() {
        override fun getVersion() = SwitchifyRemoteBridgeCoordinator.VERSION
        override fun getSnapshot() = SwitchifyRemoteBridgeCoordinator.snapshot()
        override fun registerCallback(callback: ISwitchifyRemoteBridgeCallback) = SwitchifyRemoteBridgeCoordinator.register(callback)
        override fun unregisterCallback(callback: ISwitchifyRemoteBridgeCallback) = SwitchifyRemoteBridgeCoordinator.unregister(callback)
        override fun setRepeatActive(generation: Long, active: Boolean) = SwitchifyRemoteBridgeCoordinator.setRepeatActive(generation, active)
        override fun setForwardingActive(generation: Long, active: Boolean) = SwitchifyRemoteBridgeCoordinator.setForwardingActive(generation, active)
    }
    override fun onBind(intent: Intent?): IBinder = binder
    override fun onUnbind(intent: Intent?): Boolean { SwitchifyRemoteBridgeCoordinator.clearActive(); return false }
    override fun onDestroy() { SwitchifyRemoteBridgeCoordinator.clearActive(); super.onDestroy() }
}
