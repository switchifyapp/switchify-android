package com.enaboapps.switchify.service.screenshot

import android.accessibilityservice.AccessibilityService
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import android.view.Display
import androidx.annotation.RequiresApi
import com.enaboapps.switchify.service.core.SwitchifyAccessibilityService
import java.io.IOException

/**
 * Manages screenshot capture functionality with delay and flexible data handling
 */
object ScreenshotManager {
    private const val TAG = "ScreenshotManager"
    private const val DEFAULT_DELAY_MS = 3000L
    private const val DEFAULT_QUALITY = 90
    
    /**
     * Interface for screenshot callbacks
     */
    interface ScreenshotCallback {
        fun onScreenshotTaken(bitmap: Bitmap, timestamp: Long)
        fun onScreenshotSaved(uri: Uri?)
        fun onScreenshotFailed(error: String)
        fun onCountdownTick(remainingSeconds: Int) {}
    }
    
    /**
     * Takes a screenshot with configurable delay and options
     * 
     * @param accessibilityService The accessibility service instance
     * @param context Application context for saving
     * @param delayMs Delay in milliseconds before taking screenshot
     * @param saveToGallery Whether to save screenshot to gallery
     * @param callback Optional callback for handling screenshot data
     */
    fun takeScreenshotWithDelay(
        accessibilityService: SwitchifyAccessibilityService,
        context: Context,
        delayMs: Long = DEFAULT_DELAY_MS,
        saveToGallery: Boolean = true,
        callback: ScreenshotCallback? = null
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            callback?.onScreenshotFailed("Screenshot feature requires Android 11 (API 30) or higher")
            return
        }
        
        Log.d(TAG, "Starting screenshot with ${delayMs}ms delay")
        
        // Start countdown
        startCountdown(delayMs, callback) {
            captureScreenshot(accessibilityService, context, saveToGallery, callback)
        }
    }
    
    /**
     * Starts countdown with callback notifications
     */
    private fun startCountdown(
        delayMs: Long,
        callback: ScreenshotCallback?,
        onComplete: () -> Unit
    ) {
        val handler = Handler(Looper.getMainLooper())
        val totalSeconds = (delayMs / 1000).toInt()
        
        for (second in totalSeconds downTo 1) {
            handler.postDelayed({
                callback?.onCountdownTick(second)
                Log.d(TAG, "Screenshot in $second seconds...")
            }, (totalSeconds - second) * 1000L)
        }
        
        handler.postDelayed({
            onComplete()
        }, delayMs)
    }
    
    /**
     * Captures the actual screenshot
     */
    @RequiresApi(Build.VERSION_CODES.R)
    private fun captureScreenshot(
        accessibilityService: SwitchifyAccessibilityService,
        context: Context,
        saveToGallery: Boolean,
        callback: ScreenshotCallback?
    ) {
        Log.d(TAG, "Taking screenshot...")
        
        accessibilityService.takeScreenshot(
            Display.DEFAULT_DISPLAY,
            context.mainExecutor,
            object : AccessibilityService.TakeScreenshotCallback {
                override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                    Log.d(TAG, "Screenshot captured successfully")
                    
                    try {
                        // Convert HardwareBuffer to Bitmap
                        val bitmap = Bitmap.wrapHardwareBuffer(
                            result.hardwareBuffer,
                            result.colorSpace
                        )
                        
                        if (bitmap != null) {
                            val timestamp = System.currentTimeMillis()
                            
                            // Notify callback first
                            callback?.onScreenshotTaken(bitmap, timestamp)
                            
                            // Save to gallery if requested
                            if (saveToGallery) {
                                saveToGallery(context, bitmap, timestamp, callback)
                            }
                        } else {
                            Log.e(TAG, "Failed to create bitmap from HardwareBuffer")
                            callback?.onScreenshotFailed("Failed to create bitmap from screenshot data")
                        }
                        
                        // Important: Close the HardwareBuffer to prevent memory leaks
                        result.hardwareBuffer.close()
                        
                    } catch (e: Exception) {
                        Log.e(TAG, "Error processing screenshot", e)
                        callback?.onScreenshotFailed("Failed to process screenshot: ${e.message}")
                        result.hardwareBuffer.close()
                    }
                }
                
                override fun onFailure(errorCode: Int) {
                    Log.e(TAG, "Screenshot failed with error code: $errorCode")
                    callback?.onScreenshotFailed("Screenshot failed with error code: $errorCode")
                }
            }
        )
    }
    
    /**
     * Saves screenshot to device gallery using MediaStore API
     */
    private fun saveToGallery(
        context: Context,
        bitmap: Bitmap,
        timestamp: Long,
        callback: ScreenshotCallback?
    ) {
        try {
            val resolver = context.contentResolver
            val filename = "Switchify_Screenshot_$timestamp.jpg"
            
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.DATE_TAKEN, timestamp)
                
                // Use scoped storage for API 29+
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, "${android.os.Environment.DIRECTORY_PICTURES}/Switchify")
                    put(MediaStore.Images.Media.IS_PENDING, true)
                }
            }
            
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            
            if (uri != null) {
                try {
                    resolver.openOutputStream(uri)?.use { outputStream ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, DEFAULT_QUALITY, outputStream)
                    }
                    
                    // Mark as complete for API 29+
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        values.put(MediaStore.Images.Media.IS_PENDING, false)
                        resolver.update(uri, values, null, null)
                    }
                    
                    Log.d(TAG, "Screenshot saved to gallery: $uri")
                    callback?.onScreenshotSaved(uri)
                    
                } catch (e: IOException) {
                    Log.e(TAG, "Error saving screenshot to gallery", e)
                    resolver.delete(uri, null, null) // Cleanup on failure
                    callback?.onScreenshotFailed("Failed to save screenshot: ${e.message}")
                }
            } else {
                Log.e(TAG, "Failed to create MediaStore URI")
                callback?.onScreenshotFailed("Failed to create gallery entry")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error saving screenshot to gallery", e)
            callback?.onScreenshotFailed("Failed to save screenshot: ${e.message}")
        }
    }
    
    /**
     * Convenience method to take screenshot and save to gallery only
     */
    fun takeScreenshotAndSave(
        accessibilityService: SwitchifyAccessibilityService,
        context: Context,
        delayMs: Long = DEFAULT_DELAY_MS
    ) {
        takeScreenshotWithDelay(
            accessibilityService = accessibilityService,
            context = context,
            delayMs = delayMs,
            saveToGallery = true,
            callback = object : ScreenshotCallback {
                override fun onScreenshotTaken(bitmap: Bitmap, timestamp: Long) {
                    Log.d(TAG, "Screenshot taken and will be saved to gallery")
                }
                
                override fun onScreenshotSaved(uri: Uri?) {
                    Log.d(TAG, "Screenshot saved successfully to: $uri")
                }
                
                override fun onScreenshotFailed(error: String) {
                    Log.e(TAG, "Screenshot failed: $error")
                }
            }
        )
    }
}