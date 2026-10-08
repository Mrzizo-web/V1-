package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.entity.PaymentAttemptEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PaymentAttemptDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttempt(attempt: PaymentAttemptEntity): Long

    @Query("SELECT * FROM payment_attempts WHERE paymentId = :paymentId ORDER BY attemptedAt DESC")
    suspend fun getAttemptsForPayment(paymentId: String): List<PaymentAttemptEntity>

    @Query("SELECT * FROM payment_attempts WHERE paymentId = :paymentId ORDER BY attemptedAt DESC")
    fun observeAttemptsForPayment(paymentId: String): Flow<List<PaymentAttemptEntity>>
}
