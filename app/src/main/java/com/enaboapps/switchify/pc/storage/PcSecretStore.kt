package com.enaboapps.switchify.pc.storage

import java.io.IOException
import java.security.GeneralSecurityException
import java.security.InvalidKeyException
import java.security.ProviderException
import java.security.UnrecoverableKeyException
import java.util.Base64
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class PcSecureStorageUnavailableException(cause: Throwable? = null) :
    Exception("Secure PC storage is unavailable.", cause)

class PcSecretKeyUnusableException(cause: Throwable? = null) :
    Exception("The PC secret key can no longer be used.", cause)

interface PcSecretKeySource {
    fun existingKey(): SecretKey?
    fun createKey(): SecretKey
    fun deleteKey()
}

enum class PcSecretKeyStatus {
    Usable,
    Missing,
    Unusable
}

class PcSecretCipher(private val keys: PcSecretKeySource) {
    fun keyStatus(): PcSecretKeyStatus = try {
        secure { if (keys.existingKey() != null) PcSecretKeyStatus.Usable else PcSecretKeyStatus.Missing }
    } catch (_: PcSecretKeyUnusableException) {
        PcSecretKeyStatus.Unusable
    }

    fun deleteKey() = secure { keys.deleteKey() }

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
        return try {
            secure {
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
        } catch (_: PcSecretKeyUnusableException) {
            null
        }
    }

    private inline fun <T> secure(action: () -> T): T = try {
        action()
    } catch (error: UnrecoverableKeyException) {
        throw PcSecretKeyUnusableException(error)
    } catch (error: InvalidKeyException) {
        throw PcSecretKeyUnusableException(error)
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
        return when (cipher.keyStatus()) {
            PcSecretKeyStatus.Missing -> {
                backing.clear()
                null
            }
            PcSecretKeyStatus.Unusable -> null
            PcSecretKeyStatus.Usable -> cipher.decrypt(key, stored)
        }
    }

    override suspend fun put(key: String, value: String) {
        when (cipher.keyStatus()) {
            PcSecretKeyStatus.Missing -> backing.clear()
            PcSecretKeyStatus.Unusable -> resetKey()
            PcSecretKeyStatus.Usable -> Unit
        }
        val sealed = try {
            cipher.encrypt(key, value)
        } catch (_: PcSecretKeyUnusableException) {
            resetKey()
            cipher.encrypt(key, value)
        }
        backing.put(key, sealed)
    }

    override suspend fun remove(key: String) = backing.remove(key)

    override suspend fun clear() = backing.clear()

    private suspend fun resetKey() {
        cipher.deleteKey()
        backing.clear()
    }
}
