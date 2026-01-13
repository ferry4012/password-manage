package com.example.mykeys

import android.content.Context
import android.util.Base64
import com.google.gson.Gson
import java.util.Date

object ExportImportUtils {
    const val EXPORT_VERSION = "1.0"
    
    // 导出数据结构
    data class ExportData(
        val appName: String,
        val version: String,
        val exportDate: Long,
        val entries: List<PasswordEntry>
    )
    
    // 导出数据为JSON字符串
    suspend fun exportData(context: Context): String {
        val entries = PasswordRepository.getAll(context)
        val exportData = ExportData(
            appName = "MyKey",
            version = EXPORT_VERSION,
            exportDate = Date().time,
            entries = entries
        )
        return Gson().toJson(exportData)
    }
    
    // 从JSON字符串导入数据
    suspend fun importData(context: Context, jsonData: String): Int {
        val exportData = Gson().fromJson(jsonData, ExportData::class.java)
        
        // 验证版本兼容性
        if (exportData.version != EXPORT_VERSION) {
            throw IllegalArgumentException("不兼容的导出版本")
        }
        
        // 保存数据到数据库
        var importedCount = 0
        for (entry in exportData.entries) {
            // 检查是否已存在相同ID的条目
            val existingEntry = PasswordRepository.find(context, entry.id)
            if (existingEntry == null) {
                PasswordRepository.insert(context, entry)
                importedCount++
            } else {
                // 可选：更新已存在的条目
                PasswordRepository.update(context, entry)
                importedCount++
            }
        }
        return importedCount
    }
    
    // 加密导出数据
    fun encryptExportData(context: Context, jsonData: String, password: String): String {
        // 为每次导出生成新的随机盐值，并将其包含在加密数据中
        val salt = ByteArray(32)
        java.security.SecureRandom().nextBytes(salt)
        val key = EncryptionUtils.deriveEncryptionKey(password, salt)
        val encryptedData = EncryptionUtils.encrypt(jsonData, key)
        
        // 将盐值和加密数据组合，格式：盐值(Base64):加密数据
        val saltBase64 = android.util.Base64.encodeToString(salt, android.util.Base64.DEFAULT)
        return "$saltBase64:$encryptedData"
    }
    
    // 解密导出数据
    fun decryptExportData(context: Context, encryptedDataWithSalt: String, password: String): String {
        // 分离盐值和加密数据
        val parts = encryptedDataWithSalt.split(":", limit = 2)
        if (parts.size != 2) {
            throw IllegalArgumentException("无效的导出文件格式")
        }
        
        val salt = android.util.Base64.decode(parts[0], android.util.Base64.DEFAULT)
        val encryptedData = parts[1]
        val key = EncryptionUtils.deriveEncryptionKey(password, salt)
        return EncryptionUtils.decrypt(encryptedData, key)
    }
}
