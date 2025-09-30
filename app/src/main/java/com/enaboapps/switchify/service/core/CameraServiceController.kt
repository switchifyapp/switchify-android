package com.enaboapps.switchify.service.core

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import androidx.lifecycle.LifecycleOwner
import com.enaboapps.switchify.service.camera.CameraForegroundService
import com.enaboapps.switchify.service.camera.CameraPermissionManager
import com.enaboapps.switchify.service.utils.DeviceLockObserver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout

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
    private var isBinding = false
    private val bindingMutex = Mutex()
    private val permissionManager = CameraPermissionManager.getInstance(context)

    private companion object {
        private const val TAG = "CameraServiceController"
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            logd("Camera service connected")
            val svc = (binder as CameraForegroundService.CameraServiceBinder).getService()
            serviceScope.launch {
                bindingMutex.withLock {
                    service = svc
                    isBound = true
                    isBinding = false
                }
                if (svc.initialize()) {
                    onServiceConnected(svc)
                    startIfAvailable()
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            logd("Camera service disconnected")
            serviceScope.launch {
                bindingMutex.withLock {
                    service = null
                    isBound = false
                    isBinding = false
                }
            }
            onServiceDisconnected()
        }
    }

    fun bindIfNeeded() {
        serviceScope.launch {
            bindingMutex.withLock {
                val provider = ServiceCore.getSwitchEventProvider()
                // Head control is now independent - check if it's enabled and ready
                val headControlService = ServiceCore.getHeadControlService()
                val headActive = headControlService?.isReady() == true
                val needsCamera = provider?.hasCameraSwitch == true || headActive

                if (needsCamera && !isBound && !isBinding && permissionManager.hasPermission()) {
                    try {
                        isBinding = true
                        val intent = Intent(context, CameraForegroundService::class.java)
                        context.startService(intent)
                        val bound =
                            context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
                        if (!bound) {
                            logd("Failed to bind to camera service")
                            isBinding = false
                        } else {
                            logd("Binding to camera foreground service (camera switches: ${provider?.hasCameraSwitch}, head control: $headActive)")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Exception during service binding", e)
                        isBinding = false
                    }
                } else if (needsCamera && !permissionManager.hasPermission()) {
                    logd("Camera permission not granted, skipping camera service binding")
                }
            }
        }
    }

    fun unbindIfBound() {
        serviceScope.launch {
            bindingMutex.withLock {
                if (isBound || isBinding) {
                    service?.stopCamera()

                    try {
                        withTimeout(5_000) {
                            service?.cleanup()
                        }
                    } catch (t: Throwable) {
                        Log.w(TAG, "Cleanup failed or timed out; proceeding with unbind/stop", t)
                    }

                    // Safe unbinding with exception handling
                    try {
                        if (isBound) {
                            context.unbindService(connection)
                            logd("Successfully unbound from camera foreground service")
                        }
                    } catch (e: IllegalArgumentException) {
                        Log.w(TAG, "Service was not registered or already unbound", e)
                    } catch (e: Exception) {
                        Log.e(TAG, "Unexpected exception during service unbinding", e)
                    } finally {
                        // Always clean up state regardless of unbind success
                        try {
                            context.stopService(
                                Intent(
                                    context,
                                    CameraForegroundService::class.java
                                )
                            )
                        } catch (e: Exception) {
                            Log.w(TAG, "Exception stopping service", e)
                        }

                        isBound = false
                        isBinding = false
                        service = null
                        logd("Camera service state reset")
                    }
                }
            }
        }
    }

    fun startIfAvailable() {
        val provider = ServiceCore.getSwitchEventProvider()
        // Head control is now independent - check if it's enabled and ready
        val headControlService = ServiceCore.getHeadControlService()
        val headActive = headControlService?.isReady() == true
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