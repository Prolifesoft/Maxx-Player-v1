package com.example.model.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val id: String,
    val name: String?,
    val email: String?,
    val photoUrl: String? = null
)
