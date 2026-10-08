package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.domain.model.SmsProcessingStatus
import com.example.domain.model.WalletType

@Entity(
    tableName = "sms_messages",
    indices = [
        Index("receivedAt"),
        Index("wallet"),
        Index("paymentId"),
        Index("parsedTransactionId")
    ]
)
data class SmsMessageEntity(
    @PrimaryKey
    val smsId: String,
    val receivedAt: Long,
    val sender: String,
    val body: String,
    val wallet: WalletType,
    val parsedAmount: Double?,
    val parsedTransactionId: String?,
    val parsedSender: String?,
    val paymentId: String?,
    val processingStatus: SmsProcessingStatus,
    val parserUsed: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
