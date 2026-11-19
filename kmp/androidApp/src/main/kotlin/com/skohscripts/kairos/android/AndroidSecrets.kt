package com.skohscripts.kairos.android

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import android.util.Base64
import com.skohscripts.kairos.ui.app.SecretLevel
import com.skohscripts.kairos.ui.app.SecretStore
import java.io.IOException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec

class AndroidSecrets(context: Context) : SecretStore {
    private val preferences = context.applicationContext.getSharedPreferences("online-secrets", Context.MODE_PRIVATE)
    private val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    @Suppress("DEPRECATION")
    override val level: SecretLevel
        get() = runCatching {
            val info = SecretKeyFactory.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
                .getKeySpec(encryptionKey(), KeyInfo::class.java) as KeyInfo
            if (info.isInsideSecureHardware) SecretLevel.HARDWARE else SecretLevel.SYSTEM
        }.getOrDefault(SecretLevel.NONE)

    @Synchronized
    override fun get(key: String): String? = runCatching {
        val encoded = preferences.getString(key, null) ?: return null
        // Do not generate a replacement key when an existing secret cannot be read.
        val secretKey = store.getKey(ALIAS, null) as? SecretKey ?: return null
        val envelope = Base64.decode(encoded, Base64.NO_WRAP)
        require(envelope.size >= IV_BYTES + TAG_BYTES)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, envelope, 0, IV_BYTES))
        cipher.updateAAD(key.toByteArray(Charsets.UTF_8))
        cipher.doFinal(envelope, IV_BYTES, envelope.size - IV_BYTES).toString(Charsets.UTF_8)
    }.getOrNull()

    @Synchronized
    override fun put(key: String, value: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, encryptionKey())
        cipher.updateAAD(key.toByteArray(Charsets.UTF_8))
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        require(cipher.iv.size == IV_BYTES)
        val envelope = Base64.encodeToString(cipher.iv + encrypted, Base64.NO_WRAP)
        if (!preferences.edit().putString(key, envelope).commit()) throw IOException("Secret write failed")
    }

    @Synchronized
    override fun delete(key: String) {
        if (!preferences.edit().remove(key).commit()) throw IOException("Secret deletion failed")
    }

    override fun keys(): Set<String> = preferences.all.keys.toSet()

    @Synchronized
    private fun encryptionKey(): SecretKey = (store.getKey(ALIAS, null) as? SecretKey) ?: run {
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(256)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build(),
        )
        generator.generateKey()
    }

    private companion object {
        const val ALIAS = "kairos-secrets"
        const val IV_BYTES = 12
        const val TAG_BYTES = 16
    }
}
