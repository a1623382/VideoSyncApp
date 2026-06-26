package com.videosync.app.util

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * 加密管理器
 * 使用 Android KeyStore 进行安全的密钥管理，AES-GCM 模式加密敏感数据
 * 用于保护用户存储的 NAS 凭据（密码等）
 */
object CryptoManager {

    private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
    private const val KEY_ALIAS = "VideoSyncKey"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH = 128
    private const val SEPARATOR = ":"

    init {
        // 确保 KeyStore 中存在加密密钥
        createKeyIfNotExists()
    }

    /**
     * 在 Android KeyStore 中创建加密密钥（如果不存在）
     */
    private fun createKeyIfNotExists() {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }

        if (!keyStore.containsAlias(KEY_ALIAS)) {
            val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                KEYSTORE_PROVIDER
            )
            val keyGenSpec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()

            keyGenerator.init(keyGenSpec)
            keyGenerator.generateKey()
        }
    }

    /**
     * 获取 KeyStore 中的密钥
     */
    private fun getSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        return keyStore.getKey(KEY_ALIAS, null) as SecretKey
    }

    /**
     * 加密字符串数据
     * @param plainText 明文数据
     * @return Base64 编码的密文（包含 IV 前缀）
     */
    fun encrypt(plainText: String): String {
        if (plainText.isEmpty()) return ""

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getSecretKey())

        val iv = cipher.iv
        val encryptedBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))

        // 将 IV 和密文拼接后 Base64 编码
        val combined = iv + encryptedBytes
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    /**
     * 解密字符串数据
     * @param encryptedText Base64 编码的密文
     * @return 解密后的明文
     */
    fun decrypt(encryptedText: String): String {
        if (encryptedText.isEmpty()) return ""

        val combined = Base64.decode(encryptedText, Base64.NO_WRAP)

        // 提取 IV（前 12 字节）和密文
        val iv = combined.sliceArray(0..11)
        val encryptedBytes = combined.sliceArray(12 until combined.size)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.DECRYPT_MODE, getSecretKey(), gcmSpec)

        val decryptedBytes = cipher.doFinal(encryptedBytes)
        return String(decryptedBytes, Charsets.UTF_8)
    }
}
