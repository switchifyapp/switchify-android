package com.enaboapps.switchify.service.llm

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.util.Log
import com.enaboapps.switchify.R
import com.enaboapps.switchify.screens.screenhighlights.ScreenHighlightsActivity
import com.enaboapps.switchify.service.core.SwitchifyAccessibilityService
import com.enaboapps.switchify.service.screenshot.ScreenshotManager
import com.enaboapps.switchify.service.window.MessageSeverity
import com.enaboapps.switchify.service.window.ServiceMessageHUD

object ScreenHighlightsManager {
    private const val TAG = "ScreenHighlightsManager"
    private const val SCREENSHOT_DELAY_MS = 1000L

    /**
     * Capture the screen, then open [ScreenHighlightsActivity] to extract the
     * actionable items. The activity is a visible, foreground screen — AICore
     * (Gemini Nano) only runs inference while the app is the top foreground app.
     */
    fun startExtracting(service: SwitchifyAccessibilityService) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            ServiceMessageHUD.instance.showMessage(
                R.string.screen_highlights_unsupported_os,
                ServiceMessageHUD.MessageType.DISAPPEARING,
                ServiceMessageHUD.Time.MEDIUM,
                severity = MessageSeverity.Error
            )
            return
        }

        ServiceMessageHUD.instance.showMessage(
            R.string.screen_highlights_starting,
            ServiceMessageHUD.MessageType.DISAPPEARING,
            ServiceMessageHUD.Time.SHORT,
            severity = MessageSeverity.Info
        )

        ScreenshotManager.takeScreenshotWithDelay(
            accessibilityService = service,
            context = service.applicationContext,
            delayMs = SCREENSHOT_DELAY_MS,
            saveToGallery = false,
            callback = object : ScreenshotManager.ScreenshotCallback {
                override fun onScreenshotTaken(bitmap: Bitmap, timestamp: Long) {
                    // Copy off the hardware bitmap before its HardwareBuffer is
                    // released, into ARGB_8888 as the LLM backends require.
                    val image = bitmap.copy(Bitmap.Config.ARGB_8888, false) ?: bitmap
                    ScreenHighlightsScreenshotHolder.set(image)
                    service.startActivity(
                        Intent(service, ScreenHighlightsActivity::class.java).addFlags(
                            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        )
                    )
                }

                override fun onScreenshotSaved(uri: Uri?) {}

                override fun onScreenshotFailed(error: String) {
                    Log.e(TAG, "Screenshot failed: $error")
                    ServiceMessageHUD.instance.showMessage(
                        R.string.screen_highlights_screenshot_failed,
                        ServiceMessageHUD.MessageType.DISAPPEARING,
                        ServiceMessageHUD.Time.MEDIUM,
                        severity = MessageSeverity.Error
                    )
                }
            }
        )
    }
}
