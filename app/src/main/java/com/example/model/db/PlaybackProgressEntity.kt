package com.example.model.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "playback_progress")
data class PlaybackProgressEntity(
    @PrimaryKey val url: String,
    val title: String,
    val logo: String?,
    val type: String, // MOVIE or SERIES
    val positionMs: Long,
    val durationMs: Long,
    val timestamp: Long = System.currentTimeMillis()
)
