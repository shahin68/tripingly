package com.falcon.tripingly.core.storage

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * AES-GCM with a key that lives in the Android Keystore and can't be exported;
 * only the ciphertext is in SharedPreferences. A value that no longer decrypts
 * (the key is gone after a restore to a new device) is dropped and reads as absent.
 */
internal class AndroidSecureStore(context: Context) : SecureStore {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val mutex = Mutex()

    override suspend fun get(key: String): String? = locked {
        val stored = prefs.getString(key, null) ?: return@locked null
        try {
            val (iv, ciphertext) = stored.split(SEPARATOR, limit = 2).map { Base64.decode(it, Base64.NO_WRAP) }
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(TAG_BITS, iv))
            cipher.doFinal(ciphertext).decodeToString()
        } catch (e: GeneralSecurityException) {
            prefs.edit().remove(key).apply()
            null
        } catch (e: IllegalArgumentException) {
            prefs.edit().remove(key).apply()
            null
        }
    }

    override suspend fun put(key: String, value: String) = locked<Unit> {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val ciphertext = cipher.doFinal(value.encodeToByteArray())
        val encoded = Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + SEPARATOR +
            Base64.encodeToString(ciphertext, Base64.NO_WRAP)
        // commit, not apply: the value is on disk before the caller goes on, so a
        // process killed right after a token refresh can't lose the new token.
        prefs.edit().putString(key, encoded).commit()
    }

    override suspend fun remove(key: String) = locked {
        prefs.edit().remove(key).apply()
    }

    private suspend fun <T> locked(block: () -> T): T = withContext(Dispatchers.IO) { mutex.withLock { block() } }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).apply {
            init(
                KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(KEY_BITS)
                    .build(),
            )
        }.generateKey()
    }

    private companion object {
        const val PREFS_NAME = "tripinly_secure"
        const val KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "tripinly_secure_store"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_BITS = 256
        const val TAG_BITS = 128
        const val SEPARATOR = ":"
    }
}
