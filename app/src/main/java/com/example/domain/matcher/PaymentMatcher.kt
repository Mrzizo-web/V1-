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
class PaymentMatcher(private val paymentDao: PaymentDao) {
    suspend fun matchCandidate(candidate: PaymentCandidate): MatchOutcome {
        val now = System.currentTimeMillis()
        if (!candidate.transactionId.isNullOrBlank()) {
            val existing = paymentDao.findByTransactionId(candidate.transactionId)
            if (existing != null) return MatchOutcome.LinkToExisting(existing, "Exact Transaction ID match: ${candidate.transactionId}")
        }
        val amount = candidate.amount
        val valid = candidate.isFinancialTransfer && amount != null && amount > 0.0
        val paymentId = UUID.randomUUID().toString()
        val status = if (valid && !candidate.transactionId.isNullOrBlank() && candidate.confidence >= 0.6f) PaymentStatus.QUEUED else PaymentStatus.NEEDS_REVIEW
        val reason = when {
            !valid -> "Missing or unparseable financial amount; human review required"
            candidate.transactionId.isNullOrBlank() -> "No transaction/reference ID; never auto-merged and flagged for review"
            else -> "New unique transaction detected"
        }
        val payment = PaymentEntity(
            paymentId = paymentId, transactionId = candidate.transactionId, wallet = candidate.wallet,
            amount = amount ?: 0.0, currency = candidate.currency, sender = candidate.sender,
            senderAccount = candidate.senderAccount, receivedAt = candidate.receivedAt, messageCount = 1,
            confidence = candidate.confidence, status = status, parserVersion = candidate.parserVersion,
            rawMessageSnippet = candidate.rawMessage.take(150), initialSmsId = candidate.sourceSmsId,
            createdAt = now, updatedAt = now, syncErrorMessage = if (status == PaymentStatus.NEEDS_REVIEW) reason else null
        )
        return MatchOutcome.CreateNew(payment, status, reason)
    }
}
