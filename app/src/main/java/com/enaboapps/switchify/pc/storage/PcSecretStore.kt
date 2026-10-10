package com.enaboapps.switchify.pc.storage

import java.io.IOException
import java.security.GeneralSecurityException
import java.security.ProviderException
import java.util.Base64
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class PcSecureStorageUnavailableException(cause: Throwable? = null) :
    Exception("Secure PC storage is unavailable.", cause)

interface PcSecretKeySource {
    fun existingKey(): SecretKey?
    fun createKey(): SecretKey
}

class PcSecretCipher(private val keys: PcSecretKeySource) {
    fun hasKey(): Boolean = secure { keys.existingKey() != null }

    fun encrypt(name: String, value: String): String = secure {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, keys.existingKey() ?: keys.createKey())
        cipher.updateAAD(name.toByteArray(Charsets.UTF_8))
        val sealed = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        Base64.getEncoder().encodeToString(cipher.iv + sealed)
    }

    fun decrypt(name: String, stored: String): String? {
        val bytes = try {
            Base64.getDecoder().decode(stored)
        } catch (_: IllegalArgumentException) {
            return null
        }
        if (bytes.size < MIN_BLOB_BYTES) return null
        return secure {
            val key = keys.existingKey() ?: return@secure null
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, bytes, 0, IV_BYTES))
            cipher.updateAAD(name.toByteArray(Charsets.UTF_8))
            try {
                String(cipher.doFinal(bytes, IV_BYTES, bytes.size - IV_BYTES), Charsets.UTF_8)
            } catch (_: AEADBadTagException) {
                null
            }
        }
    }

    private inline fun <T> secure(action: () -> T): T = try {
        action()
    } catch (error: GeneralSecurityException) {
        throw PcSecureStorageUnavailableException(error)
    } catch (error: ProviderException) {
        throw PcSecureStorageUnavailableException(error)
    } catch (error: IOException) {
        throw PcSecureStorageUnavailableException(error)
    }

    companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val TAG_BITS = 128
        const val MIN_BLOB_BYTES = IV_BYTES + TAG_BITS / 8
    }
}

class EncryptedPcKeyValueStore(
    private val backing: PcKeyValueStore,
    private val cipher: PcSecretCipher
) : PcKeyValueStore {
    override suspend fun get(key: String): String? {
        val stored = backing.get(key) ?: return null
        if (!cipher.hasKey()) {
            backing.clear()
            return null
        }
        return cipher.decrypt(key, stored)
    }

    override suspend fun put(key: String, value: String) {
        if (!cipher.hasKey()) backing.clear()
        backing.put(key, cipher.encrypt(key, value))
    }

    override suspend fun remove(key: String) = backing.remove(key)

    override suspend fun clear() = backing.clear()
}
