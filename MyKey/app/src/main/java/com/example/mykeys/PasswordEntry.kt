package com.example.mykeys

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.Serializable
import java.util.UUID

@Entity(tableName = "password_entries")
data class PasswordEntry(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    var title: String,
    var account: String,
    var password: String, // This will be encrypted
    var note: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) : Serializable
