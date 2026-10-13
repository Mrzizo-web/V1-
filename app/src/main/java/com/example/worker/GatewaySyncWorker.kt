package com.example.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.GatewayApplication
import com.example.data.local.entity.PaymentEntity
import com.example.domain.model.PaymentStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reconciles persisted SMS and synchronizes eligible payments without a permanent
 * foreground service. Financial/SMS state remains in the local Room database.
 */
class GatewaySyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val app = applicationContext as? GatewayApplication ?: return@withContext Result.failure()

        try {
            app.repository.recoverPendingSms()
            val config = app.configRepository.getCurrentConfig()
            if (!config.isGatewayActive || !config.isAutoSyncEnabled) {
                return@withContext Result.success()
            }

            app.repository.syncPendingPayments()
            val pending = app.database.paymentDao().getPendingSyncPayments()
            if (hasRetryablePayments(pending, config.maxRetryCount.coerceAtLeast(1))) {
                Result.retry()
            } else {
                Result.success()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Do not put SMS bodies, payment data, endpoint details, or secrets in logs.
            runCatching {
                app.repository.logEvent(
                    tag = "BACKGROUND_SYNC_ERROR",
                    message = "Gateway background sync failed; WorkManager will retry",
                    level = "WARN"
                )
            }
            Result.retry()
        }
    }

    companion object {
        /**
         * Never automatically send items awaiting manual review. Failed/SENT rows
         * retry only while below the configured attempt limit.
         */
        internal fun hasRetryablePayments(
            payments: List<PaymentEntity>,
            maxAttempts: Int
        ): Boolean {
            val limit = maxAttempts.coerceAtLeast(1)
            return payments.any { payment ->
                when (payment.status) {
                    PaymentStatus.QUEUED -> true
                    PaymentStatus.FAILED, PaymentStatus.SENT -> payment.retryCount < limit
                    else -> false
                }
            }
        }
    }
}
