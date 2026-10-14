package com.example.worker

import com.example.data.local.entity.PaymentEntity
import com.example.domain.model.PaymentStatus
import com.example.domain.model.WalletType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GatewaySyncWorkerPolicyTest {

    private fun payment(status: PaymentStatus, retryCount: Int = 0) = PaymentEntity(
        paymentId = "payment-${status.name}-$retryCount",
        transactionId = null,
        wallet = WalletType.JAWALI,
        amount = 5000.0,
        sender = "synthetic test sender",
        senderAccount = null,
        receivedAt = 1L,
        status = status,
        rawMessageSnippet = "synthetic test SMS",
        initialSmsId = "synthetic-sms-id",
        retryCount = retryCount
    )

    @Test
    fun queuedPaymentsAlwaysRemainEligibleForSync() {
        assertTrue(GatewaySyncWorker.hasRetryablePayments(listOf(payment(PaymentStatus.QUEUED)), 5))
    }

    @Test
    fun failedAndSentPaymentsKeepRetryingAfterConfiguredThreshold() {
        assertTrue(GatewaySyncWorker.hasRetryablePayments(listOf(payment(PaymentStatus.FAILED, 2)), 5))
        assertTrue(GatewaySyncWorker.hasRetryablePayments(listOf(payment(PaymentStatus.SENT, 4)), 5))
        assertTrue(GatewaySyncWorker.hasRetryablePayments(listOf(payment(PaymentStatus.FAILED, 5)), 5))
        assertTrue(GatewaySyncWorker.hasRetryablePayments(listOf(payment(PaymentStatus.SENT, 6)), 5))
        assertTrue(GatewaySyncWorker.hasRetryablePayments(listOf(payment(PaymentStatus.FAILED, 1000)), 5))
        assertTrue(GatewaySyncWorker.hasRetryablePayments(listOf(payment(PaymentStatus.FAILED, 0)), 0))
    }

    @Test
    fun manuallyReviewedAndTerminalPaymentsAreNeverRetried() {
        val nonRetryable = listOf(
            payment(PaymentStatus.NEEDS_REVIEW),
            payment(PaymentStatus.ACKNOWLEDGED),
            payment(PaymentStatus.DUPLICATE),
            payment(PaymentStatus.PARSED),
            payment(PaymentStatus.PARSING)
        )
        assertFalse(GatewaySyncWorker.hasRetryablePayments(nonRetryable, 5))
    }

    @Test
    fun emptyQueueDoesNotRequestAnotherRetry() {
        assertFalse(GatewaySyncWorker.hasRetryablePayments(emptyList(), 5))
    }
}
