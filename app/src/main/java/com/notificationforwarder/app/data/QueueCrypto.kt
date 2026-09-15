package com.notificationforwarder.app.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.util.Base64
import com.google.gson.Gson
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.security.UnrecoverableKeyException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.AEADBadTagException
import javax.crypto.spec.GCMParameterSpec

data class EncryptedQueuePayload(
    val ciphertext: String,
    val iv: String
)

class QueueKeyMissingException : IllegalStateException("queue_key_unavailable")
class QueuePayloadCorruptException : IllegalStateException("queue_payload_corrupt")

object QueueCrypto {
    private const val KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "notification_forwarder_queue_key"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    const val FORMAT_VERSION = 1

    private val gson = Gson()

    fun encrypt(payload: NotificationPayload): EncryptedQueuePayload {
        try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
            cipher.updateAAD(FORMAT_VERSION.toString().toByteArray(StandardCharsets.UTF_8))
            val encrypted = cipher.doFinal(gson.toJson(payload).toByteArray(StandardCharsets.UTF_8))
            return EncryptedQueuePayload(
                ciphertext = Base64.encodeToString(encrypted, Base64.NO_WRAP),
                iv = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
            )
        } catch (e: QueueKeyMissingException) {
            throw e
        } catch (_: KeyPermanentlyInvalidatedException) {
            throw QueueKeyMissingException()
        }
    }

    fun decrypt(item: QueueItem): NotificationPayload {
        require(item.encryptionVersion == FORMAT_VERSION)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        try {
            cipher.init(
                Cipher.DECRYPT_MODE,
                getExistingKey() ?: throw QueueKeyMissingException(),
                GCMParameterSpec(128, Base64.decode(item.iv, Base64.NO_WRAP))
            )
            cipher.updateAAD(FORMAT_VERSION.toString().toByteArray(StandardCharsets.UTF_8))
            val plaintext = cipher.doFinal(Base64.decode(item.encryptedPayload, Base64.NO_WRAP))
            return gson.fromJson(String(plaintext, StandardCharsets.UTF_8), NotificationPayload::class.java)
        } catch (e: QueueKeyMissingException) {
            throw e
        } catch (_: KeyPermanentlyInvalidatedException) {
            throw QueueKeyMissingException()
        } catch (_: AEADBadTagException) {
            throw QueuePayloadCorruptException()
        } catch (_: UnrecoverableKeyException) {
            throw QueueKeyMissingException()
        } catch (_: Exception) {
            throw QueuePayloadCorruptException()
        }
    }

    fun hasUsableKey(): Boolean {
        return runCatching { getExistingKey() != null }.getOrDefault(false)
    }

    fun resetKey() {
        KeyStore.getInstance(KEYSTORE).apply {
            load(null)
            if (containsAlias(KEY_ALIAS)) {
                deleteEntry(KEY_ALIAS)
            }
        }
    }

    private fun getOrCreateKey(): SecretKey {
        return getExistingKey() ?: synchronized(this) {
            getExistingKey() ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).apply {
                init(
                    KeyGenParameterSpec.Builder(
                        KEY_ALIAS,
                        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                    )
                        .setKeySize(256)
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setUserAuthenticationRequired(false)
                        .build()
                )
            }.generateKey()
        }
    }

    private fun getExistingKey(): SecretKey? {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        return try {
            keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        } catch (_: UnrecoverableKeyException) {
            throw QueueKeyMissingException()
        } catch (_: Exception) {
            throw QueueKeyMissingException()
        }
    }
}
