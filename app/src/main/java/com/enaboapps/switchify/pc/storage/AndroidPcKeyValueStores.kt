package com.enaboapps.switchify.pc.storage

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import androidx.annotation.RequiresApi
import androidx.core.util.AtomicFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

class DeviceProtectedPcKeyValueStore(
    context: Context,
    private val fileName: String = FILE_NAME
) : PcKeyValueStore {
    private val directory = File(context.applicationContext.createDeviceProtectedStorageContext().filesDir, DIRECTORY)
    private val lock = Any()

    private fun file() = File(directory, fileName)

    private fun read(): JSONObject {
        val file = file()
        if (!file.exists()) return JSONObject()
        return JSONObject(String(AtomicFile(file).readFully(), Charsets.UTF_8))
    }

    private fun write(value: JSONObject) {
        if (!directory.isDirectory && !directory.mkdirs()) throw IllegalStateException(UNAVAILABLE)
        val atomicFile = AtomicFile(file())
        val stream = atomicFile.startWrite()
        try {
            stream.write(value.toString().toByteArray(Charsets.UTF_8))
            atomicFile.finishWrite(stream)
        } catch (error: Exception) {
            atomicFile.failWrite(stream)
            throw error
        }
    }

    override suspend fun get(key: String): String? = withContext(Dispatchers.IO) {
        synchronized(lock) { read().optString(key, "").ifEmpty { null } }
    }

    override suspend fun put(key: String, value: String) = withContext(Dispatchers.IO) {
        synchronized(lock) { write(read().put(key, value)) }
    }

    override suspend fun remove(key: String) = withContext(Dispatchers.IO) {
        synchronized(lock) {
            val current = read()
            if (current.has(key)) {
                current.remove(key)
                write(current)
            }
        }
    }

    override suspend fun clear() = withContext(Dispatchers.IO) {
        synchronized(lock) {
            AtomicFile(file()).delete()
        }
    }

    companion object {
        const val DIRECTORY = "switchify_pc"
        const val FILE_NAME = "pairings.json"
        const val SECRETS_FILE_NAME = "secrets.json"
        private const val UNAVAILABLE = "PC pairing storage is unavailable."
    }
}

class AndroidKeystorePcSecretKeySource : PcSecretKeySource {
    override fun existingKey(): SecretKey? {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        return keyStore.getKey(KEY_ALIAS, null) as? SecretKey
    }

    override fun deleteKey() {
        synchronized(keyLock) {
            KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }.deleteEntry(KEY_ALIAS)
        }
    }

    override fun createKey(): SecretKey = synchronized(keyLock) {
        existingKey() ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).run {
            init(
                KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(KEY_BITS)
                    .build()
            )
            generateKey()
        }
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "switchify_pc_pairing_secrets"
        private const val KEY_BITS = 256
        private val keyLock = Any()
    }
}

fun keystorePcSecretStore(context: Context): PcKeyValueStore = EncryptedPcKeyValueStore(
    backing = DeviceProtectedPcKeyValueStore(context, DeviceProtectedPcKeyValueStore.SECRETS_FILE_NAME),
    cipher = PcSecretCipher(AndroidKeystorePcSecretKeySource(), AndroidPcKeyFailureClassifier)
)

object AndroidPcKeyFailureClassifier : PcKeyFailureClassifier {
    override fun isPermanent(error: Throwable): Boolean = PcSecretCipher.causes(error).any { cause ->
        cause is KeyPermanentlyInvalidatedException ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && isPermanentKeystoreFailure(cause))
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun isPermanentKeystoreFailure(cause: Throwable): Boolean =
        cause is android.security.KeyStoreException && !cause.isTransientFailure
}
