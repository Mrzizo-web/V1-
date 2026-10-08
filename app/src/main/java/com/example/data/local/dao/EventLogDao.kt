package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.entity.EventLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EventLogDao {
    @Query("SELECT * FROM event_logs ORDER BY timestamp DESC LIMIT 200")
    fun getRecentLogs(): Flow<List<EventLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: EventLogEntity): Long

    @Query("DELETE FROM event_logs WHERE id NOT IN (SELECT id FROM event_logs ORDER BY timestamp DESC LIMIT 500)")
    suspend fun trimOldLogs()

    @Query("DELETE FROM event_logs")
    suspend fun clearLogs()
}
