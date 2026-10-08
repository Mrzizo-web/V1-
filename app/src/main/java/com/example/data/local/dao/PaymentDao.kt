package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.PaymentEntity
import com.example.domain.model.PaymentStatus
import com.example.domain.model.WalletType
import kotlinx.coroutines.flow.Flow

@Dao
interface PaymentDao {
    @Query("SELECT * FROM payments ORDER BY receivedAt DESC")
    fun getAllPayments(): Flow<List<PaymentEntity>>

    @Query("SELECT * FROM payments WHERE wallet = :wallet ORDER BY receivedAt DESC")
    fun getPaymentsByWallet(wallet: WalletType): Flow<List<PaymentEntity>>

    @Query("SELECT * FROM payments WHERE status = :status ORDER BY receivedAt DESC")
    fun getPaymentsByStatus(status: PaymentStatus): Flow<List<PaymentEntity>>

    @Query("SELECT * FROM payments WHERE paymentId = :paymentId LIMIT 1")
    suspend fun getPaymentById(paymentId: String): PaymentEntity?

    @Query("SELECT * FROM payments WHERE transactionId = :txId LIMIT 1")
    suspend fun findByTransactionId(txId: String): PaymentEntity?

    /**
     * Find candidates within time window (e.g., 5 minutes) matching wallet, amount, and sender
     * Used for multiple-SMS matching when transactionId is absent.
     */
    @Query("""
        SELECT * FROM payments 
        WHERE wallet = :wallet 
        AND amount = :amount 
        AND receivedAt BETWEEN :windowStart AND :windowEnd
        ORDER BY receivedAt DESC
    """)
    suspend fun findRecentSimilar(
        wallet: WalletType,
        amount: Double,
        windowStart: Long,
        windowEnd: Long
    ): List<PaymentEntity>

    /**
     * Get payments queued for sync
     */
    @Query("""
        SELECT * FROM payments 
        WHERE status IN ('QUEUED', 'FAILED', 'SENT', 'NEEDS_REVIEW') 
        ORDER BY receivedAt ASC
    """)
    suspend fun getPendingSyncPayments(): List<PaymentEntity>

    @Query("""
        SELECT * FROM payments 
        WHERE status IN ('QUEUED', 'FAILED', 'NEEDS_REVIEW') 
        ORDER BY receivedAt ASC
    """)
    fun observePendingSyncPayments(): Flow<List<PaymentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPayment(payment: PaymentEntity)

    @Update
    suspend fun updatePayment(payment: PaymentEntity)

    @Query("UPDATE payments SET status = :status, updatedAt = :updatedAt WHERE paymentId = :paymentId")
    suspend fun updateStatus(paymentId: String, status: PaymentStatus, updatedAt: Long = System.currentTimeMillis())

    @Query("""
        UPDATE payments 
        SET messageCount = messageCount + 1, updatedAt = :updatedAt 
        WHERE paymentId = :paymentId
    """)
    suspend fun incrementMessageCount(paymentId: String, updatedAt: Long = System.currentTimeMillis())

    // Metrics counters
    @Query("SELECT COUNT(*) FROM payments WHERE status IN ('QUEUED', 'PARSED')")
    fun getPendingCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM payments WHERE status = 'ACKNOWLEDGED' AND receivedAt >= :startOfDay")
    fun getSentTodayCount(startOfDay: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM payments WHERE status = 'FAILED'")
    fun getFailedCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM payments WHERE status = 'NEEDS_REVIEW'")
    fun getNeedsReviewCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM payments")
    fun getTotalCount(): Flow<Int>
}
