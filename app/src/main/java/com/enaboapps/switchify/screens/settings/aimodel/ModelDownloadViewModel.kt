package com.enaboapps.switchify.screens.settings.aimodel

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enaboapps.switchify.backend.preferences.PreferenceManager
import com.enaboapps.switchify.service.llm.model.ModelDownloader
import com.enaboapps.switchify.service.llm.model.ModelManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

sealed interface ModelDownloadUiState {
    data object NotDownloaded : ModelDownloadUiState
    data object Ready : ModelDownloadUiState
    data class Downloading(val bytesDownloaded: Long, val totalBytes: Long) : ModelDownloadUiState
    data object Failed : ModelDownloadUiState
}

class ModelDownloadViewModel(context: Context) : ViewModel() {
    private val appContext = context.applicationContext
    private val modelManager = ModelManager(appContext)
    private val downloader = ModelDownloader(appContext)
    private val preferenceManager = PreferenceManager(appContext)

    private val _uiState = MutableLiveData(idleState())
    val uiState: LiveData<ModelDownloadUiState> = _uiState

    private var downloadJob: Job? = null

    fun hasEnoughFreeSpace(): Boolean = modelManager.hasEnoughFreeSpace()

    fun isTermsAccepted(): Boolean = preferenceManager.getBooleanValue(
        PreferenceManager.PREFERENCE_KEY_GEMMA_TERMS_ACCEPTED
    )

    fun startDownload() {
        if (!isTermsAccepted()) return
        if (downloadJob?.isActive == true) return
        downloadJob = viewModelScope.launch {
            _uiState.value = ModelDownloadUiState.Downloading(0L, 0L)
            val success = downloader.download { downloaded, total ->
                _uiState.postValue(ModelDownloadUiState.Downloading(downloaded, total))
            }
            _uiState.value =
                if (success) ModelDownloadUiState.Ready else ModelDownloadUiState.Failed
        }
    }

    fun cancelDownload() {
        downloadJob?.cancel()
        downloadJob = null
        _uiState.value = idleState()
    }

    fun deleteModel() {
        modelManager.deleteModel()
        _uiState.value = idleState()
    }

    private fun idleState(): ModelDownloadUiState =
        if (modelManager.isModelReady()) ModelDownloadUiState.Ready
        else ModelDownloadUiState.NotDownloaded
}
