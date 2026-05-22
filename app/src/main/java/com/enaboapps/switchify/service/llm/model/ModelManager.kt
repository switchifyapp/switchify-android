package com.enaboapps.switchify.service.llm.model

import android.content.Context
import java.io.File

class ModelManager(context: Context) {

    private val appContext = context.applicationContext

    fun getModelDir(): File = File(appContext.filesDir, AiModelConfig.MODEL_SUBDIR)

    fun getModelFile(): File = File(getModelDir(), AiModelConfig.MODEL_FILE_NAME)

    fun getPartFile(): File =
        File(getModelDir(), AiModelConfig.MODEL_FILE_NAME + ".part")

    fun ensureModelDir() {
        getModelDir().mkdirs()
        deleteLegacyModelFiles()
    }

    private fun deleteLegacyModelFiles() {
        for (name in LEGACY_MODEL_FILE_NAMES) {
            File(getModelDir(), name).delete()
        }
    }

    fun isModelReady(): Boolean {
        val file = getModelFile()
        if (!file.exists() || file.length() == 0L) return false
        val expected = AiModelConfig.EXPECTED_SIZE_BYTES
        return expected <= 0L || file.length() == expected
    }

    fun getModelFileIfReady(): File? = getModelFile().takeIf { isModelReady() }

    fun deleteModel() {
        getModelFile().delete()
        getPartFile().delete()
    }

    fun hasEnoughFreeSpace(): Boolean {
        val required = AiModelConfig.EXPECTED_SIZE_BYTES
        if (required <= 0L) return true
        return appContext.filesDir.usableSpace > required + FREE_SPACE_HEADROOM_BYTES
    }

    companion object {
        private const val FREE_SPACE_HEADROOM_BYTES = 250L * 1024 * 1024
        private val LEGACY_MODEL_FILE_NAMES = listOf(
            "gemma-3n-E2B-it-int4.task",
            "gemma-3n-E2B-it-int4.task.part"
        )
    }
}
