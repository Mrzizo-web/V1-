package com.example

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.example.data.local.AppDatabase
import com.example.data.local.entity.GatewayConfigEntity
import com.example.data.local.entity.PaymentEntity
import com.example.data.local.entity.SmsMessageEntity
import com.example.data.network.PosGateway
import com.example.data.network.model.PosAckResponse
import com.example.data.repository.GatewayRepository
import com.example.domain.model.PaymentStatus
import com.example.domain.model.SmsProcessingStatus
import com.example.domain.model.WalletType
import com.example.domain.parser.ParserRegistry
import com.example.domain.parser.WalletDetector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.coroutineScope
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Collections
import java.util.UUID

class GatewayRepositoryIdempotencyAndroidTest {
    private lateinit var db: AppDatabase
    private lateinit var pos: RecordingPosGateway
    private lateinit var repository: GatewayRepository

    private val incomingJeeb =
        "اضيف 5000 ر.ي تحويل مشترك رص:72758.47ر.ي من عبدالله الجميلي-77366225"
    private val outgoingJeeb =
        "خصم 5000 ر.ي تحويل مشترك رص:72758.47ر.ي الى 77366225 عبدالله"

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        pos = RecordingPosGateway()
        repository = GatewayRepository(
            smsMessageDao = db.smsMessageDao(),
            paymentDao = db.paymentDao(),
            paymentAttemptDao = db.paymentAttemptDao(),
            gatewayConfigDao = db.gatewayConfigDao(),
            eventLogDao = db.eventLogDao(),
            posGateway = pos,
            walletDetector = WalletDetector(),
            parserRegistry = ParserRegistry()
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun concurrentDeliveryOfSameSmsCreatesAndSendsOnlyOnePayment() = runBlocking {
        val smsId = "stable-event-123"
        val receivedAt = System.currentTimeMillis()

        val results = coroutineScope {
            (1..8).map {
                async(Dispatchers.IO) {
                    repository.processIncomingSms(
                        smsId = smsId,
                        sender = "JEEB",
                        body = incomingJeeb,
                        receivedAt = receivedAt
                    )
                }
            }.awaitAll()
        }

        assertEquals(1, db.smsMessageDao().getAllMessages().first().size)
        assertEquals(1, db.paymentDao().getAllPayments().first().size)
        assertTrue(results.any { it.status == PaymentStatus.QUEUED })

        repository.syncPendingPayments()

        val stored = db.paymentDao().getAllPayments().first().single()
        assertEquals(PaymentStatus.ACKNOWLEDGED, stored.status)
        assertEquals(listOf(stored.paymentId), pos.sentEventIds.toList())
        assertEquals(0, db.paymentDao().getPendingCount().first())
    }

    @Test
    fun sameSenderAndAmountWithDifferentSmsIdsAreNotHeuristicallyMerged() = runBlocking {
        val firstTime = System.currentTimeMillis()
        repository.processIncomingSms("sms-one", "JEEB", incomingJeeb, firstTime)
        repository.processIncomingSms("sms-two", "JEEB", incomingJeeb, firstTime + 1_000L)

        val payments = db.paymentDao().getAllPayments().first()
        assertEquals("Distinct SMS event IDs must remain distinct without a real transaction reference", 2, payments.size)

        repository.syncPendingPayments()

        assertEquals(2, pos.sentEventIds.size)
        assertEquals(2, pos.sentEventIds.toSet().size)
    }

    @Test
    fun outgoingTransferAndNeedsReviewAreNeverSentToPos() = runBlocking {
        val result = repository.processIncomingSms(
            smsId = "outgoing-sms",
            sender = "JEEB",
            body = outgoingJeeb,
            receivedAt = System.currentTimeMillis()
        )
        assertEquals(PaymentStatus.NEEDS_REVIEW, result.status)

        val payment = db.paymentDao().getAllPayments().first().single()
        assertEquals(PaymentStatus.NEEDS_REVIEW, payment.status)
        assertFalse(repository.syncSinglePayment(payment))
        assertEquals(0, repository.syncPendingPayments())
        assertTrue(pos.sentEventIds.isEmpty())
        assertTrue(db.paymentDao().getPendingSyncPayments().isEmpty())
    }

    @Test
    fun smsLeftPendingByInterruptedProcessingCanBeRecovered() = runBlocking {
        val smsId = "pending-before-crash"
        val now = System.currentTimeMillis()
        db.smsMessageDao().insertMessage(
            SmsMessageEntity(
                smsId = smsId,
                receivedAt = now,
                sender = "JEEB",
                body = incomingJeeb,
                wallet = WalletType.JEEB,
                parsedAmount = null,
                parsedTransactionId = null,
                parsedSender = null,
                paymentId = null,
                processingStatus = SmsProcessingStatus.PENDING
            )
        )

        assertEquals(1, repository.recoverPendingSms())
        assertEquals(PaymentStatus.QUEUED, db.paymentDao().getPaymentByInitialSmsId(smsId)!!.status)
        assertEquals(SmsProcessingStatus.PROCESSED, db.smsMessageDao().getMessageById(smsId)!!.processingStatus)
        assertEquals(1, db.paymentDao().getAllPayments().first().size)

        repository.syncPendingPayments()
        assertEquals(1, pos.sentEventIds.size)
    }


    @Test
    fun retryDelayPreventsImmediateRepeatedSend() = runBlocking {
        db.gatewayConfigDao().insertOrUpdate(
            GatewayConfigEntity(maxRetryCount = 3, retryDelaySeconds = 300)
        )
        pos.nextSuccess = false
        repository.processIncomingSms(
            smsId = "retry-delay-sms",
            sender = "JEEB",
            body = incomingJeeb,
            receivedAt = System.currentTimeMillis()
        )

        assertEquals(0, repository.syncPendingPayments())
        val failed = db.paymentDao().getAllPayments().first().single()
        assertEquals(PaymentStatus.FAILED, failed.status)
        assertEquals(1, pos.sentEventIds.size)

        pos.nextSuccess = true
        assertEquals(0, repository.syncPendingPayments())
        assertEquals("Automatic retry must respect retryDelaySeconds", 1, pos.sentEventIds.size)

        db.paymentDao().updatePayment(
            failed.copy(lastSyncAttemptAt = System.currentTimeMillis() - 301_000L)
        )
        assertEquals(1, repository.syncPendingPayments())
        assertEquals(2, pos.sentEventIds.size)
        assertEquals(PaymentStatus.ACKNOWLEDGED, db.paymentDao().getAllPayments().first().single().status)
    }

    @Test
    fun maxRetryCountStopsAutomaticAttemptsButKeepsFailedPaymentVisible() = runBlocking {
        db.gatewayConfigDao().insertOrUpdate(
            GatewayConfigEntity(maxRetryCount = 1, retryDelaySeconds = 1)
        )
        pos.nextSuccess = false
        repository.processIncomingSms(
            smsId = "retry-limit-sms",
            sender = "JEEB",
            body = incomingJeeb,
            receivedAt = System.currentTimeMillis()
        )

        assertEquals(0, repository.syncPendingPayments())
        assertEquals(1, pos.sentEventIds.size)
        val failed = db.paymentDao().getAllPayments().first().single()
        assertEquals(PaymentStatus.FAILED, failed.status)
        assertEquals(1, failed.retryCount)

        pos.nextSuccess = true
        assertEquals(0, repository.syncPendingPayments())
        assertEquals("The configured automatic retry limit must be enforced", 1, pos.sentEventIds.size)
        assertEquals(PaymentStatus.FAILED, db.paymentDao().getAllPayments().first().single().status)
    }


    @Test
    fun pendingSmsWithAlreadyInsertedPaymentIsLinkedInsteadOfDuplicated() = runBlocking {
        val smsId = "payment-inserted-before-crash"
        val now = System.currentTimeMillis()
        db.smsMessageDao().insertMessage(
            SmsMessageEntity(
                smsId = smsId,
                receivedAt = now,
                sender = "JEEB",
                body = incomingJeeb,
                wallet = WalletType.JEEB,
                parsedAmount = null,
                parsedTransactionId = null,
                parsedSender = null,
                paymentId = null,
                processingStatus = SmsProcessingStatus.PENDING
            )
        )
        val existingPayment = PaymentEntity(
            paymentId = "already-created-payment",
            transactionId = null,
            wallet = WalletType.JEEB,
            amount = 5000.0,
            currency = "YER",
            sender = "عبدالله الجميلي",
            senderAccount = "77366225",
            receivedAt = now,
            confidence = 0.95f,
            status = PaymentStatus.QUEUED,
            parserVersion = "2.0-jeeb-real",
            rawMessageSnippet = incomingJeeb.take(150),
            initialSmsId = smsId
        )
        db.paymentDao().insertPayment(existingPayment)

        assertEquals(1, repository.recoverPendingSms())
        assertEquals("already-created-payment", db.smsMessageDao().getMessageById(smsId)!!.paymentId)
        assertEquals(1, db.paymentDao().getAllPayments().first().size)
        assertEquals("already-created-payment", db.smsMessageDao().getMessageById(smsId)!!.paymentId)

        repository.syncPendingPayments()
        assertEquals(listOf("already-created-payment"), pos.sentEventIds.toList())
    }

    @Test
    fun posUnmatchedResponseMovesPaymentToReviewWithoutAutomaticRetries() = runBlocking {
        repository.processIncomingSms(
            smsId = "pos-unmatched-review",
            sender = "KURAMI",
            body = incomingJeeb,
            receivedAt = System.currentTimeMillis()
        )
        pos.nextResponse = PosAckResponse(
            isSuccess = false,
            httpStatusCode = 200,
            rawResponse = """{"success":false,"result":"UNMATCHED","smsId":"pos-unmatched-review"}""",
            errorMessage = "POS could not create a wallet transaction; manual review is required",
            requiresReview = true
        )

        assertEquals(0, repository.syncPendingPayments())
        val payment = db.paymentDao().getAllPayments().first().single()
        assertEquals(PaymentStatus.NEEDS_REVIEW, payment.status)
        assertTrue(db.paymentDao().getPendingSyncPayments().isEmpty())
        assertEquals(1, pos.sentEventIds.size)

        repository.syncPendingPayments()
        val afterSecondSync = db.paymentDao().getAllPayments().first().single()
        assertEquals(PaymentStatus.NEEDS_REVIEW, afterSecondSync.status)
        assertEquals("Manual-review payments must not be automatically retried", 1, pos.sentEventIds.size)
    }

    private class RecordingPosGateway : PosGateway {
        val sentEventIds = Collections.synchronizedList(mutableListOf<String>())
        @Volatile var nextSuccess: Boolean = true
        @Volatile var nextResponse: PosAckResponse? = null

        override suspend fun checkConnection(): Boolean = true

        override suspend fun sendPayment(payment: PaymentEntity, eventId: String): PosAckResponse {
            sentEventIds.add(eventId)
            return nextResponse ?: PosAckResponse(
                isSuccess = nextSuccess,
                httpStatusCode = if (nextSuccess) 200 else 503,
                rawResponse = if (nextSuccess) """{"success":true,"result":"CREATED"}""" else null,
                errorMessage = if (nextSuccess) null else "POS unreachable"
            )
        }
    }
    @Test
    fun sameJawaliReferenceAndAmountIsLinkedAsDuplicate() = runBlocking {
        val body = "استلمت YER 100 كقيمة مشتريات بمرجع 123456789 من عميل اختبار"
        repository.processIncomingSms("jawali-ref-one", "JAWALI", body, 1_800_000_000_000L)
        val duplicate = repository.processIncomingSms(
            "jawali-ref-two", "JAWALI", body, 1_800_000_001_000L
        )

        assertEquals(PaymentStatus.DUPLICATE, duplicate.status)
        assertEquals(1, db.paymentDao().getAllPayments().first().size)
        assertEquals(2, db.smsMessageDao().getAllMessages().first().size)
    }

    @Test
    fun sameJawaliReferenceWithDifferentAmountIsHeldForManualReview() = runBlocking {
        val firstBody = "استلمت YER 100 كقيمة مشتريات بمرجع 123456789 من عميل اختبار"
        val conflictingBody = "استلمت YER 200 كقيمة مشتريات بمرجع 123456789 من عميل اختبار"
        repository.processIncomingSms("jawali-conflict-one", "JAWALI", firstBody, 1_800_000_000_000L)
        val conflict = repository.processIncomingSms(
            "jawali-conflict-two", "JAWALI", conflictingBody, 1_800_000_001_000L
        )

        assertEquals(PaymentStatus.NEEDS_REVIEW, conflict.status)
        val payments = db.paymentDao().getAllPayments().first()
        assertEquals(2, payments.size)
        assertEquals(PaymentStatus.QUEUED, payments.first { it.initialSmsId == "jawali-conflict-one" }.status)
        val reviewPayment = payments.first { it.initialSmsId == "jawali-conflict-two" }
        assertEquals(PaymentStatus.NEEDS_REVIEW, reviewPayment.status)
        assertEquals(null, reviewPayment.transactionId)
        assertTrue(reviewPayment.syncErrorMessage!!.contains("123456789"))

        repository.syncPendingPayments()
        assertEquals("Only the validated original event may be transmitted", 1, pos.sentEventIds.size)
        assertEquals(PaymentStatus.NEEDS_REVIEW,
            db.paymentDao().getAllPayments().first().first { it.initialSmsId == "jawali-conflict-two" }.status)
    }


}
