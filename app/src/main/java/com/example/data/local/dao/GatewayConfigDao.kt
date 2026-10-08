package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.GatewayConfigEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GatewayConfigDao {
    @Query("SELECT * FROM gateway_config WHERE id = 1 LIMIT 1")
    fun observeConfig(): Flow<GatewayConfigEntity?>

    @Query("SELECT * FROM gateway_config WHERE id = 1 LIMIT 1")
    suspend fun getConfig(): GatewayConfigEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(config: GatewayConfigEntity)

    @Update
    suspend fun updateConfig(config: GatewayConfigEntity)

    @Query("UPDATE gateway_config SET isGatewayActive = :active WHERE id = 1")
    suspend fun setGatewayActive(active: Boolean)
}
