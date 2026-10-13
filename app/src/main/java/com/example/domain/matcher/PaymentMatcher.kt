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
        val transactionId = candidate.transactionId?.takeIf { it.isNotBlank() }
        var conflictingReference: PaymentEntity? = null

        if (transactionId != null) {
            val existing = paymentDao.findByTransactionId(candidate.wallet, transactionId)
            if (existing != null) {
                val incomingAmount = candidate.amount
                val amountMatches = incomingAmount != null &&
                    kotlin.math.abs(existing.amount - incomingAmount) <= 0.01
                val currencyMatches = existing.currency.equals(candidate.currency, ignoreCase = true)

                if (amountMatches && currencyMatches) {
                    return MatchOutcome.LinkToExisting(
                        existing,
                        "Exact transaction/reference ID and amount match: $transactionId"
                    )
                }

                // A shared reference with a different amount/currency is not a duplicate.
                // Keep it as a separate review record and never auto-send it to POS.
                conflictingReference = existing
            }
        }

        val amount = candidate.amount
        val valid = candidate.isFinancialTransfer && amount != null && amount > 0.0
        val paymentId = UUID.randomUUID().toString()
        val conflictReason = conflictingReference?.let { existing ->
            "Transaction/reference ID $transactionId already exists for ${existing.amount} ${existing.currency}; " +
                "incoming value is ${amount ?: "unknown"} ${candidate.currency}. Manual review required."
        }
        val status = if (conflictReason == null && valid && candidate.confidence >= 0.6f) {
            PaymentStatus.QUEUED
        } else {
            PaymentStatus.NEEDS_REVIEW
        }
        val reason = when {
            conflictReason != null -> conflictReason
            !valid -> "Missing or unparseable incoming financial amount; human review required"
            candidate.confidence < 0.6f -> "Parser confidence below the automatic-transfer threshold; human review required"
            transactionId == null ->
                "Verified incoming transfer has no reference ID; use the stable SMS event ID and never merge by amount, sender, or time"
            else -> "New unique transaction detected"
        }
        val payment = PaymentEntity(
            paymentId = paymentId,
            transactionId = if (conflictingReference == null) transactionId else null,
            wallet = candidate.wallet,
            amount = amount ?: 0.0,
            currency = candidate.currency,
            sender = candidate.sender,
            senderAccount = candidate.senderAccount,
            receivedAt = candidate.receivedAt,
            messageCount = 1,
            confidence = candidate.confidence,
            status = status,
            parserVersion = candidate.parserVersion,
            rawMessageSnippet = candidate.rawMessage.take(150),
            initialSmsId = candidate.sourceSmsId,
            createdAt = now,
            updatedAt = now,
            syncErrorMessage = if (status == PaymentStatus.NEEDS_REVIEW) reason else null
        )
        return MatchOutcome.CreateNew(payment, status, reason)
    }
}
