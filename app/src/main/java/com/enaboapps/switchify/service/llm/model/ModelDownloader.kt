package com.enaboapps.switchify.service.llm.model

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URI
import java.security.MessageDigest

class ModelDownloader(context: Context) {

    private val modelManager = ModelManager(context.applicationContext)

    suspend fun download(onProgress: (downloaded: Long, total: Long) -> Unit): Boolean =
        withContext(Dispatchers.IO) {
            if (!modelManager.hasEnoughFreeSpace()) return@withContext false
            modelManager.ensureModelDir()

            val partFile = modelManager.getPartFile()
            val targetFile = modelManager.getModelFile()

            try {
                downloadToPartFile(partFile, onProgress)

                val expectedSize = AiModelConfig.EXPECTED_SIZE_BYTES
                if (expectedSize > 0L && partFile.length() != expectedSize) {
                    partFile.delete()
                    return@withContext false
                }
                if (!verifyChecksum(partFile)) {
                    partFile.delete()
                    return@withContext false
                }
                if (targetFile.exists()) targetFile.delete()
                if (!partFile.renameTo(targetFile)) return@withContext false

                true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Model download failed", e)
                false
            }
        }

    private suspend fun downloadToPartFile(
        partFile: File,
        onProgress: (downloaded: Long, total: Long) -> Unit
    ) {
        val existing = if (partFile.exists()) partFile.length() else 0L
        val connection = (URI(AiModelConfig.MODEL_URL).toURL()
            .openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            if (existing > 0L) setRequestProperty("Range", "bytes=$existing-")
        }
        try {
            connection.connect()
            val resuming = connection.responseCode == HttpURLConnection.HTTP_PARTIAL
            if (connection.responseCode != HttpURLConnection.HTTP_OK && !resuming) {
                throw IOException("Unexpected HTTP response ${connection.responseCode}")
            }
            val startAt = if (resuming) existing else 0L
            val remaining = connection.contentLengthLong
            val totalBytes = if (remaining > 0L) {
                startAt + remaining
            } else {
                AiModelConfig.EXPECTED_SIZE_BYTES
            }

            RandomAccessFile(partFile, "rw").use { raf ->
                if (resuming) raf.seek(existing) else raf.setLength(0L)
                connection.inputStream.use { input ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var downloaded = startAt
                    var lastReported = startAt
                    onProgress(downloaded, totalBytes)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        raf.write(buffer, 0, read)
                        downloaded += read
                        if (downloaded - lastReported >= PROGRESS_INTERVAL_BYTES) {
                            lastReported = downloaded
                            onProgress(downloaded, totalBytes)
                        }
                    }
                    onProgress(downloaded, totalBytes)
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun verifyChecksum(file: File): Boolean {
        val expected = AiModelConfig.EXPECTED_SHA256
        if (expected.isBlank()) return true
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
            .equals(expected, ignoreCase = true)
    }

    companion object {
        private const val TAG = "ModelDownloader"
        private const val BUFFER_SIZE = 64 * 1024
        private const val PROGRESS_INTERVAL_BYTES = 4L * 1024 * 1024
        private const val TIMEOUT_MS = 30_000
    }
}
