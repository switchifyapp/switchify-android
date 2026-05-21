package com.enaboapps.switchify.service.llm

import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.util.Log
import com.enaboapps.switchify.R
import com.enaboapps.switchify.service.core.SwitchifyAccessibilityService
import com.enaboapps.switchify.service.llm.model.ModelManager
import com.enaboapps.switchify.service.screenshot.ScreenshotManager
import com.enaboapps.switchify.service.window.MessageSeverity
import com.enaboapps.switchify.service.window.ReplyDrafterHUD
import com.enaboapps.switchify.service.window.ServiceMessageHUD
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object ReplyDrafterManager {
    private const val TAG = "ReplyDrafterManager"
    private const val SCREENSHOT_DELAY_MS = 1000L

    fun startDrafting(service: SwitchifyAccessibilityService) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            ServiceMessageHUD.instance.showMessage(
                R.string.reply_drafter_unsupported_os,
                ServiceMessageHUD.MessageType.DISAPPEARING,
                ServiceMessageHUD.Time.MEDIUM,
                MessageSeverity.Error
            )
            return
        }

        val modelFile = ModelManager(service).getModelFileIfReady()
        if (modelFile == null) {
            ServiceMessageHUD.instance.showMessage(
                R.string.reply_drafter_model_not_ready,
                ServiceMessageHUD.MessageType.DISAPPEARING,
                ServiceMessageHUD.Time.LONG,
                MessageSeverity.Warning
            )
            return
        }

        ServiceMessageHUD.instance.showMessage(
            R.string.reply_drafter_starting,
            ServiceMessageHUD.MessageType.DISAPPEARING,
            ServiceMessageHUD.Time.SHORT
        )

        ScreenshotManager.takeScreenshotWithDelay(
            accessibilityService = service,
            context = service.applicationContext,
            delayMs = SCREENSHOT_DELAY_MS,
            saveToGallery = false,
            callback = object : ScreenshotManager.ScreenshotCallback {
                override fun onScreenshotTaken(bitmap: Bitmap, timestamp: Long) {
                    service.getServiceScope().launch(Dispatchers.Default) {
                        processScreenshot(service, bitmap, modelFile.absolutePath)
                    }
                }

                override fun onScreenshotSaved(uri: Uri?) {}

                override fun onScreenshotFailed(error: String) {
                    Log.e(TAG, "Screenshot failed: $error")
                    ServiceMessageHUD.instance.showMessage(
                        R.string.reply_drafter_screenshot_failed,
                        ServiceMessageHUD.MessageType.DISAPPEARING,
                        ServiceMessageHUD.Time.MEDIUM,
                        MessageSeverity.Error
                    )
                }
            }
        )
    }

    private fun processScreenshot(
        service: SwitchifyAccessibilityService,
        bitmap: Bitmap,
        modelPath: String
    ) {
        ServiceMessageHUD.instance.showMessage(
            R.string.reply_drafter_processing,
            ServiceMessageHUD.MessageType.PERMANENT,
            severity = MessageSeverity.Info
        )

        LlmManager.generateReplySuggestions(
            context = service.applicationContext,
            bitmap = bitmap,
            modelPath = modelPath,
            onResult = { suggestions ->
                if (suggestions.isEmpty()) {
                    ServiceMessageHUD.instance.showMessage(
                        R.string.reply_drafter_no_suggestions,
                        ServiceMessageHUD.MessageType.DISAPPEARING,
                        ServiceMessageHUD.Time.MEDIUM,
                        MessageSeverity.Warning
                    )
                } else {
                    ServiceMessageHUD.instance.showMessage(
                        R.string.reply_drafter_success,
                        ServiceMessageHUD.MessageType.DISAPPEARING,
                        ServiceMessageHUD.Time.SHORT,
                        MessageSeverity.Success
                    )
                    ReplyDrafterHUD.instance.showSuggestions(suggestions)
                }
            },
            onError = { error ->
                Log.e(TAG, "LLM error: $error")
                ServiceMessageHUD.instance.showMessage(
                    R.string.reply_drafter_llm_failed,
                    ServiceMessageHUD.MessageType.DISAPPEARING,
                    ServiceMessageHUD.Time.MEDIUM,
                    MessageSeverity.Error
                )
            }
        )
    }
}
