package com.enaboapps.switchify.service.camera

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
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
        private const val PERMISSION_CHECK_INTERVAL = 5000L // Check every 5 seconds
        
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
    private var isMonitoring = false
    private var permissionReceiver: BroadcastReceiver? = null
    private val handler = Handler(Looper.getMainLooper())
    
    // Callbacks for permission changes
    private var onPermissionGrantedCallback: (() -> Unit)? = null
    private var onPermissionRevokedCallback: (() -> Unit)? = null
    
    /**
     * Observable camera permission state.
     */
    val permissionState: StateFlow<Boolean> = _permissionState.asStateFlow()
    
    /**
     * Check if camera permission is currently granted.
     */
    fun hasPermission(): Boolean = _permissionState.value
    
    /**
     * Start monitoring camera permission changes
     * @param onGranted Callback when permission is granted
     * @param onRevoked Callback when permission is revoked
     */
    fun startMonitoring(
        onGranted: (() -> Unit)? = null,
        onRevoked: (() -> Unit)? = null
    ) {
        if (isMonitoring) {
            Log.d(TAG, "Permission monitoring already started")
            return
        }
        
        onPermissionGrantedCallback = onGranted
        onPermissionRevokedCallback = onRevoked
        
        // Create broadcast receiver for permission changes
        permissionReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_PACKAGE_REPLACED,
                    Intent.ACTION_PACKAGE_CHANGED -> {
                        val packageName = intent.data?.schemeSpecificPart
                        if (packageName == context?.packageName) {
                            // Our app's permissions might have changed
                            handler.post { refreshPermissionState() }
                        }
                    }
                }
            }
        }
        
        // Register receiver for package changes (which can include permission changes)
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        
        try {
            context.registerReceiver(permissionReceiver, filter)
            isMonitoring = true
            Log.d(TAG, "Started camera permission monitoring")
            
            // Start periodic checking as backup (some permission changes don't trigger broadcasts)
            startPeriodicCheck()
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register permission monitor", e)
            permissionReceiver = null
        }
    }
    
    /**
     * Stop monitoring camera permission changes
     */
    fun stopMonitoring() {
        if (!isMonitoring) {
            return
        }
        
        try {
            permissionReceiver?.let { receiver ->
                context.unregisterReceiver(receiver)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to unregister permission receiver", e)
        }
        
        permissionReceiver = null
        isMonitoring = false
        onPermissionGrantedCallback = null
        onPermissionRevokedCallback = null
        
        // Stop periodic checking
        handler.removeCallbacksAndMessages(null)
        
        Log.d(TAG, "Stopped camera permission monitoring")
    }
    
    /**
     * Refresh the permission state (call when permission might have changed).
     */
    fun refreshPermissionState() {
        val wasGranted = _permissionState.value
        val newState = checkPermissionSync()
        
        if (wasGranted != newState) {
            Log.i(TAG, "Camera permission state changed: $wasGranted -> $newState")
            _permissionState.value = newState
            
            // Notify callbacks
            if (newState && !wasGranted) {
                // Permission granted
                onPermissionGrantedCallback?.invoke()
            } else if (!newState && wasGranted) {
                // Permission revoked
                onPermissionRevokedCallback?.invoke()
            }
        }
    }
    
    /**
     * Start periodic permission checking as backup
     */
    private fun startPeriodicCheck() {
        val checkRunnable = object : Runnable {
            override fun run() {
                if (isMonitoring) {
                    refreshPermissionState()
                    handler.postDelayed(this, PERMISSION_CHECK_INTERVAL)
                }
            }
        }
        handler.postDelayed(checkRunnable, PERMISSION_CHECK_INTERVAL)
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