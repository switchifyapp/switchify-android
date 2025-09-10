package com.enaboapps.switchify.service.core

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.enaboapps.switchify.service.camera.CameraForegroundService
import com.enaboapps.switchify.service.camera.CameraPermissionManager
import com.enaboapps.switchify.service.utils.DeviceLockObserver
import com.enaboapps.switchify.service.techniques.AccessTechnique
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class CameraServiceController(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val deviceLockObserver: DeviceLockObserver,
    private val serviceScope: CoroutineScope = CoroutineScope(Dispatchers.Main),
    private val onServiceConnected: (CameraForegroundService) -> Unit = {},
    private val onServiceDisconnected: () -> Unit = {}
) {
    var service: CameraForegroundService? = null
    private var isBound = false
    private val permissionManager = CameraPermissionManager.getInstance(context)

    private companion object {
        private const val TAG = "CameraServiceController"
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            logd("Camera service connected")
            val svc = (binder as CameraForegroundService.CameraServiceBinder).getService()
            service = svc
            isBound = true
            serviceScope.launch {
                if (svc.initialize()) {
                    onServiceConnected(svc)
                    startIfAvailable()
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            logd("Camera service disconnected")
            service = null
            isBound = false
            onServiceDisconnected()
        }
    }

    fun bindIfNeeded() {
        val provider = ServiceCore.getSwitchEventProvider()
        val headActive = AccessTechnique.getCurrentTechnique() == AccessTechnique.Technique.HEAD_CONTROL
        val needsCamera = provider?.hasCameraSwitch == true || headActive
        if (needsCamera && !isBound && permissionManager.hasPermission()) {
            val intent = Intent(context, CameraForegroundService::class.java)
            context.startService(intent)
            context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
            logd("Binding to camera foreground service (camera switches: ${provider?.hasCameraSwitch}, head control: $headActive)")
        } else if (needsCamera && !permissionManager.hasPermission()) {
            logd("Camera permission not granted, skipping camera service binding")
        }
    }

    fun unbindIfBound() {
        if (isBound) {
            service?.stopCamera()
            serviceScope.launch {
                service?.cleanup()
            }
            context.unbindService(connection)
            val intent = Intent(context, CameraForegroundService::class.java)
            context.stopService(intent)
            isBound = false
            service = null
            logd("Unbound from camera foreground service")
        }
    }

    fun startIfAvailable() {
        val provider = ServiceCore.getSwitchEventProvider()
        val headActive = AccessTechnique.getCurrentTechnique() == AccessTechnique.Technique.HEAD_CONTROL
        val needsCamera = provider?.hasCameraSwitch == true || headActive
        if (needsCamera && deviceLockObserver.isUserUnlocked() && permissionManager.hasPermission()) {
            service?.startCamera(lifecycleOwner)
            logd("Started camera service (camera switches: ${provider?.hasCameraSwitch}, head control: $headActive)")
        } else if (needsCamera && !permissionManager.hasPermission()) {
            logd("Camera permission not granted, cannot start camera service")
        }
    }


    private fun logd(message: String) {
        Log.d(TAG, message)
    }
}