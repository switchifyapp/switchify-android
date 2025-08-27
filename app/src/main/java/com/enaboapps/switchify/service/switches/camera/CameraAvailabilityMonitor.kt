package com.enaboapps.switchify.service.switches.camera

import android.content.Context
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.delay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.SupervisorJob

/**
 * Monitors camera availability and notifies when camera becomes available
 * after being taken by external apps
 */
class CameraAvailabilityMonitor(private val context: Context) {
    
    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    
    private var availabilityCallback: CameraManager.AvailabilityCallback? = null
    private var isMonitoring = false
    private var retryJob: Job? = null
    
    // Recovery state
    private var cameraWasTakenByExternalApp = false
    private var retryCount = 0
    private var lastRetryTime = 0L
    
    var onCameraAvailable: ((String) -> Unit)? = null
    var onCameraUnavailable: ((String) -> Unit)? = null
    var onCameraRecovered: ((String) -> Unit)? = null
    
    companion object {
        private const val TAG = "CameraAvailabilityMonitor"
        private const val FRONT_CAMERA_ID = "1"
        private const val MAX_RETRY_COUNT = 5
        private const val BASE_RETRY_DELAY = 1000L // 1 second
        private const val MAX_RETRY_DELAY = 30000L // 30 seconds
        private const val MIN_RETRY_INTERVAL = 5000L // 5 seconds between retries
    }
    
    fun startMonitoring() {
        if (isMonitoring) {
            Log.d(TAG, "Already monitoring camera availability")
            return
        }
        
        try {
            availabilityCallback = object : CameraManager.AvailabilityCallback() {
                override fun onCameraAvailable(cameraId: String) {
                    Log.d(TAG, "Camera $cameraId became available")
                    
                    if (cameraId == FRONT_CAMERA_ID) {
                        if (cameraWasTakenByExternalApp) {
                            Log.i(TAG, "Front camera recovered after external app usage")
                            cameraWasTakenByExternalApp = false
                            retryCount = 0
                            retryJob?.cancel()
                            
                            // Notify recovery with delay to ensure camera is fully available
                            mainHandler.postDelayed({
                                onCameraRecovered?.invoke(cameraId)
                            }, 500)
                        } else {
                            onCameraAvailable?.invoke(cameraId)
                        }
                    }
                }
                
                override fun onCameraUnavailable(cameraId: String) {
                    Log.d(TAG, "Camera $cameraId became unavailable")
                    
                    if (cameraId == FRONT_CAMERA_ID) {
                        cameraWasTakenByExternalApp = true
                        onCameraUnavailable?.invoke(cameraId)
                        startRecoveryRetries()
                    }
                }
            }
            
            cameraManager.registerAvailabilityCallback(availabilityCallback!!, mainHandler)
            isMonitoring = true
            Log.d(TAG, "Camera availability monitoring started")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start camera availability monitoring", e)
        }
    }
    
    fun stopMonitoring() {
        if (!isMonitoring) return
        
        try {
            availabilityCallback?.let { callback ->
                cameraManager.unregisterAvailabilityCallback(callback)
            }
            availabilityCallback = null
            isMonitoring = false
            retryJob?.cancel()
            retryJob = null
            
            Log.d(TAG, "Camera availability monitoring stopped")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop camera availability monitoring", e)
        }
    }
    
    private fun startRecoveryRetries() {
        retryJob?.cancel()
        retryJob = coroutineScope.launch {
            while (cameraWasTakenByExternalApp && retryCount < MAX_RETRY_COUNT) {
                val currentTime = System.currentTimeMillis()
                
                // Ensure minimum interval between retries
                if (currentTime - lastRetryTime < MIN_RETRY_INTERVAL) {
                    delay(MIN_RETRY_INTERVAL - (currentTime - lastRetryTime))
                }
                
                // Exponential backoff with max delay
                val delayTime = minOf(
                    BASE_RETRY_DELAY * (1L shl retryCount), 
                    MAX_RETRY_DELAY
                )
                
                Log.d(TAG, "Attempting camera recovery in ${delayTime}ms (attempt ${retryCount + 1}/$MAX_RETRY_COUNT)")
                delay(delayTime)
                
                if (cameraWasTakenByExternalApp) {
                    retryCount++
                    lastRetryTime = System.currentTimeMillis()
                    
                    // Check if camera is actually available now
                    if (isFrontCameraAvailable()) {
                        Log.i(TAG, "Camera available on retry attempt $retryCount")
                        cameraWasTakenByExternalApp = false
                        retryCount = 0
                        
                        mainHandler.post {
                            onCameraRecovered?.invoke(FRONT_CAMERA_ID)
                        }
                        break
                    }
                }
            }
            
            if (retryCount >= MAX_RETRY_COUNT) {
                Log.w(TAG, "Camera recovery failed after $MAX_RETRY_COUNT attempts")
                retryCount = 0
            }
        }
    }
    
    private fun isFrontCameraAvailable(): Boolean {
        return try {
            val cameraIds = cameraManager.cameraIdList
            cameraIds.contains(FRONT_CAMERA_ID)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check camera availability", e)
            false
        }
    }
    
    fun isCameraRecoveryNeeded(): Boolean {
        return cameraWasTakenByExternalApp
    }
    
    fun resetRecoveryState() {
        cameraWasTakenByExternalApp = false
        retryCount = 0
        retryJob?.cancel()
    }
}