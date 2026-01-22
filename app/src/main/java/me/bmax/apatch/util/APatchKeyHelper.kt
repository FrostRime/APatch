package me.bmax.apatch.util

import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import androidx.core.content.edit
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.security.SecureRandom
import java.security.spec.AlgorithmParameterSpec
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object APatchKeyHelper {
    internal const val SUPER_KEY: String = "super_key"
    internal const val SUPER_KEY_ENC: String = "super_key_enc"
    private const val TAG = "APatchSecurityHelper"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val SKIP_STORE_SUPER_KEY = "skip_store_super_key"
    private const val SUPER_KEY_IV = "super_key_iv"
    private const val KEY_ALIAS = "APatchSecurityKey"
    private const val ENCRYPT_MODE = "AES/GCM/NoPadding"
    private var prefs: SharedPreferences? = null

    init {
        try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
            keyStore.load(null)
            if (!keyStore.containsAlias(KEY_ALIAS)) {
                generateSecretKey()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to checkAndGenerateSecretKey", e)
        }
    }

    fun setSharedPreferences(sp: SharedPreferences?) {
        prefs = sp
    }

    private fun generateSecretKey() {
        try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
            keyStore.load(null)

            if (!keyStore.containsAlias(KEY_ALIAS)) {
                val keyGenerator = KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE
                )

                val spec: AlgorithmParameterSpec = KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(false)
                    .build()

                keyGenerator.init(spec)
                keyGenerator.generateKey()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generateSecretKey", e)
        }
    }

    private val randomIV: String?
        get() {
            var randIV =
                prefs!!.getString(SUPER_KEY_IV, null)
            if (randIV == null) {
                val secureRandom = SecureRandom()
                val generated = secureRandom.generateSeed(12)
                randIV = Base64.encodeToString(generated, Base64.DEFAULT)
                prefs!!.edit { putString(SUPER_KEY_IV, randIV) }
            }
            return randIV
        }

    private fun encrypt(orig: String): String? {
        try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
            keyStore.load(null)
            val secretKey = keyStore.getKey(KEY_ALIAS, null) as SecretKey?

            val cipher = Cipher.getInstance(ENCRYPT_MODE)
            cipher.init(
                Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(
                    128, Base64.decode(
                        randomIV, Base64.DEFAULT
                    )
                )
            )

            return Base64.encodeToString(
                cipher.doFinal(orig.toByteArray(StandardCharsets.UTF_8)),
                Base64.DEFAULT
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to encrypt: ", e)
            return null
        }
    }

    private fun decrypt(encryptedData: String?): String? {
        try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
            keyStore.load(null)
            val secretKey = keyStore.getKey(KEY_ALIAS, null) as SecretKey?

            val cipher = Cipher.getInstance(ENCRYPT_MODE)
            cipher.init(
                Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(
                    128, Base64.decode(
                        randomIV, Base64.DEFAULT
                    )
                )
            )

            return String(
                cipher.doFinal(Base64.decode(encryptedData, Base64.DEFAULT)),
                StandardCharsets.UTF_8
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decrypt", e)
            return null
        }
    }

    fun shouldSkipStoreSuperKey(): Boolean {
        return prefs!!.getInt(SKIP_STORE_SUPER_KEY, 0) != 0
    }

    fun clearConfigKey() {
        prefs!!.edit { remove(SUPER_KEY) }
        prefs!!.edit { remove(SUPER_KEY_ENC) }
        prefs!!.edit { remove(SUPER_KEY_IV) }
    }

    fun setShouldSkipStoreSuperKey(should: Boolean) {
        clearConfigKey()
        prefs!!.edit { putInt(SKIP_STORE_SUPER_KEY, if (should) 1 else 0) }
    }

    fun readSPSuperKey(): String? {
        val encKey: String = prefs!!.getString(SUPER_KEY_ENC, "")!!
        if (!encKey.isEmpty()) {
            return decrypt(encKey)
        }

        val key: String = prefs!!.getString(SUPER_KEY, "")!!
        writeSPSuperKey(key)
        prefs!!.edit { remove(SUPER_KEY) }
        return key
    }

    fun writeSPSuperKey(key: String) {
        var key = key
        if (shouldSkipStoreSuperKey()) return
        key = encrypt(key)!!
        prefs!!.edit { putString(SUPER_KEY_ENC, key) }
    }
}
