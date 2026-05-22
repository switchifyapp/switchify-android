package com.enaboapps.switchify.service.llm

import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.util.Log
import com.enaboapps.switchify.R
import com.enaboapps.switchify.service.core.SwitchifyAccessibilityService
import com.enaboapps.switchify.service.llm.model.ModelManager
import com.enaboapps.switchify.service.menu.MenuManager
import com.enaboapps.switchify.service.screenshot.ScreenshotManager
import com.enaboapps.switchify.service.window.MessageSeverity
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

        service.getServiceScope().launch {
            val useAiCore =
                AiCoreManager.availability() == AiCoreManager.Availability.AVAILABLE
            val modelPath = if (useAiCore) {
                null
            } else {
                ModelManager(service).getModelFileIfReady()?.absolutePath
            }
            if (!useAiCore && modelPath == null) {
                ServiceMessageHUD.instance.showMessage(
                    R.string.reply_drafter_model_not_ready,
                    ServiceMessageHUD.MessageType.DISAPPEARING,
                    ServiceMessageHUD.Time.LONG,
                    MessageSeverity.Warning
                )
                return@launch
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
                        // Copy off the hardware bitmap before its HardwareBuffer is
                        // released, and into ARGB_8888 as the LLM backends require.
                        val image = bitmap.copy(Bitmap.Config.ARGB_8888, false) ?: bitmap
                        service.getServiceScope().launch(Dispatchers.Default) {
                            processScreenshot(service, image, modelPath)
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
    }

    // modelPath null → AICore (Gemini Nano); non-null → MediaPipe + the downloaded model.
    private suspend fun processScreenshot(
        service: SwitchifyAccessibilityService,
        bitmap: Bitmap,
        modelPath: String?
    ) {
        ServiceMessageHUD.instance.showMessage(
            R.string.reply_drafter_processing,
            ServiceMessageHUD.MessageType.PERMANENT,
            severity = MessageSeverity.Info
        )

        if (modelPath == null) {
            try {
                onSuggestions(service, AiCoreManager.generateReplySuggestions(bitmap))
            } catch (e: Exception) {
                Log.e(TAG, "AICore error", e)
                onLlmError()
            }
        } else {
            LlmManager.generateReplySuggestions(
                context = service.applicationContext,
                bitmap = bitmap,
                modelPath = modelPath,
                onResult = { onSuggestions(service, it) },
                onError = { error ->
                    Log.e(TAG, "LLM error: $error")
                    onLlmError()
                }
            )
        }
    }

    private fun onSuggestions(
        service: SwitchifyAccessibilityService,
        suggestions: List<String>
    ) {
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
            service.getServiceScope().launch(Dispatchers.Main) {
                MenuManager.getInstance().openReplyDrafterMenu(suggestions)
            }
        }
    }

    private fun onLlmError() {
        ServiceMessageHUD.instance.showMessage(
            R.string.reply_drafter_llm_failed,
            ServiceMessageHUD.MessageType.DISAPPEARING,
            ServiceMessageHUD.Time.MEDIUM,
            MessageSeverity.Error
        )
    }
}
