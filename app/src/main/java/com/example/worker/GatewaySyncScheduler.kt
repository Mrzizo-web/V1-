package com.example.worker

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Durable local-first scheduling for pending wallet SMS and POS synchronization.
 * WorkManager persists scheduled work across process death and device restart.
 */
object GatewaySyncScheduler {
    const val PERIODIC_WORK_NAME = "power_feul_gateway_periodic_sync"
    const val IMMEDIATE_WORK_NAME = "power_feul_gateway_immediate_sync"

    private fun connectedNetworkConstraints(): Constraints =
        Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

    fun schedulePeriodic(context: Context) {
        val request = PeriodicWorkRequest.Builder(
            GatewaySyncWorker::class.java,
            15,
            TimeUnit.MINUTES
        )
            .setConstraints(connectedNetworkConstraints())
            .build()

        WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    fun enqueueImmediate(context: Context) {
        val request = OneTimeWorkRequest.Builder(GatewaySyncWorker::class.java)
            .setConstraints(connectedNetworkConstraints())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            IMMEDIATE_WORK_NAME,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            request
        )
    }
}
