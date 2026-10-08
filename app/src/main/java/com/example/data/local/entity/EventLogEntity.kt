package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "event_logs",
    indices = [Index("timestamp"), Index("tag"), Index("level")]
)
data class EventLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val tag: String,
    val level: String = "INFO", // "INFO", "WARN", "ERROR", "SUCCESS"
    val message: String,
    val details: String? = null
)
