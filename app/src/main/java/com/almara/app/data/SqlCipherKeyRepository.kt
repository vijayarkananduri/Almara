package com.almara.app.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Encrypts the SQLCipher database secret with an AES/GCM key that never leaves AndroidKeyStore. */
class SqlCipherKeyRepository(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @Synchronized
    fun getOrCreatePassphrase(): ByteArray {
        val wrapped = preferences.getString(WRAPPED_KEY, null)
        if (wrapped != null) return decrypt(wrapped)

        val generated = ByteArray(PASSPHRASE_BYTES).also { SecureRandom().nextBytes(it) }
        val encrypted = encrypt(generated)
        check(preferences.edit().putString(WRAPPED_KEY, encrypted).commit()) {
            "Unable to persist encrypted database credential"
        }
        return generated
    }

    private fun encrypt(plain: ByteArray): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateWrappingKey())
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plain)
        val envelope = ByteArray(1 + iv.size + ciphertext.size)
        envelope[0] = ENVELOPE_VERSION
        System.arraycopy(iv, 0, envelope, 1, iv.size)
        System.arraycopy(ciphertext, 0, envelope, 1 + iv.size, ciphertext.size)
        return Base64.encodeToString(envelope, Base64.NO_WRAP)
    }

    private fun decrypt(encoded: String): ByteArray {
        val envelope = Base64.decode(encoded, Base64.NO_WRAP)
        require(envelope.size > 1 + GCM_IV_BYTES + GCM_TAG_BYTES && envelope[0] == ENVELOPE_VERSION) {
            "Invalid encrypted database credential"
        }
        val iv = envelope.copyOfRange(1, 1 + GCM_IV_BYTES)
        val ciphertext = envelope.copyOfRange(1 + GCM_IV_BYTES, envelope.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateWrappingKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(ciphertext)
    }

    private fun getOrCreateWrappingKey(): SecretKey {
        val store = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    companion object {
        private const val PREFS = "almara_sqlcipher_credentials"
        private const val WRAPPED_KEY = "wrapped_passphrase_v1"
        private const val KEY_ALIAS = "com.almara.app.sqlcipher.wrap.v1"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val PASSPHRASE_BYTES = 32
        private const val GCM_IV_BYTES = 12
        private const val GCM_TAG_BITS = 128
        private const val GCM_TAG_BYTES = GCM_TAG_BITS / 8
        private const val ENVELOPE_VERSION: Byte = 1
    }
}
