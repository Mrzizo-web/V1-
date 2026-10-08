package com.example.data.repository

import com.example.data.local.dao.EventLogDao
import com.example.data.local.dao.GatewayConfigDao
import com.example.data.local.dao.PaymentAttemptDao
import com.example.data.local.dao.PaymentDao
import com.example.data.local.dao.SmsMessageDao
import com.example.data.local.entity.EventLogEntity
import com.example.data.local.entity.PaymentAttemptEntity
import com.example.data.local.entity.PaymentEntity
import com.example.data.local.entity.SmsMessageEntity
import com.example.data.network.PosGateway
import com.example.domain.matcher.MatchOutcome
import com.example.domain.matcher.PaymentMatcher
import com.example.domain.model.PaymentStatus
import com.example.domain.model.SmsProcessingStatus
import com.example.domain.model.WalletType
import com.example.domain.parser.ParserRegistry
import com.example.domain.parser.WalletDetector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class GatewayRepository(
    private val smsMessageDao: SmsMessageDao,
    private val paymentDao: PaymentDao,
    private val paymentAttemptDao: PaymentAttemptDao,
    private val gatewayConfigDao: GatewayConfigDao,
    private val eventLogDao: EventLogDao,
    private val posGateway: PosGateway,
    private val walletDetector: WalletDetector,
    private val parserRegistry: ParserRegistry
) {

    private val paymentMatcher = PaymentMatcher(paymentDao)
    private val syncMutex = Mutex()
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.ENGLISH)

    private fun formatTime(millis: Long): String = timeFormat.format(Date(millis))

    suspend fun logEvent(tag: String, message: String, level: String = "INFO", details: String? = null) {
        val entity = EventLogEntity(
            timestamp = System.currentTimeMillis(),
            tag = tag,
            level = level,
            message = message,
            details = details
        )
        eventLogDao.insertLog(entity)
    }

    /**
     * Primary entry point when an SMS is intercepted or simulated.
     * Transaction-safe, deduplicating, and idempotent.
     */
    suspend fun processIncomingSms(
        smsId: String,
        sender: String,
        body: String,
        receivedAt: Long = System.currentTimeMillis()
    ): ProcessSmsResult = withContext(Dispatchers.IO) {
        val timeStr = formatTime(receivedAt)
        logEvent("SMS_RECEIVED", "$timeStr SMS RECEIVED from $sender", level = "INFO", details = body)

        // Detect Wallet
        val detectedWallet = walletDetector.detectWallet(sender, body)
        logEvent(
            "WALLET_DETECTED",
            "$timeStr WALLET DETECTED: ${detectedWallet.displayNameAr} (${detectedWallet.name})",
            level = if (detectedWallet != WalletType.UNKNOWN) "INFO" else "WARN"
        )

        // Store raw SMS initially
        val rawSms = SmsMessageEntity(
            smsId = smsId,
            receivedAt = receivedAt,
            sender = sender,
            body = body,
            wallet = detectedWallet,
            parsedAmount = null,
            parsedTransactionId = null,
            parsedSender = null,
            paymentId = null,
            processingStatus = SmsProcessingStatus.PENDING
        )
        smsMessageDao.insertMessage(rawSms)

        // Retrieve Parser
        val parser = parserRegistry.findParser(sender, body, detectedWallet)
        if (parser == null) {
            val updatedSms = rawSms.copy(processingStatus = SmsProcessingStatus.NEEDS_REVIEW)
            smsMessageDao.updateMessage(updatedSms)
            logEvent("PARSER", "$timeStr No parser matched for wallet $detectedWallet", level = "WARN")
            return@withContext ProcessSmsResult(
                smsId = smsId,
                paymentId = null,
                status = PaymentStatus.NEEDS_REVIEW,
                message = "No matching parser found"
            )
        }

        // Parse Message into Candidate
        val candidate = parser.parse(smsId, sender, body, receivedAt)

        // Check if non-financial SMS (OTP, spam, balance inquiry)
        if (!candidate.isFinancialTransfer && candidate.amount == null) {
            val updatedSms = rawSms.copy(
                processingStatus = SmsProcessingStatus.IGNORED,
                parserUsed = parser.parserVersion
            )
            smsMessageDao.updateMessage(updatedSms)
            logEvent("IGNORED", "$timeStr Non-transfer SMS ignored (OTP or informational)", level = "INFO")
            return@withContext ProcessSmsResult(
                smsId = smsId,
                paymentId = null,
                status = PaymentStatus.RECEIVED_SMS,
                message = "Non-transfer message ignored"
            )
        }

        // Log extracted details
        candidate.amount?.let {
            logEvent("AMOUNT_PARSED", "$timeStr AMOUNT PARSED: $it YER", level = "SUCCESS")
        }
        candidate.transactionId?.let {
            logEvent("TRANSACTION_ID", "$timeStr TRANSACTION ID: $it", level = "SUCCESS")
        }

        // Multi-SMS Matching and Deduplication
        val matchOutcome = paymentMatcher.matchCandidate(candidate)

        return@withContext when (matchOutcome) {
            is MatchOutcome.LinkToExisting -> {
                val existing = matchOutcome.existingPayment
                // Increment message count for existing payment
                paymentDao.incrementMessageCount(existing.paymentId)

                // Update SMS to link to existing payment
                val updatedSms = rawSms.copy(
                    paymentId = existing.paymentId,
                    parsedAmount = candidate.amount,
                    parsedTransactionId = candidate.transactionId,
                    parsedSender = candidate.sender,
                    processingStatus = SmsProcessingStatus.LINKED_TO_PAYMENT,
                    parserUsed = parser.parserVersion
                )
                smsMessageDao.updateMessage(updatedSms)

                logEvent(
                    "DUPLICATE_LINKED",
                    "$timeStr MULTI-SMS LINKED: Combined into payment ${existing.paymentId.take(8)} (count: ${existing.messageCount + 1}) - ${matchOutcome.reason}",
                    level = "WARN"
                )

                ProcessSmsResult(
                    smsId = smsId,
                    paymentId = existing.paymentId,
                    status = PaymentStatus.DUPLICATE,
                    message = "Linked to existing payment: ${matchOutcome.reason}"
                )
            }

            is MatchOutcome.CreateNew -> {
                val payment = matchOutcome.newPayment
                paymentDao.insertPayment(payment)

                val updatedSms = rawSms.copy(
                    paymentId = payment.paymentId,
                    parsedAmount = candidate.amount,
                    parsedTransactionId = candidate.transactionId,
                    parsedSender = candidate.sender,
                    processingStatus = if (matchOutcome.initialStatus == PaymentStatus.NEEDS_REVIEW)
                        SmsProcessingStatus.NEEDS_REVIEW else SmsProcessingStatus.PROCESSED,
                    parserUsed = parser.parserVersion
                )
                smsMessageDao.updateMessage(updatedSms)

                logEvent("PAYMENT_CREATED", "$timeStr PAYMENT CREATED: ID=${payment.paymentId.take(8)}", level = "SUCCESS")
                logEvent("STATUS", "$timeStr Status: ${matchOutcome.initialStatus.name}", level = "INFO")

                // If queued, trigger background sync
                if (matchOutcome.initialStatus == PaymentStatus.QUEUED) {
                    syncSinglePayment(payment)
                }

                ProcessSmsResult(
                    smsId = smsId,
                    paymentId = payment.paymentId,
                    status = matchOutcome.initialStatus,
                    message = matchOutcome.reason
                )
            }
        }
    }

    /**
     * Attempts to send a single payment to POS.
     * If POS is offline, it leaves it in QUEUED / FAILED for offline sync.
     */
    suspend fun syncSinglePayment(payment: PaymentEntity): Boolean = withContext(Dispatchers.IO) {
        val config = gatewayConfigDao.getConfig()
        if (config?.isGatewayActive == false) {
            logEvent("SYNC_SKIPPED", "Gateway is inactive, payment kept in queue", level = "WARN")
            return@withContext false
        }

        val timeStr = formatTime(System.currentTimeMillis())
        logEvent("SENDING", "$timeStr SENDING TO POS: ${payment.amount} YER (ID: ${payment.paymentId.take(8)})", level = "INFO")

        val eventId = UUID.randomUUID().toString()
        val result = posGateway.sendPayment(payment, eventId)

        // Record attempt
        paymentAttemptDao.insertAttempt(
            PaymentAttemptEntity(
                paymentId = payment.paymentId,
                attemptedAt = System.currentTimeMillis(),
                isSuccess = result.isSuccess,
                httpStatusCode = result.httpStatusCode,
                endpoint = "http://${config?.posIpAddress ?: "unknown"}:${config?.posPort ?: 8080}",
                responseBody = result.rawResponse,
                errorMessage = result.errorMessage
            )
        )

        if (result.isSuccess) {
            paymentDao.updateStatus(payment.paymentId, PaymentStatus.ACKNOWLEDGED)
            logEvent("ACKNOWLEDGED", "$timeStr ACKNOWLEDGED by POS for payment ${payment.paymentId.take(8)}", level = "SUCCESS")
            true
        } else {
            val updated = payment.copy(
                status = PaymentStatus.FAILED,
                retryCount = payment.retryCount + 1,
                lastSyncAttemptAt = System.currentTimeMillis(),
                syncErrorMessage = result.errorMessage
            )
            paymentDao.updatePayment(updated)
            logEvent("SYNC_FAILED", "$timeStr FAILED: ${result.errorMessage ?: "POS unreachable"}. In offline queue.", level = "ERROR")
            false
        }
    }

    /**
     * Loops through all pending/failed payments in Room DB and syncs them.
     * Safe against concurrent calls.
     */
    suspend fun syncPendingPayments(): Int = withContext(Dispatchers.IO) {
        syncMutex.withLock {
            val pending = paymentDao.getPendingSyncPayments()
            if (pending.isEmpty()) return@withLock 0

            var successfulCount = 0
            for (payment in pending) {
                val ok = syncSinglePayment(payment)
                if (ok) successfulCount++
            }
            successfulCount
        }
    }

    suspend fun checkPosConnection(): Boolean = withContext(Dispatchers.IO) {
        posGateway.checkConnection()
    }

    fun observeAllPayments(): Flow<List<PaymentEntity>> = paymentDao.getAllPayments()

    fun observePaymentsByWallet(wallet: WalletType): Flow<List<PaymentEntity>> =
        if (wallet == WalletType.UNKNOWN) paymentDao.getAllPayments() else paymentDao.getPaymentsByWallet(wallet)

    fun observeAllMessages(): Flow<List<SmsMessageEntity>> = smsMessageDao.getAllMessages()

    fun observeMessagesByWallet(wallet: WalletType): Flow<List<SmsMessageEntity>> =
        if (wallet == WalletType.UNKNOWN) smsMessageDao.getAllMessages() else smsMessageDao.getMessagesByWallet(wallet)

    fun observeLogs(): Flow<List<EventLogEntity>> = eventLogDao.getRecentLogs()

    suspend fun clearLogs() = eventLogDao.clearLogs()

    suspend fun getPaymentById(paymentId: String): PaymentEntity? = paymentDao.getPaymentById(paymentId)

    suspend fun getMessagesForPayment(paymentId: String): List<SmsMessageEntity> =
        smsMessageDao.getMessagesForPayment(paymentId)

    // Observables for Dashboard metrics
    fun getPendingCount(): Flow<Int> = paymentDao.getPendingCount()
    fun getSentTodayCount(startOfDay: Long): Flow<Int> = paymentDao.getSentTodayCount(startOfDay)
    fun getFailedCount(): Flow<Int> = paymentDao.getFailedCount()
    fun getNeedsReviewCount(): Flow<Int> = paymentDao.getNeedsReviewCount()
    fun getTotalCount(): Flow<Int> = paymentDao.getTotalCount()
}

data class ProcessSmsResult(
    val smsId: String,
    val paymentId: String?,
    val status: PaymentStatus,
    val message: String
)
