package com.example.mykeys

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Delete

@Dao
interface PasswordDao {
    @Insert
    suspend fun insert(entry: PasswordEntry)

    @Update
    suspend fun update(entry: PasswordEntry)

    @Delete
    suspend fun delete(entry: PasswordEntry)

    @Query("SELECT * FROM password_entries ORDER BY createdAt DESC")
    suspend fun getAll(): List<PasswordEntry>

    @Query("SELECT * FROM password_entries ORDER BY createdAt DESC LIMIT :limit OFFSET :offset")
    suspend fun getPaged(limit: Int, offset: Int): List<PasswordEntry>

    @Query("SELECT * FROM password_entries WHERE id = :id")
    suspend fun getById(id: String): PasswordEntry?

    @Query("DELETE FROM password_entries WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT * FROM password_entries WHERE title LIKE '%' || :query || '%' OR account LIKE '%' || :query || '%' OR note LIKE '%' || :query || '%' ORDER BY createdAt DESC")
    suspend fun search(query: String): List<PasswordEntry>

    @Query("SELECT COUNT(*) FROM password_entries")
    suspend fun getTotalCount(): Int

    @Query("SELECT COUNT(*) FROM password_entries WHERE LENGTH(password) < 8")
    suspend fun getWeakPasswordCount(): Int
}
