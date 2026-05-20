package com.enaboapps.switchify.service.llm

import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import com.enaboapps.switchify.R
import com.enaboapps.switchify.service.core.SwitchifyAccessibilityService
import com.enaboapps.switchify.service.screenshot.ScreenshotManager
import com.enaboapps.switchify.service.window.ReplyDrafterHUD
import com.enaboapps.switchify.service.window.ServiceMessageHUD
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Coordinates the reply drafting process: screenshot -> LLM -> suggestions HUD
 */
object ReplyDrafterManager {
    private const val TAG = "ReplyDrafterManager"
    
    /**
     * Starts the reply drafting process
     */
    fun startDrafting(service: SwitchifyAccessibilityService) {
        Log.d(TAG, "Starting reply drafting process")
        
        // Show initial message
        ServiceMessageHUD.instance.showMessage(
            R.string.reply_drafter_starting,
            ServiceMessageHUD.MessageType.DISAPPEARING,
            ServiceMessageHUD.Time.SHORT
        )
        
        // 1. Take screenshot
        ScreenshotManager.takeScreenshotWithDelay(
            accessibilityService = service,
            context = service.applicationContext,
            delayMs = 1000L, // Short delay to let menu close
            saveToGallery = false, // We only need it in memory
            callback = object : ScreenshotManager.ScreenshotCallback {
                override fun onScreenshotTaken(bitmap: Bitmap, timestamp: Long) {
                    service.getServiceScope().launch(Dispatchers.Default) {
                        processScreenshot(service, bitmap)
                    }
                }
                
                override fun onScreenshotSaved(uri: Uri?) {
                    // Not used since saveToGallery is false
                }
                
                override fun onScreenshotFailed(error: String) {
                    Log.e(TAG, "Screenshot failed: $error")
                    ServiceMessageHUD.instance.showMessage(
                        R.string.reply_drafter_screenshot_failed,
                        ServiceMessageHUD.MessageType.DISAPPEARING,
                        ServiceMessageHUD.Time.MEDIUM,
                        ServiceMessageHUD.MessageSeverity.Error
                    )
                }
                
                override fun onCountdownTick(remainingSeconds: Int) {
                    // Optional: show countdown if delay was longer
                }
            }
        )
    }
    
    /**
     * Processes the captured screenshot with the LLM
     */
    private fun processScreenshot(service: SwitchifyAccessibilityService, bitmap: Bitmap) {
        Log.d(TAG, "Processing screenshot with LLM")
        
        ServiceMessageHUD.instance.showMessage(
            R.string.reply_drafter_processing,
            ServiceMessageHUD.MessageType.PERMANENT,
            severity = ServiceMessageHUD.MessageSeverity.Info
        )
        
        LlmManager.generateReplySuggestions(
            context = service.applicationContext,
            bitmap = bitmap,
            onResult = { suggestions ->
                Log.d(TAG, "Generated ${suggestions.size} suggestions")
                
                // Hide processing message
                ServiceMessageHUD.instance.showMessage(
                    R.string.reply_drafter_success,
                    ServiceMessageHUD.MessageType.DISAPPEARING,
                    ServiceMessageHUD.Time.SHORT,
                    ServiceMessageHUD.MessageSeverity.Success
                )
                
                // Show suggestions in HUD
                ReplyDrafterHUD.instance.showSuggestions(suggestions)
            },
            onError = { error ->
                Log.e(TAG, "LLM error: $error")
                ServiceMessageHUD.instance.showMessage(
                    R.string.reply_drafter_llm_failed,
                    ServiceMessageHUD.MessageType.DISAPPEARING,
                    ServiceMessageHUD.Time.MEDIUM,
                    ServiceMessageHUD.MessageSeverity.Error
                )
            }
        )
    }
}
