package com.example.mykeys

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object Prefs {
    private const val PREFS_NAME = "mypassword_prefs"
    private const val KEY_MASTER_SALT = "master_salt"
    private const val KEY_MASTER_HASH = "master_hash"
    private const val KEY_AUTO_LOCK = "auto_lock"
    private const val KEY_ENCRYPTION_SALT = "encryption_salt"
    
    // Enhanced KDF parameters
    private const val KDF_ITERATIONS = 100_000
    private const val KDF_KEY_LENGTH = 256

    private fun prefs(ctx: Context): SharedPreferences {
        return try {
            val masterKey = MasterKey.Builder(ctx)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            EncryptedSharedPreferences.create(
                ctx,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            // Fallback to regular SharedPreferences if encryption fails
            ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    fun isMasterSet(ctx: Context): Boolean {
        val p = prefs(ctx)
        return p.getString(KEY_MASTER_HASH, null) != null && p.getString(KEY_MASTER_SALT, null) != null
    }

    // Enhanced KDF using PBKDF2-HMAC-SHA256
    fun kdf(password: String, salt: ByteArray): ByteArray {
        return try {
            val spec = PBEKeySpec(
                password.toCharArray(),
                salt,
                KDF_ITERATIONS,
                KDF_KEY_LENGTH
            )
            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            factory.generateSecret(spec).encoded
        } catch (e: Exception) {
            // Fallback to SHA-256 if PBKDF2 fails
            val md = MessageDigest.getInstance("SHA-256")
            md.update(salt)
            md.update(password.toByteArray(Charsets.UTF_8))
            md.digest()
        }
    }

    fun setMaster(ctx: Context, password: String) {
        val salt = ByteArray(32) // Increased salt size for better security
        SecureRandom().nextBytes(salt)
        val hash = kdf(password, salt)
        val hexSalt = salt.joinToString("") { "%02x".format(it) }
        val hexHash = hash.joinToString("") { "%02x".format(it) }
        prefs(ctx).edit()
            .putString(KEY_MASTER_SALT, hexSalt)
            .putString(KEY_MASTER_HASH, hexHash)
            .apply()
    }

    fun verifyMaster(ctx: Context, password: String): Boolean {
        val p = prefs(ctx)
        val hexSalt = p.getString(KEY_MASTER_SALT, null) ?: return false
        val hexHash = p.getString(KEY_MASTER_HASH, null) ?: return false
        val salt = hexToBytes(hexSalt)
        val computed = kdf(password, salt)
        val computedHex = computed.joinToString("") { "%02x".format(it) }
        return slowEquals(hexHash, computedHex)
    }

    fun isAutoLock(ctx: Context): Boolean {
        return prefs(ctx).getBoolean(KEY_AUTO_LOCK, false)
    }

    fun setAutoLock(ctx: Context, enabled: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_AUTO_LOCK, enabled).apply()
    }

    // Get encryption salt for password entries
    fun getEncryptionSalt(ctx: Context): ByteArray {
        val p = prefs(ctx)
        val hexSalt = p.getString(KEY_ENCRYPTION_SALT, null)
        return if (hexSalt != null) {
            hexToBytes(hexSalt)
        } else {
            val salt = ByteArray(32)
            SecureRandom().nextBytes(salt)
            val hexSaltStr = salt.joinToString("") { "%02x".format(it) }
            p.edit().putString(KEY_ENCRYPTION_SALT, hexSaltStr).apply()
            salt
        }
    }

    private fun hexToBytes(hex: String): ByteArray {
        val out = ByteArray(hex.length / 2)
        var i = 0
        while (i < hex.length) {
            out[i / 2] = ((Character.digit(hex[i], 16) shl 4) + Character.digit(hex[i + 1], 16)).toByte()
            i += 2
        }
        return out
    }

    private fun slowEquals(a: String, b: String): Boolean {
        var diff = 0
        val al = a.length
        val bl = b.length
        val len = if (al < bl) al else bl
        var i = 0
        while (i < len) {
            diff = diff or (a[i].code xor b[i].code)
            i++
        }
        diff = diff or (al xor bl)
        return diff == 0
    }
}
