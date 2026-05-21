package com.enaboapps.switchify.service.llm.model

import android.content.Context
import android.content.pm.ServiceInfo
import android.util.Log
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.enaboapps.switchify.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URI
import java.security.MessageDigest

class ModelDownloadWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        if (!ReplyDrafterModelConfig.isDownloadConfigured()) return@withContext Result.failure()
        if (runAttemptCount >= MAX_ATTEMPTS) return@withContext Result.failure()

        val modelManager = ModelManager(applicationContext)
        if (!modelManager.hasEnoughFreeSpace()) return@withContext Result.failure()
        modelManager.ensureModelDir()

        val partFile = modelManager.getPartFile()
        val targetFile = modelManager.getModelFile()

        try {
            ensureNotificationChannel()
            setForeground(buildForegroundInfo(0L, 0L))
            downloadToPartFile(partFile)

            val expectedSize = ReplyDrafterModelConfig.EXPECTED_SIZE_BYTES
            if (expectedSize > 0L && partFile.length() != expectedSize) {
                partFile.delete()
                return@withContext Result.retry()
            }
            if (!verifyChecksum(partFile)) {
                partFile.delete()
                return@withContext Result.retry()
            }
            if (targetFile.exists()) targetFile.delete()
            if (!partFile.renameTo(targetFile)) return@withContext Result.failure()

            modelManager.markModelReady()
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Model download failed", e)
            Result.retry()
        }
    }

    private suspend fun downloadToPartFile(partFile: File) {
        val existing = if (partFile.exists()) partFile.length() else 0L
        val connection = (URI(ReplyDrafterModelConfig.MODEL_URL).toURL()
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
                ReplyDrafterModelConfig.EXPECTED_SIZE_BYTES
            }

            RandomAccessFile(partFile, "rw").use { raf ->
                if (resuming) raf.seek(existing) else raf.setLength(0L)
                connection.inputStream.use { input ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var downloaded = startAt
                    var lastReported = startAt
                    while (true) {
                        if (isStopped) throw CancellationException("Download stopped")
                        val read = input.read(buffer)
                        if (read < 0) break
                        raf.write(buffer, 0, read)
                        downloaded += read
                        if (downloaded - lastReported >= PROGRESS_INTERVAL_BYTES) {
                            lastReported = downloaded
                            reportProgress(downloaded, totalBytes)
                        }
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun reportProgress(downloaded: Long, total: Long) {
        setProgress(
            workDataOf(
                ModelDownloadManager.KEY_PROGRESS_BYTES to downloaded,
                ModelDownloadManager.KEY_PROGRESS_TOTAL to total
            )
        )
        setForeground(buildForegroundInfo(downloaded, total))
    }

    private fun verifyChecksum(file: File): Boolean {
        val expected = ReplyDrafterModelConfig.EXPECTED_SHA256
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

    private fun ensureNotificationChannel() {
        NotificationManagerCompat.from(applicationContext).createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_LOW)
                .setName(applicationContext.getString(R.string.reply_drafter_download_channel))
                .build()
        )
    }

    private fun buildForegroundInfo(downloaded: Long, total: Long): ForegroundInfo {
        val percent = if (total > 0L) ((downloaded * 100L) / total).toInt() else 0
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle(applicationContext.getString(R.string.reply_drafter_download_notification))
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setProgress(100, percent, total <= 0L)
            .build()
        return ForegroundInfo(
            NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )
    }

    companion object {
        private const val TAG = "ModelDownloadWorker"
        private const val CHANNEL_ID = "reply_drafter_model_download"
        private const val NOTIFICATION_ID = 4720
        private const val MAX_ATTEMPTS = 5
        private const val BUFFER_SIZE = 64 * 1024
        private const val PROGRESS_INTERVAL_BYTES = 4L * 1024 * 1024
        private const val TIMEOUT_MS = 30_000
    }
}
