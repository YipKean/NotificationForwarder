package com.notificationforwarder.app.worker

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object WorkerScheduler {
    private const val QUEUE_SYNC_WORK = "queue_sync_work"
    private const val QUEUE_PERIODIC_WORK = "queue_periodic_work"
    private const val QUEUE_CLEANUP_WORK = "queue_cleanup_work"
    private const val DELIVERY_TAG = "notification_delivery"

    fun enqueueImmediate(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = OneTimeWorkRequestBuilder<QueueWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .addTag(DELIVERY_TAG)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            QUEUE_SYNC_WORK,
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    fun ensurePeriodic(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val periodic = PeriodicWorkRequestBuilder<QueueWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .addTag(DELIVERY_TAG)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            QUEUE_PERIODIC_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            periodic
        )

        val cleanup = PeriodicWorkRequestBuilder<QueueCleanupWorker>(15, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            QUEUE_CLEANUP_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            cleanup
        )
    }

    suspend fun enqueueContinuation(context: Context, runningId: UUID, delayMillis: Long) = withContext(Dispatchers.IO) {
        val request = OneTimeWorkRequestBuilder<QueueWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInitialDelay(delayMillis.coerceAtLeast(0), TimeUnit.MILLISECONDS)
            .addTag(DELIVERY_TAG)
            .build()
        // KEEP would discard this request while the current batch is still running.
        // Wait for persistence before reporting this batch complete.
        val manager = WorkManager.getInstance(context)
        // Periodic recovery is outside this chain: don't append redundant delayed
        // successors on every periodic tick when one-time work already exists.
        val inChain = manager.getWorkInfosForUniqueWork(QUEUE_SYNC_WORK).get().any { it.id == runningId }
        manager.enqueueUniqueWork(
            QUEUE_SYNC_WORK,
            if (inChain) ExistingWorkPolicy.APPEND_OR_REPLACE else ExistingWorkPolicy.KEEP,
            request
        ).result.get()
        Unit
    }

    fun cancelDelivery(context: Context) {
        WorkManager.getInstance(context).cancelAllWorkByTag(DELIVERY_TAG)
    }
}
