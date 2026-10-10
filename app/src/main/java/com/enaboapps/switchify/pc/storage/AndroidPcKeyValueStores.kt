package com.enaboapps.switchify.pc.storage

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.core.util.AtomicFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object PcCredentialProtectedStorage {
    const val DIRECTORY = "switchify_pc"

    fun directory(context: Context): File {
        val deviceProtectedRoot = context.applicationContext.createDeviceProtectedStorageContext().dataDir
        return File(File(credentialProtectedRoot(deviceProtectedRoot.path), "files"), DIRECTORY)
    }

    fun credentialProtectedRoot(deviceProtectedRoot: String): String {
        val segments = deviceProtectedRoot.split('/')
        val index = segments.lastIndexOf(DEVICE_PROTECTED_SEGMENT)
        check(index >= 0 && index == segments.size - 3) { "Credential-protected storage is unavailable." }
        return segments.toMutableList().also { it[index] = CREDENTIAL_PROTECTED_SEGMENT }.joinToString("/")
    }

    private const val DEVICE_PROTECTED_SEGMENT = "user_de"
    private const val CREDENTIAL_PROTECTED_SEGMENT = "user"
}

private class PcJsonFile(private val file: File) {
    private val atomicFile = AtomicFile(file)

    fun read(): JSONObject {
        if (!file.exists()) return JSONObject()
        return JSONObject(String(atomicFile.readFully(), Charsets.UTF_8))
    }

    fun write(value: JSONObject) {
        file.parentFile?.let { parent -> if (!parent.isDirectory && !parent.mkdirs()) throw IllegalStateException(UNAVAILABLE) }
        val stream = atomicFile.startWrite()
        try {
            stream.write(value.toString().toByteArray(Charsets.UTF_8))
            atomicFile.finishWrite(stream)
        } catch (error: Exception) {
            atomicFile.failWrite(stream)
            throw error
        }
    }

    companion object {
        const val UNAVAILABLE = "PC pairing storage is unavailable."
    }
}

open class CredentialProtectedPcKeyValueStore(
    context: Context,
    fileName: String = FILE_NAME
) : PcKeyValueStore {
    private val appContext = context.applicationContext
    private val name = fileName
    private val lock = Any()

    private fun file() = PcJsonFile(File(PcCredentialProtectedStorage.directory(appContext), name))

    protected open fun encode(key: String, value: String): String = value

    protected open fun decode(key: String, stored: String): String? = stored

    override suspend fun get(key: String): String? = withContext(Dispatchers.IO) {
        val stored = synchronized(lock) { file().read().optString(key, "").ifEmpty { null } }
        stored?.let { decode(key, it) }
    }

    override suspend fun put(key: String, value: String) = withContext(Dispatchers.IO) {
        val encoded = encode(key, value)
        synchronized(lock) {
            val store = file()
            store.write(store.read().put(key, encoded))
        }
    }

    override suspend fun remove(key: String) = withContext(Dispatchers.IO) {
        synchronized(lock) {
            val store = file()
            val current = store.read()
            if (current.has(key)) {
                current.remove(key)
                store.write(current)
            }
        }
    }

    companion object {
        const val FILE_NAME = "pairings.json"
    }
}

class KeystorePcSecretStore(context: Context) : CredentialProtectedPcKeyValueStore(context, FILE_NAME) {
    override fun encode(key: String, value: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        cipher.updateAAD(key.toByteArray(Charsets.UTF_8))
        val sealed = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(cipher.iv + sealed)
    }

    override fun decode(key: String, stored: String): String? = try {
        val bytes = Base64.getDecoder().decode(stored)
        val secretKey = existingKey()
        if (bytes.size <= IV_BYTES || secretKey == null) {
            null
        } else {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(TAG_BITS, bytes, 0, IV_BYTES))
            cipher.updateAAD(key.toByteArray(Charsets.UTF_8))
            String(cipher.doFinal(bytes, IV_BYTES, bytes.size - IV_BYTES), Charsets.UTF_8)
        }
    } catch (_: GeneralSecurityException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }

    private fun existingKey(): SecretKey? {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        return (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey
    }

    private fun secretKey(): SecretKey = synchronized(keyLock) {
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
        const val FILE_NAME = "secrets.json"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "switchify_pc_pairing_secrets"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val KEY_BITS = 256
        private const val TAG_BITS = 128
        private const val IV_BYTES = 12
        private val keyLock = Any()
    }
}
