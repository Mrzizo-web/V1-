package com.example.data.network.model

import com.example.data.local.entity.PaymentEntity

/**
 * Standard payload sent to POWER FEUL POS over LAN/Wi-Fi.
 */
data class PaymentEventDto(
    val eventId: String,
    val paymentId: String,
    val wallet: String,
    val amount: Double,
    val currency: String = "YER",
    val transactionId: String?,
    val sender: String?,
    val senderAccount: String?,
    val receivedAt: Long,
    val gatewayDeviceId: String,
    val sourceSmsId: String,
    val rawMessage: String,
    val parserVersion: String,
    val createdAt: Long,
    val messageCount: Int,
    val confidence: Float,
    val status: String
) {
    companion object {
        fun fromEntity(payment: PaymentEntity, deviceId: String, eventId: String): PaymentEventDto {
            return PaymentEventDto(
                eventId = eventId,
                paymentId = payment.paymentId,
                wallet = payment.wallet.name,
                amount = payment.amount,
                currency = payment.currency,
                transactionId = payment.transactionId,
                sender = payment.sender,
                senderAccount = payment.senderAccount,
                receivedAt = payment.receivedAt,
                gatewayDeviceId = deviceId,
                sourceSmsId = payment.initialSmsId,
                rawMessage = payment.rawMessageSnippet,
                parserVersion = payment.parserVersion,
                createdAt = payment.createdAt,
                messageCount = payment.messageCount,
                confidence = payment.confidence,
                status = payment.status.name
            )
        }
    }
}
