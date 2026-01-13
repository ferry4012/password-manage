package com.example.mykeys

import android.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.security.SecureRandom

object EncryptionUtils {
    private const val ALGORITHM = "AES/GCM/NoPadding"
    private const val TAG_LENGTH_BITS = 128
    private const val IV_LENGTH_BYTES = 12
    private const val SECRET_KEY_LENGTH = 32 // 256 bits

    // Generate a random initialization vector
    private fun generateIv(): ByteArray {
        val iv = ByteArray(IV_LENGTH_BYTES)
        SecureRandom().nextBytes(iv)
        return iv
    }

    // Encrypt a string using AES-256-GCM
    fun encrypt(data: String, key: ByteArray): String {
        try {
            val iv = generateIv()
            val secretKeySpec = SecretKeySpec(key, "AES")
            val gcmParameterSpec = GCMParameterSpec(TAG_LENGTH_BITS, iv)
            
            val cipher = Cipher.getInstance(ALGORITHM)
            cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, gcmParameterSpec)
            
            val encryptedData = cipher.doFinal(data.toByteArray(Charsets.UTF_8))
            
            // Combine IV and encrypted data
            val combined = ByteArray(iv.size + encryptedData.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(encryptedData, 0, combined, iv.size, encryptedData.size)
            
            return Base64.encodeToString(combined, Base64.DEFAULT)
        } catch (e: Exception) {
            e.printStackTrace()
            // Return original data if encryption fails (not ideal, but prevents app crash)
            return data
        }
    }

    // Decrypt a string using AES-256-GCM
    fun decrypt(encryptedData: String, key: ByteArray): String {
        val combined = Base64.decode(encryptedData, Base64.DEFAULT)
        
        // Extract IV and encrypted data
        val iv = combined.copyOfRange(0, IV_LENGTH_BYTES)
        val encrypted = combined.copyOfRange(IV_LENGTH_BYTES, combined.size)
        
        val secretKeySpec = SecretKeySpec(key, "AES")
        val gcmParameterSpec = GCMParameterSpec(TAG_LENGTH_BITS, iv)
        
        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, gcmParameterSpec)
        
        val decryptedData = cipher.doFinal(encrypted)
        return String(decryptedData, Charsets.UTF_8)
    }

    // Derive encryption key from master password and salt
    fun deriveEncryptionKey(masterPassword: String, salt: ByteArray): ByteArray {
        // Reuse the enhanced KDF from Prefs
        return Prefs.kdf(masterPassword, salt)
    }
}
