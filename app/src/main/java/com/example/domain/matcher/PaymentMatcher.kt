package com.example.domain.matcher

import com.example.data.local.dao.PaymentDao
import com.example.data.local.entity.PaymentEntity
import com.example.domain.model.PaymentCandidate
import com.example.domain.model.PaymentStatus
import java.util.UUID

sealed class MatchOutcome {
    data class LinkToExisting(
        val existingPayment: PaymentEntity,
        val reason: String
    ) : MatchOutcome()

    data class CreateNew(
        val newPayment: PaymentEntity,
        val initialStatus: PaymentStatus,
        val reason: String
    ) : MatchOutcome()
}

/**
 * Intelligent deduplication and multi-message correlation engine.
 */
class PaymentMatcher(
    private val paymentDao: PaymentDao,
    private val timeWindowMillis: Long = 5 * 60 * 1000L // 5 minutes window
) {

    suspend fun matchCandidate(candidate: PaymentCandidate): MatchOutcome {
        val now = System.currentTimeMillis()

        // 1. Transaction ID Match (Highest Priority & Absolute Determinism)
        if (!candidate.transactionId.isNullOrBlank()) {
            val existingByTx = paymentDao.findByTransactionId(candidate.transactionId)
            if (existingByTx != null) {
                return MatchOutcome.LinkToExisting(
                    existingPayment = existingByTx,
                    reason = "Exact Transaction ID match: ${candidate.transactionId}"
                )
            }
        }

        // 2. If Candidate is not a valid financial transfer or missing amount
        if (candidate.amount == null || candidate.amount <= 0 || !candidate.isFinancialTransfer) {
            val paymentId = UUID.randomUUID().toString()
            val newPayment = PaymentEntity(
                paymentId = paymentId,
                transactionId = candidate.transactionId,
                wallet = candidate.wallet,
                amount = candidate.amount ?: 0.0,
                currency = candidate.currency,
                sender = candidate.sender,
                senderAccount = candidate.senderAccount,
                receivedAt = candidate.receivedAt,
                messageCount = 1,
                confidence = candidate.confidence,
                status = PaymentStatus.NEEDS_REVIEW,
                parserVersion = candidate.parserVersion,
                rawMessageSnippet = candidate.rawMessage.take(150),
                initialSmsId = candidate.sourceSmsId,
                createdAt = now,
                updatedAt = now,
                syncErrorMessage = "Missing or unparseable amount"
            )
            return MatchOutcome.CreateNew(
                newPayment = newPayment,
                initialStatus = PaymentStatus.NEEDS_REVIEW,
                reason = "No amount extracted from SMS, flagged for human review"
            )
        }

        // 3. Heuristic matching within the time window
        val windowStart = candidate.receivedAt - timeWindowMillis
        val windowEnd = candidate.receivedAt + timeWindowMillis

        val similarPayments = paymentDao.findRecentSimilar(
            wallet = candidate.wallet,
            amount = candidate.amount,
            windowStart = windowStart,
            windowEnd = windowEnd
        )

        if (similarPayments.isNotEmpty()) {
            // Check if there is an exact sender match without a transaction ID
            val matchingSender = similarPayments.firstOrNull { existing ->
                existing.transactionId == null &&
                        !existing.sender.isNullOrBlank() &&
                        !candidate.sender.isNullOrBlank() &&
                        existing.sender.equals(candidate.sender, ignoreCase = true)
            }

            if (matchingSender != null) {
                return MatchOutcome.LinkToExisting(
                    existingPayment = matchingSender,
                    reason = "Heuristic match: same wallet, amount, and sender within 5-min window"
                )
            }

            // If similar payments exist but senders differ or are absent, DO NOT blindly merge!
            // Flag as NEEDS_REVIEW so operator or POS can verify rather than falsifying payments.
            val paymentId = UUID.randomUUID().toString()
            val payment = PaymentEntity(
                paymentId = paymentId,
                transactionId = candidate.transactionId,
                wallet = candidate.wallet,
                amount = candidate.amount,
                currency = candidate.currency,
                sender = candidate.sender,
                senderAccount = candidate.senderAccount,
                receivedAt = candidate.receivedAt,
                messageCount = 1,
                confidence = (candidate.confidence * 0.7f).coerceIn(0.0f, 1.0f),
                status = PaymentStatus.NEEDS_REVIEW,
                parserVersion = candidate.parserVersion,
                rawMessageSnippet = candidate.rawMessage.take(150),
                initialSmsId = candidate.sourceSmsId,
                createdAt = now,
                updatedAt = now,
                syncErrorMessage = "Ambiguous: another transaction with same amount was received recently"
            )
            return MatchOutcome.CreateNew(
                newPayment = payment,
                initialStatus = PaymentStatus.NEEDS_REVIEW,
                reason = "Multiple candidate payments with same amount within time window"
            )
        }

        // 4. Clean New Payment
        val initialStatus = if (candidate.confidence >= 0.6f) {
            PaymentStatus.QUEUED
        } else {
            PaymentStatus.NEEDS_REVIEW
        }

        val paymentId = UUID.randomUUID().toString()
        val payment = PaymentEntity(
            paymentId = paymentId,
            transactionId = candidate.transactionId,
            wallet = candidate.wallet,
            amount = candidate.amount,
            currency = candidate.currency,
            sender = candidate.sender,
            senderAccount = candidate.senderAccount,
            receivedAt = candidate.receivedAt,
            messageCount = 1,
            confidence = candidate.confidence,
            status = initialStatus,
            parserVersion = candidate.parserVersion,
            rawMessageSnippet = candidate.rawMessage.take(150),
            initialSmsId = candidate.sourceSmsId,
            createdAt = now,
            updatedAt = now
        )

        return MatchOutcome.CreateNew(
            newPayment = payment,
            initialStatus = initialStatus,
            reason = "New unique transaction detected"
        )
    }
}
