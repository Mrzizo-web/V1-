package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "payment_attempts",
    foreignKeys = [
        ForeignKey(
            entity = PaymentEntity::class,
            parentColumns = ["paymentId"],
            childColumns = ["paymentId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("paymentId"), Index("attemptedAt")]
)
data class PaymentAttemptEntity(
    @PrimaryKey(autoGenerate = true)
    val attemptId: Long = 0,
    val paymentId: String,
    val attemptedAt: Long = System.currentTimeMillis(),
    val isSuccess: Boolean,
    val httpStatusCode: Int?,
    val endpoint: String,
    val responseBody: String?,
    val errorMessage: String?
)
