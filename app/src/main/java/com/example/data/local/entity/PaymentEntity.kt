package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.domain.model.PaymentStatus
import com.example.domain.model.WalletType

@Entity(
    tableName = "payments",
    indices = [
        Index("transactionId"),
        Index("wallet"),
        Index("status"),
        Index("receivedAt")
    ]
)
data class PaymentEntity(
    @PrimaryKey
    val paymentId: String, // UUID
    val transactionId: String?,
    val wallet: WalletType,
    val amount: Double,
    val currency: String = "YER",
    val sender: String?,
    val senderAccount: String?,
    val receivedAt: Long,
    val messageCount: Int = 1,
    val confidence: Float = 1.0f,
    val status: PaymentStatus,
    val parserVersion: String = "1.0",
    val rawMessageSnippet: String,
    val initialSmsId: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val lastSyncAttemptAt: Long? = null,
    val retryCount: Int = 0,
    val syncErrorMessage: String? = null
)
