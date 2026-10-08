package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.SmsMessageEntity
import com.example.domain.model.WalletType
import kotlinx.coroutines.flow.Flow

@Dao
interface SmsMessageDao {
    @Query("SELECT * FROM sms_messages ORDER BY receivedAt DESC")
    fun getAllMessages(): Flow<List<SmsMessageEntity>>

    @Query("SELECT * FROM sms_messages WHERE wallet = :wallet ORDER BY receivedAt DESC")
    fun getMessagesByWallet(wallet: WalletType): Flow<List<SmsMessageEntity>>

    @Query("SELECT * FROM sms_messages WHERE paymentId = :paymentId ORDER BY receivedAt ASC")
    suspend fun getMessagesForPayment(paymentId: String): List<SmsMessageEntity>

    @Query("SELECT * FROM sms_messages WHERE paymentId = :paymentId ORDER BY receivedAt ASC")
    fun observeMessagesForPayment(paymentId: String): Flow<List<SmsMessageEntity>>

    @Query("SELECT * FROM sms_messages WHERE smsId = :smsId LIMIT 1")
    suspend fun getMessageById(smsId: String): SmsMessageEntity?

    @Query("SELECT * FROM sms_messages WHERE parsedTransactionId = :txId LIMIT 1")
    suspend fun findByTransactionId(txId: String): SmsMessageEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMessage(message: SmsMessageEntity): Long

    @Update
    suspend fun updateMessage(message: SmsMessageEntity)

    @Query("SELECT COUNT(*) FROM sms_messages")
    fun getMessageCount(): Flow<Int>
}
