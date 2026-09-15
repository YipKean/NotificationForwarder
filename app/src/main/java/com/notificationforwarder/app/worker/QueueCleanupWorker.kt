package com.notificationforwarder.app.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.notificationforwarder.app.data.NotificationRepository
import com.notificationforwarder.app.settings.SettingsStore

class QueueCleanupWorker(
    appContext: Context,
    workerParameters: WorkerParameters
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result {
        val repository = NotificationRepository(applicationContext)
        DeliveryCoordinator.withState {
            repository.recalculateRetentionLocked(SettingsStore(applicationContext).readAll().retentionHours)
        }
        return Result.success()
    }
}
