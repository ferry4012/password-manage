package com.example.mykeys

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object PasswordRepository {
    // This will be set after successful authentication
    private var encryptionKey: ByteArray? = null
    
    // Set encryption key after successful authentication
    fun setEncryptionKey(key: ByteArray) {
        encryptionKey = key
    }
    
    // Clear encryption key on lock/logout
    fun clearEncryptionKey() {
        encryptionKey = null
    }
    
    // Check if encryption key is available
    private fun ensureEncryptionKey(): ByteArray {
        return encryptionKey ?: throw IllegalStateException("Encryption key not set")
    }

    // Encrypt password entry fields
    private fun encryptEntry(entry: PasswordEntry): PasswordEntry {
        val key = ensureEncryptionKey()
        return entry.copy(
            title = EncryptionUtils.encrypt(entry.title, key),
            account = EncryptionUtils.encrypt(entry.account, key),
            password = EncryptionUtils.encrypt(entry.password, key),
            note = EncryptionUtils.encrypt(entry.note, key)
        )
    }

    // Decrypt password entry fields
    private fun decryptEntry(entry: PasswordEntry): PasswordEntry {
        val key = ensureEncryptionKey()
        return entry.copy(
            title = EncryptionUtils.decrypt(entry.title, key),
            account = EncryptionUtils.decrypt(entry.account, key),
            password = EncryptionUtils.decrypt(entry.password, key),
            note = EncryptionUtils.decrypt(entry.note, key)
        )
    }

    // Insert a new password entry
    suspend fun insert(ctx: Context, entry: PasswordEntry) {
        withContext(Dispatchers.IO) {
            val encryptedEntry = encryptEntry(entry)
            val db = AppDatabase.getDatabase(ctx)
            // Check if entry already exists to avoid duplicates
            val existingEntry = db.passwordDao().getById(encryptedEntry.id)
            if (existingEntry == null) {
                db.passwordDao().insert(encryptedEntry)
            }
        }
    }

    // Update an existing password entry
    suspend fun update(ctx: Context, entry: PasswordEntry) {
        withContext(Dispatchers.IO) {
            val encryptedEntry = encryptEntry(entry)
            val db = AppDatabase.getDatabase(ctx)
            db.passwordDao().update(encryptedEntry)
        }
    }

    // Delete a password entry by ID
    suspend fun delete(ctx: Context, id: String) {
        withContext(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(ctx)
            db.passwordDao().deleteById(id)
        }
    }

    // Default page size
    private const val PAGE_SIZE = 10

    // Get all password entries (for search and other operations)
    suspend fun getAll(ctx: Context): MutableList<PasswordEntry> {
        return withContext(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(ctx)
            val encryptedEntries = db.passwordDao().getAll()
            encryptedEntries.map { decryptEntry(it) }.toMutableList()
        }
    }

    // Get all password entries for initial load (deprecated, use getPaged instead)
    suspend fun list(ctx: Context): MutableList<PasswordEntry> {
        return getAll(ctx)
    }

    // Get paged password entries
    suspend fun getPaged(ctx: Context, page: Int): List<PasswordEntry> {
        return withContext(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(ctx)
            val offset = page * PAGE_SIZE
            val encryptedEntries = db.passwordDao().getPaged(PAGE_SIZE, offset)
            encryptedEntries.map { decryptEntry(it) }
        }
    }

    // Get total count of entries
    suspend fun getTotalCount(ctx: Context): Int {
        return withContext(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(ctx)
            db.passwordDao().getTotalCount()
        }
    }

    // Find a password entry by ID
    suspend fun find(ctx: Context, id: String): PasswordEntry? {
        return withContext(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(ctx)
            val encryptedEntry = db.passwordDao().getById(id)
            encryptedEntry?.let { decryptEntry(it) }
        }
    }

    // Search password entries
    suspend fun search(ctx: Context, query: String): List<PasswordEntry> {
        return withContext(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(ctx)
            // Get all entries, decrypt them, then filter in memory
            val encryptedEntries = db.passwordDao().getAll()
            val decryptedEntries = encryptedEntries.map { decryptEntry(it) }
            
            val q = query.lowercase()
            decryptedEntries.filter {
                it.title.lowercase().contains(q) ||
                it.account.lowercase().contains(q) ||
                it.note.lowercase().contains(q)
            }
        }
    }

    // Get count of weak passwords (length < 8)
    suspend fun getWeakPasswordCount(ctx: Context): Int {
        return withContext(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(ctx)
            val encryptedEntries = db.passwordDao().getAll()
            val decryptedEntries = encryptedEntries.map { decryptEntry(it) }
            decryptedEntries.count { it.password.length < 8 }
        }
    }

    // Migrate data from SharedPreferences to Room database (one-time operation)
    suspend fun migrateFromSharedPreferences(ctx: Context, masterPassword: String) {
        withContext(Dispatchers.IO) {
            // Check if migration is needed
            val db = AppDatabase.getDatabase(ctx)
            val count = db.passwordDao().getTotalCount()
            if (count > 0) {
                return@withContext // Already migrated
            }

            // Set encryption key for migration
            val salt = Prefs.getEncryptionSalt(ctx)
            val key = EncryptionUtils.deriveEncryptionKey(masterPassword, salt)
            encryptionKey = key

            // Migrate old data
            val oldPrefs = ctx.getSharedPreferences("mypassword_prefs", Context.MODE_PRIVATE)
            val oldEntriesJson = oldPrefs.getString("entries", null)
            if (oldEntriesJson != null) {
                try {
                    val arr = org.json.JSONArray(oldEntriesJson)
                    var i = 0
                    while (i < arr.length()) {
                        val o = arr.getJSONObject(i)
                        val entry = PasswordEntry(
                            id = o.optString("id"),
                            title = o.optString("title"),
                            account = o.optString("account"),
                            password = o.optString("password"),
                            note = o.optString("note"),
                            createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                            updatedAt = System.currentTimeMillis()
                        )
                        insert(ctx, entry)
                        i++
                    }
                    // Clear old data after migration
                    oldPrefs.edit().remove("entries").apply()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
}