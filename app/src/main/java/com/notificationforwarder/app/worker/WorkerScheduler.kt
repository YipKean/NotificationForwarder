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

    fun cancelDelivery(context: Context) {
        WorkManager.getInstance(context).cancelAllWorkByTag(DELIVERY_TAG)
    }
}
