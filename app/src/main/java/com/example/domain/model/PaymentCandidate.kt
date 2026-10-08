package com.example.domain.model

/**
 * Standard candidate extracted by a WalletParser.
 * Missing/unrecognized fields MUST be null.
 */
data class PaymentCandidate(
    val wallet: WalletType,
    val amount: Double?,
    val currency: String = "YER",
    val transactionId: String?,
    val sender: String?,
    val senderAccount: String?,
    val receivedAt: Long,
    val rawMessage: String,
    val confidence: Float, // 0.0f to 1.0f
    val sourceSmsId: String,
    val parserVersion: String = "1.0",
    val isFinancialTransfer: Boolean = true
)
