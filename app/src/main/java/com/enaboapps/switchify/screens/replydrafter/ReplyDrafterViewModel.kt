package com.enaboapps.switchify.screens.replydrafter

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enaboapps.switchify.R
import com.enaboapps.switchify.service.llm.AiFailure
import com.enaboapps.switchify.service.llm.AiResult
import com.enaboapps.switchify.service.llm.OnDeviceAi
import com.enaboapps.switchify.service.llm.ReplyDrafterScreenshotHolder
import com.enaboapps.switchify.service.llm.ReplyDrafterTask
import kotlinx.coroutines.launch

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
            _uiState.value = toUiState(OnDeviceAi.run(appContext, ReplyDrafterTask, image))
        }
    }

    private fun toUiState(result: AiResult<List<String>>): ReplyDrafterUiState =
        when (result) {
            is AiResult.Success ->
                if (result.value.isEmpty()) {
                    ReplyDrafterUiState.Failed(
                        R.string.reply_drafter_no_suggestions,
                        canRetry = true
                    )
                } else {
                    ReplyDrafterUiState.Suggestions(result.value)
                }

            is AiResult.Failure -> when (result.reason) {
                AiFailure.NOT_READY ->
                    ReplyDrafterUiState.Failed(
                        R.string.reply_drafter_model_not_ready,
                        canRetry = false
                    )

                AiFailure.INFERENCE_ERROR ->
                    ReplyDrafterUiState.Failed(
                        R.string.reply_drafter_llm_failed,
                        canRetry = true
                    )
            }
        }

    fun copyToClipboard(reply: String) {
        val clipboard =
            appContext.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText("reply", reply))
    }
}
