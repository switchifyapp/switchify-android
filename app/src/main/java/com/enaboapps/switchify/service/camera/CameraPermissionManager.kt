package com.enaboapps.switchify.service.camera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Centralized camera permission management for all camera-related components.
 * Provides consistent permission checking and state management.
 */
class CameraPermissionManager(private val context: Context) {
    
    companion object {
        private const val TAG = "CameraPermissionManager"
        
        @Volatile
        private var INSTANCE: CameraPermissionManager? = null
        
        fun getInstance(context: Context): CameraPermissionManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: CameraPermissionManager(context.applicationContext).also { 
                    INSTANCE = it 
                }
            }
        }
    }
    
    private val _permissionState = MutableStateFlow(checkPermissionSync())
    
    /**
     * Observable camera permission state.
     */
    val permissionState: StateFlow<Boolean> = _permissionState.asStateFlow()
    
    /**
     * Check if camera permission is currently granted.
     */
    fun hasPermission(): Boolean = _permissionState.value
    
    /**
     * Refresh the permission state (call when permission might have changed).
     */
    fun refreshPermissionState() {
        val newState = checkPermissionSync()
        if (_permissionState.value != newState) {
            Log.d(TAG, "Camera permission state changed: $newState")
            _permissionState.value = newState
        }
    }
    
    /**
     * Check camera permission synchronously.
     */
    private fun checkPermissionSync(): Boolean {
        return ContextCompat.checkSelfPermission(
            context, 
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    /**
     * Execute action only if camera permission is granted.
     * 
     * @param onGranted Action to execute if permission is granted
     * @param onDenied Optional action to execute if permission is denied
     * @return true if permission was granted and action was executed
     */
    fun withPermission(
        onGranted: () -> Unit,
        onDenied: (() -> Unit)? = null
    ): Boolean {
        return if (hasPermission()) {
            onGranted()
            true
        } else {
            Log.w(TAG, "Camera permission not granted")
            onDenied?.invoke()
            false
        }
    }
    
    /**
     * Execute suspending action only if camera permission is granted.
     * 
     * @param onGranted Suspending action to execute if permission is granted
     * @param onDenied Optional suspending action to execute if permission is denied
     * @return true if permission was granted and action was executed
     */
    suspend fun withPermissionSuspend(
        onGranted: suspend () -> Unit,
        onDenied: (suspend () -> Unit)? = null
    ): Boolean {
        return if (hasPermission()) {
            onGranted()
            true
        } else {
            Log.w(TAG, "Camera permission not granted")
            onDenied?.invoke()
            false
        }
    }
}