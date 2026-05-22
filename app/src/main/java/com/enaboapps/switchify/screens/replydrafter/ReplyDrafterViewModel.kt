package com.enaboapps.switchify.screens.replydrafter

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enaboapps.switchify.R
import com.enaboapps.switchify.service.llm.AiCoreManager
import com.enaboapps.switchify.service.llm.LlmManager
import com.enaboapps.switchify.service.llm.ReplyDrafterScreenshotHolder
import com.enaboapps.switchify.service.llm.model.ModelManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface ReplyDrafterUiState {
    data object Loading : ReplyDrafterUiState
    data class Suggestions(val replies: List<String>) : ReplyDrafterUiState
    data class Failed(val messageRes: Int, val canRetry: Boolean) : ReplyDrafterUiState
}

class ReplyDrafterViewModel(context: Context) : ViewModel() {
    private val appContext = context.applicationContext
    private val bitmap: Bitmap? = ReplyDrafterScreenshotHolder.consume()

    private val _uiState = MutableLiveData<ReplyDrafterUiState>(ReplyDrafterUiState.Loading)
    val uiState: LiveData<ReplyDrafterUiState> = _uiState

    private var hasStarted = false

    /**
     * Start drafting once, when the activity first reaches the foreground.
     * AICore (Gemini Nano) only runs inference while the app is the top
     * foreground app, so this must not run from the view model's init.
     */
    fun start() {
        if (hasStarted) return
        hasStarted = true
        draft()
    }

    fun draft() {
        val image = bitmap
        if (image == null) {
            _uiState.value =
                ReplyDrafterUiState.Failed(R.string.reply_drafter_llm_failed, canRetry = false)
            return
        }
        _uiState.value = ReplyDrafterUiState.Loading
        viewModelScope.launch {
            _uiState.value = withContext(Dispatchers.Default) { runInference(image) }
        }
    }

    // AICore (Gemini Nano) when the device supports it, otherwise MediaPipe with
    // the downloaded Gemma model.
    private suspend fun runInference(image: Bitmap): ReplyDrafterUiState {
        return try {
            val suggestions =
                if (AiCoreManager.availability() == AiCoreManager.Availability.AVAILABLE) {
                    AiCoreManager.generateReplySuggestions(image)
                } else {
                    val modelPath = ModelManager(appContext).getModelFileIfReady()?.absolutePath
                        ?: return ReplyDrafterUiState.Failed(
                            R.string.reply_drafter_model_not_ready,
                            canRetry = false
                        )
                    generateWithMediaPipe(image, modelPath)
                }
            if (suggestions.isEmpty()) {
                ReplyDrafterUiState.Failed(R.string.reply_drafter_no_suggestions, canRetry = true)
            } else {
                ReplyDrafterUiState.Suggestions(suggestions)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Reply drafting failed", e)
            ReplyDrafterUiState.Failed(R.string.reply_drafter_llm_failed, canRetry = true)
        }
    }

    // LlmManager.generateReplySuggestions is callback-based but synchronous, so
    // one of the callbacks has fired by the time it returns.
    private fun generateWithMediaPipe(image: Bitmap, modelPath: String): List<String> {
        var result: List<String>? = null
        var error: String? = null
        LlmManager.generateReplySuggestions(
            context = appContext,
            bitmap = image,
            modelPath = modelPath,
            onResult = { result = it },
            onError = { error = it }
        )
        return result ?: throw IllegalStateException(error ?: "Reply drafting failed")
    }

    fun copyToClipboard(reply: String) {
        val clipboard =
            appContext.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText("reply", reply))
    }

    companion object {
        private const val TAG = "ReplyDrafterViewModel"
    }
}
